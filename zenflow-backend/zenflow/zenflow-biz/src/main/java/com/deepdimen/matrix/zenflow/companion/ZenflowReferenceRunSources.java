package com.deepdimen.matrix.zenflow.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Public roots are pinned independently from private capsule consent. */
@Component
public class ZenflowReferenceRunSources {

	static final String KIND = "REFERENCE";

	public static String citationKey(String snapshot) {
		return "REFERENCE_" + snapshot;
	}

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final ZenflowReferenceSnapshots snapshots;

	private final ZenflowReferenceContextAdapter adapter;

	public ZenflowReferenceRunSources(JdbcTemplate jdbc, ObjectMapper json, ZenflowReferenceSnapshots snapshots,
			ZenflowReferenceContextAdapter adapter) {
		this.jdbc = jdbc;
		this.json = json;
		this.snapshots = snapshots;
		this.adapter = adapter;
	}

	public List<ZenflowReferenceSnapshots.Snapshot> retrieve(AssistantRunRecord run, Runnable heartbeat) {
		var rows = jdbc.queryForList(
				"SELECT r.* FROM zf_companion_run_context x JOIN zf_companion_revision r ON r.id=x.companion_revision_id AND r.tenant_id=x.tenant_id WHERE x.run_id=? AND x.tenant_id=? AND x.owner_user_id=? AND x.session_id=? AND x.lineage_state='VALID' AND r.state='READY'",
				run.runId(), run.tenantId(), run.ownerUserId(), run.sessionId());
		if (rows.size() != 1)
			throw fail();
		var revision = rows.get(0);
		return adapter.retrieve(revision, policy(revision), run.input().content(), heartbeat);
	}

	public void attach(AssistantRunRecord run, List<ZenflowReferenceSnapshots.Snapshot> selected) {
		if (!TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("REFERENCE_TRANSACTION_REQUIRED");
		if (selected.size() > 26)
			throw fail();
		var contexts = jdbc.queryForList(
				"SELECT companion_revision_id FROM zf_companion_run_context WHERE run_id=? AND tenant_id=? AND owner_user_id=? AND session_id=? AND lineage_state='VALID' FOR UPDATE",
				run.runId(), run.tenantId(), run.ownerUserId(), run.sessionId());
		if (contexts.size() != 1)
			throw fail();
		String revision = contexts.get(0).get("companion_revision_id").toString();
		for (var snapshot : selected) {
			String key = key(revision, snapshot.id());
			jdbc.update(
					"INSERT INTO zf_run_source(id,tenant_id,owner_user_id,run_id,source_key,source_kind,reference_snapshot_id,content_hash,use_role,citation_key) VALUES(?,?,?,?,?,'REFERENCE',?,?,'DIRECT',?) ON DUPLICATE KEY UPDATE use_role='DIRECT',derived_from_message_id=NULL",
					IdWorker.getId(), run.tenantId(), run.ownerUserId(), run.runId(), key, snapshot.id(),
					snapshot.contentHash(), citationKey(snapshot.id()));
		}
	}

	public List<ModelContextFragment> authorize(AssistantRunRecord run, List<Map<String, Object>> sources) {
		var references = sources.stream().filter(s -> KIND.equals(s.get("source_kind"))).toList();
		if (references.isEmpty())
			return List.of();
		if (references.size() > 300 || references.stream().filter(s -> "DIRECT".equals(s.get("use_role"))).count() > 26)
			throw fail();
		var fragments = new ArrayList<ModelContextFragment>();
		var groups = new LinkedHashMap<String, List<Map<String, Object>>>();
		var revisions = new HashMap<String, Map<String, Object>>();
		var ids = references.stream()
			.map(s -> Objects.toString(s.get("reference_snapshot_id"), ""))
			.distinct()
			.toList();
		var parameters = new ArrayList<Object>();
		parameters.add(run.tenantId());
		parameters.addAll(ids);
		var parentRows = jdbc.queryForList(
				"SELECT f.id AS ref_snapshot_id,r.id AS id FROM zf_reference_snapshot f JOIN zf_companion_revision r ON r.id=f.companion_revision_id AND r.tenant_id=f.tenant_id WHERE f.tenant_id=? AND r.state='READY' AND f.id IN ("
						+ String.join(",", Collections.nCopies(ids.size(), "?")) + ")",
				parameters.toArray());
		var parentsById = new HashMap<String, Map<String, Object>>();
		for (var parent : parentRows)
			if (parentsById.putIfAbsent(parent.get("ref_snapshot_id").toString(), parent) != null)
				throw fail();
		if (parentsById.size() != ids.size())
			throw fail();
		var revisionIds = parentsById.values().stream().map(parent -> parent.get("id").toString()).distinct().toList();
		var revisionParameters = new ArrayList<Object>();
		revisionParameters.add(run.tenantId());
		revisionParameters.addAll(revisionIds);
		for (var revision : jdbc.queryForList(
				"SELECT * FROM zf_companion_revision WHERE tenant_id=? AND state='READY' AND id IN ("
						+ String.join(",", Collections.nCopies(revisionIds.size(), "?")) + ")",
				revisionParameters.toArray()))
			revisions.put(revision.get("id").toString(), revision);
		if (revisions.size() != revisionIds.size())
			throw fail();
		var current = jdbc.queryForList(
				"SELECT companion_revision_id FROM zf_companion_run_context WHERE run_id=? AND tenant_id=? AND owner_user_id=? AND session_id=? AND lineage_state='VALID'",
				run.runId(), run.tenantId(), run.ownerUserId(), run.sessionId());
		if (current.size() != 1)
			throw fail();
		var history = references.stream().filter(source -> "HISTORY_DERIVED".equals(source.get("use_role"))).toList();
		var rootCounts = new HashMap<List<String>, Integer>();
		if (!history.isEmpty()) {
			var rootParameters = new ArrayList<Object>(List.of(run.tenantId(), run.ownerUserId(), run.sessionId()));
			for (var source : history) {
				rootParameters.add(source.get("derived_from_message_id"));
				rootParameters.add(source.get("source_key"));
				rootParameters.add(source.get("reference_snapshot_id"));
			}
			var roots = jdbc.queryForList(
					"SELECT x.response_message_id,root.source_key,root.reference_snapshot_id FROM zf_companion_run_context x JOIN zf_run_source root ON root.run_id=x.run_id AND root.tenant_id=x.tenant_id AND root.owner_user_id=x.owner_user_id WHERE x.tenant_id=? AND x.owner_user_id=? AND x.session_id=? AND x.lineage_state='VALID' AND root.status='VALID' AND (x.response_message_id,root.source_key,root.reference_snapshot_id) IN ("
							+ String.join(",", Collections.nCopies(history.size(), "(?,?,?)")) + ")",
					rootParameters.toArray());
			for (var root : roots)
				rootCounts.merge(List.of(root.get("response_message_id").toString(), root.get("source_key").toString(),
						root.get("reference_snapshot_id").toString()), 1, Integer::sum);
		}

		for (var source : references) {
			if (!"VALID".equals(source.get("status")))
				throw fail();
			String revision = parentsById.get(source.get("reference_snapshot_id").toString()).get("id").toString();
			if (!key(revision, source.get("reference_snapshot_id").toString()).equals(source.get("source_key"))
					|| !(citationKey(source.get("reference_snapshot_id").toString()))
						.equals(source.get("citation_key")))
				throw fail();
			String role = Objects.toString(source.get("use_role"), "");
			if ("DIRECT".equals(role)) {
				if (!revision.equals(current.get(0).get("companion_revision_id").toString()))
					throw fail();
			}
			else if ("HISTORY_DERIVED".equals(role)) {
				if (rootCounts.getOrDefault(
						List.of(Objects.toString(source.get("derived_from_message_id"), ""),
								source.get("source_key").toString(), source.get("reference_snapshot_id").toString()),
						0) != 1)
					throw fail();
			}
			else
				throw fail();
			groups.computeIfAbsent(revision, ignored -> new ArrayList<>()).add(source);
		}
		for (var entry : groups.entrySet()) {
			var parent = revisions.get(entry.getKey());
			var rows = entry.getValue();
			var pinnedPolicy = policy(parent);
			var hashes = new LinkedHashMap<String, String>();
			for (var source : rows)
				if (hashes.putIfAbsent(source.get("reference_snapshot_id").toString(),
						source.get("content_hash").toString()) != null)
					throw fail();
			var checked = snapshots.historyRoots(entry.getKey(), pinnedPolicy,
					parent.get("reference_digest").toString(), hashes);
			var directIds = new HashSet<String>();
			for (var source : rows)
				if ("DIRECT".equals(source.get("use_role")))
					directIds.add(source.get("reference_snapshot_id").toString());
			for (var fixed : checked)
				if (directIds.contains(fixed.id()))
					fragments.add(new ModelContextFragment(citationKey(fixed.id()), fixed.title(), fixed.content(),
							DataClassification.PUBLIC, Map.of("sourceType", KIND, "snapshotId", fixed.id(),
									"citationVisibility", fixed.citationVisibility())));
		}
		if (fragments.stream().mapToInt(f -> f.content().length()).sum() > 12000)
			throw fail();
		return List.copyOf(fragments);
	}

	private ZenflowReferencePolicy policy(Map<String, Object> revision) {
		try {
			return ZenflowReferencePolicy.parse(json.readValue(revision.get("retrieval_policy_json").toString(),
					new TypeReference<Map<String, Object>>() {
					}));
		}
		catch (Exception invalid) {
			throw fail();
		}
	}

	private static String key(String revision, String snapshot) {
		return ZenflowRunAuthorization.hash("REFERENCE:" + revision + ":" + snapshot);
	}

	private static AssistantModelException fail() {
		return new AssistantModelException("REFERENCE_SOURCE_REVOKED", "本轮参考资料已变化，请重新发送");
	}

}
