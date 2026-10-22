package com.deepdimen.matrix.zenflow.conversation;

import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.companion.ZenflowRunAuthorization;
import com.deepdimen.matrix.zenflow.companion.ZenflowReferenceSnapshots;
import com.deepdimen.matrix.zenflow.companion.ZenflowReferencePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Actual markers in a validated complete answer; source bodies remain in CapsuleService
 * stores.
 */
@Service
public class ZenflowCitations {

	private static final Pattern MARKER = Pattern.compile("\\[([A-Za-z0-9_-]{1,128})\\]");

	private final JdbcTemplate jdbc;

	private final ZenflowConsumerAccess access;

	private final ZenflowReferenceSnapshots snapshots;

	private final ObjectMapper json;

	public ZenflowCitations(JdbcTemplate jdbc, ZenflowConsumerAccess access, ZenflowReferenceSnapshots snapshots,
			ObjectMapper json) {
		this.jdbc = jdbc;
		this.access = access;
		this.snapshots = snapshots;
		this.json = json;
	}

	public static Set<String> referenced(String answer, Collection<String> available) {
		var result = new LinkedHashSet<String>();
		var matcher = MARKER.matcher(answer);
		var keys = new HashSet<>(available);
		while (matcher.find())
			if (keys.contains(matcher.group(1)))
				result.add(matcher.group(1));
		return Collections.unmodifiableSet(result);
	}

	/** Only explicit block quotes uniquely contained in an authorized public source qualify. */
	public static Set<String> exactPublicQuotes(String answer, Map<String, String> publicSources) {
		var result = new LinkedHashSet<String>();
		for (String line : answer.split("\\R")) {
			String trimmed = line.strip();
			if (!trimmed.startsWith(">") || MARKER.matcher(trimmed).find())
				continue;
			String quote = trimmed.substring(1).strip();
			if (quote.codePointCount(0, quote.length()) < 6)
				continue;
			var matches = publicSources.entrySet().stream()
				.filter(source -> source.getValue().contains(quote))
				.map(Map.Entry::getKey).toList();
			if (matches.size() == 1)
				result.add(matches.get(0));
		}
		return Collections.unmodifiableSet(result);
	}

	public List<Map<String, Object>> list(String runId) {
		var identity = access.capsuleIdentity();
		var run = jdbc.queryForList(
				"SELECT x.lineage_state,r.status FROM zf_companion_run_context x JOIN zf_conversation_binding b ON b.session_id=x.session_id AND b.tenant_id=x.tenant_id AND b.owner_user_id=x.owner_user_id JOIN ai_assistant_execution_run r ON r.id=x.run_id AND r.tenant_id=x.tenant_id AND r.session_id=x.session_id WHERE x.run_id=? AND x.tenant_id=? AND x.owner_user_id=? AND b.deleted_at IS NULL",
				runId, identity.tenantId(), identity.ownerUserId());
		if (run.size() != 1)
			throw ZenflowConversationFacade.error(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND");
		if (!"VALID".equals(run.get(0).get("lineage_state")) || !"COMPLETED".equals(run.get(0).get("status")))
			return List.of();
		var rows = jdbc.queryForList(
				"SELECT s.citation_key,s.source_kind,s.capsule_id,s.capsule_revision_id,s.content_hash,c.title,r.content,m.summary FROM zf_run_source s "
						+ "JOIN zf_capsule_consent g ON g.id=s.consent_id AND g.tenant_id=s.tenant_id AND g.owner_user_id=s.owner_user_id AND g.capsule_id=s.capsule_id AND g.capsule_revision_id=s.capsule_revision_id AND g.version=s.consent_version "
						+ "JOIN ai_capsule c ON c.id=s.capsule_id AND c.tenant_id=s.tenant_id AND c.owner_user_id=s.owner_user_id "
						+ "JOIN ai_capsule_revision r ON r.id=s.capsule_revision_id AND r.capsule_id=c.id AND r.tenant_id=c.tenant_id AND r.owner_user_id=c.owner_user_id "
						+ "JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id "
						+ "LEFT JOIN zf_memory m ON m.id=s.memory_id AND m.tenant_id=s.tenant_id AND m.owner_user_id=s.owner_user_id AND m.consent_id=g.id AND m.capsule_revision_id=r.id "
						+ "JOIN zf_profile p ON p.tenant_id=s.tenant_id AND p.owner_user_id=s.owner_user_id "
						+ "WHERE s.run_id=? AND s.tenant_id=? AND s.owner_user_id=? AND s.cited_flag=1 AND s.status='VALID' AND s.use_role='DIRECT' AND g.state='ACTIVE' AND g.source_hash=r.content_hash AND c.current_revision_id=r.id AND c.owner_subject_type='APP_USER' AND r.owner_subject_type='APP_USER' AND c.status='ACTIVE' AND c.del_flag='0' AND e.delete_state='ACTIVE' AND e.text_state='CONFIRMED' AND (s.source_kind='CAPSULE' OR (s.source_kind='MEMORY' AND m.state='ACTIVE' AND p.memory_enabled=1)) ORDER BY s.id",
				runId, identity.tenantId(), identity.ownerUserId());
		var cards = new ArrayList<Map<String, Object>>();
		for (var row : rows) {
			String content = Objects
				.toString("MEMORY".equals(row.get("source_kind")) ? row.get("summary") : row.get("content"), "");
			if (!ZenflowRunAuthorization.hash(content).equals(row.get("content_hash")))
				continue;
			cards.add(Map.of("citationKey", row.get("citation_key"), "sourceType", row.get("source_kind"), "title",
					Objects.toString(row.get("title"), "私人记录"), "noteId", row.get("capsule_id").toString(),
					"revisionId", row.get("capsule_revision_id").toString(), "runId", runId, "excerpt",
					content.substring(0, Math.min(content.length(), 1000)), "visibility", "PRIVATE", "cited", true));
		}
		cards.addAll(publicCards(runId, identity.tenantId(), identity.ownerUserId()));
		return List.copyOf(cards);
	}

	private List<Map<String, Object>> publicCards(String runId, Long tenant, Long owner) {
		var rows = jdbc.queryForList(
				"SELECT s.citation_key,s.content_hash,s.reference_snapshot_id,r.id AS revision_id,r.reference_digest,r.retrieval_policy_json FROM zf_run_source s JOIN zf_companion_run_context x ON x.run_id=s.run_id AND x.tenant_id=s.tenant_id AND x.owner_user_id=s.owner_user_id JOIN zf_reference_snapshot f ON f.id=s.reference_snapshot_id AND f.tenant_id=s.tenant_id AND f.companion_revision_id=x.companion_revision_id JOIN zf_companion_revision r ON r.id=f.companion_revision_id AND r.tenant_id=f.tenant_id WHERE s.run_id=? AND s.tenant_id=? AND s.owner_user_id=? AND s.source_kind='REFERENCE' AND s.use_role='DIRECT' AND s.cited_flag=1 AND s.status='VALID' AND x.lineage_state='VALID' AND r.state='READY' ORDER BY s.id LIMIT 27",
				runId, tenant, owner);
		if (rows.isEmpty() || rows.size() > 26)
			return List.of();
		var first = rows.get(0);
		var hashes = new LinkedHashMap<String, String>();
		for (var row : rows) {
			String id = row.get("reference_snapshot_id").toString();
			if (!Objects.equals(first.get("revision_id"), row.get("revision_id"))
					|| !com.deepdimen.matrix.zenflow.companion.ZenflowReferenceRunSources.citationKey(id)
						.equals(row.get("citation_key"))
					|| hashes.putIfAbsent(id, row.get("content_hash").toString()) != null)
				return List.of();
		}
		List<ZenflowReferenceSnapshots.Snapshot> checked;
		try {
			var policy = ZenflowReferencePolicy.parse(json.readValue(first.get("retrieval_policy_json").toString(),
					new TypeReference<Map<String, Object>>() {
					}));
			checked = snapshots.selected(first.get("revision_id").toString(), policy,
					first.get("reference_digest").toString(), hashes);
		}
		catch (org.springframework.web.server.ResponseStatusException unavailable) {
			return List.of();
		}
		catch (java.io.IOException | IllegalArgumentException invalid) {
			return List.of();
		}
		var cards = new ArrayList<Map<String, Object>>();
		for (var fixed : checked) {
			if ("INTERNAL".equals(fixed.citationVisibility()))
				continue;
			var card = new LinkedHashMap<String, Object>();
			card.put("citationKey",
					com.deepdimen.matrix.zenflow.companion.ZenflowReferenceRunSources.citationKey(fixed.id()));
			card.put("sourceType", "REFERENCE");
			card.put("title", fixed.title());
			card.put("snapshotId", fixed.id());
			card.put("documentId", fixed.documentId());
			card.put("revisionId", first.get("revision_id").toString());
			card.put("runId", runId);
			card.put("visibility", fixed.citationVisibility());
			card.put("cited", true);
			card.put("excerpt", "PUBLIC".equals(fixed.citationVisibility())
					? fixed.content() : "");
			if ("PUBLIC".equals(fixed.citationVisibility()))
				card.put("sourceLocator", fixed.sourceLocator());
			cards.add(Collections.unmodifiableMap(card));
		}
		return List.copyOf(cards);
	}

	public Map<String, Object> detail(String runId, String key) {
		return list(runId).stream()
			.filter(card -> key.equals(card.get("citationKey")))
			.findFirst()
			.orElseThrow(() -> ZenflowConversationFacade.error(HttpStatus.NOT_FOUND, "CITATION_UNAVAILABLE"));
	}

}
