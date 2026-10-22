package com.deepdimen.matrix.zenflow.conversation;

import static com.deepdimen.matrix.zenflow.conversation.ZenflowConversationFacade.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.zenflow.companion.ZenflowRunAuthorization;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates TURN grants only inside the accepted-message transaction. Never imports all
 * private notes.
 */
@Component
public class ZenflowConversationSources {

	private final JdbcTemplate jdbc;

	public ZenflowConversationSources(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Map<String, Object>> selections(Object context) {
		if (context == null)
			return List.of();
		if (!(context instanceof Map<?, ?> contextMap) || !Set.of("capsules").containsAll(contextMap.keySet()))
			throw error(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE_CONTEXT");
		Object value = ((Map<?, ?>) context).get("capsules");
		if (value == null)
			return List.of();
		if (!(value instanceof List<?> list) || list.size() > 3)
			throw error(HttpStatus.BAD_REQUEST, "CAPSULE_SELECTION_LIMIT");
		var result = new ArrayList<Map<String, Object>>();
		var ids = new HashSet<String>();
		for (Object item : (List<?>) value) {
			if (!(item instanceof Map<?, ?> map) || !Set.of("id", "revisionId", "scope").equals(map.keySet()))
				throw error(HttpStatus.BAD_REQUEST, "INVALID_CAPSULE_SELECTION");
			String id = text(map.get("id"), 64), revision = text(map.get("revisionId"), 64);
			if (!id.matches("[0-9]{1,19}") || !revision.matches("[0-9]{1,19}") || !"TURN".equals(map.get("scope"))
					|| !ids.add(id))
				throw error(HttpStatus.BAD_REQUEST, "INVALID_CAPSULE_SELECTION");
			result.add(Map.of("id", id, "revisionId", revision, "scope", "TURN"));
		}
		return result.stream().sorted(Comparator.comparing(x -> x.get("id").toString())).toList();
	}

	public void register(IdentityEnvelope identity, String sessionId, String runId, long historyEnd,
			List<Map<String, Object>> selections, boolean memoryEnabled) {
		var directKeys = new HashSet<String>();
		for (var selection : selections) {
			var records = jdbc.queryForList(
					"SELECT r.content_hash FROM ai_capsule c JOIN ai_capsule_revision r ON r.id=c.current_revision_id AND r.capsule_id=c.id AND r.tenant_id=c.tenant_id AND r.owner_user_id=c.owner_user_id JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id WHERE c.id=? AND r.id=? AND c.tenant_id=? AND c.owner_user_id=? AND c.owner_subject_type='APP_USER' AND r.owner_subject_type='APP_USER' AND c.status='ACTIVE' AND c.del_flag='0' AND e.delete_state='ACTIVE' AND e.text_state='CONFIRMED' FOR UPDATE",
					selection.get("id"), selection.get("revisionId"), identity.tenantId(), identity.userId());
			if (records.size() != 1)
				throw error(HttpStatus.CONFLICT, "NOTE_REVISION_UNAVAILABLE");
			long consent = IdWorker.getId();
			jdbc.update(
					"INSERT INTO zf_capsule_consent(id,tenant_id,owner_user_id,capsule_id,capsule_revision_id,source_hash,scope_type,scope_key) VALUES(?,?,?,?,?,?,'TURN',?)",
					consent, identity.tenantId(), identity.userId(), selection.get("id"), selection.get("revisionId"),
					records.get(0).get("content_hash"), runId);
			var grant = jdbc.queryForMap("SELECT * FROM zf_capsule_consent WHERE id=? AND tenant_id=?", consent,
					identity.tenantId());
			insert(identity, runId, grant, "CAPSULE", null, records.get(0).get("content_hash").toString(), "DIRECT",
					null, directKeys);
		}
		var sessionGrants = jdbc.queryForList(
				"SELECT g.* FROM zf_capsule_consent g JOIN ai_capsule c ON c.id=g.capsule_id AND c.tenant_id=g.tenant_id AND c.owner_user_id=g.owner_user_id AND c.current_revision_id=g.capsule_revision_id JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id WHERE g.tenant_id=? AND g.owner_user_id=? AND g.scope_type='SESSION' AND g.scope_key=? AND g.state='ACTIVE' AND c.owner_subject_type='APP_USER' AND c.status='ACTIVE' AND c.del_flag='0' AND e.delete_state='ACTIVE' AND e.text_state='CONFIRMED' ORDER BY g.id LIMIT 13",
				identity.tenantId(), identity.userId(), sessionId);
		for (var grant : sessionGrants)
			insert(identity, runId, grant, "CAPSULE", null, grant.get("source_hash").toString(), "DIRECT", null,
					directKeys);
		if (memoryEnabled) {
			var memories = jdbc.queryForList(
					"SELECT g.*,m.id AS memory_id,m.summary FROM zf_memory m JOIN zf_capsule_consent g ON g.id=m.consent_id AND g.tenant_id=m.tenant_id AND g.owner_user_id=m.owner_user_id AND g.capsule_revision_id=m.capsule_revision_id JOIN ai_capsule c ON c.id=g.capsule_id AND c.tenant_id=g.tenant_id AND c.owner_user_id=g.owner_user_id AND c.current_revision_id=g.capsule_revision_id JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id WHERE m.tenant_id=? AND m.owner_user_id=? AND m.state='ACTIVE' AND g.state='ACTIVE' AND g.scope_type='PERSISTENT' AND g.scope_key='*' AND c.owner_subject_type='APP_USER' AND c.status='ACTIVE' AND c.del_flag='0' AND e.delete_state='ACTIVE' AND e.text_state='CONFIRMED' ORDER BY m.id LIMIT 13",
					identity.tenantId(), identity.userId());
			for (var grant : memories)
				insert(identity, runId, grant, "MEMORY", grant.get("memory_id"),
						ZenflowRunAuthorization.hash(grant.get("summary").toString()), "DIRECT", null, directKeys);
		}
		// Keep original root-message IDs when inheriting, so a revoked ancestor cannot
		// regain authority.
		var inherited = jdbc.queryForList(
				"SELECT src.*,x.response_message_id FROM zf_run_source src JOIN zf_companion_run_context x ON x.run_id=src.run_id AND x.tenant_id=src.tenant_id AND x.owner_user_id=src.owner_user_id JOIN ai_assistant_message m ON m.id=x.response_message_id AND m.session_id=x.session_id AND m.status='COMPLETED' WHERE x.tenant_id=? AND x.owner_user_id=? AND x.session_id=? AND x.lineage_state='VALID' AND src.status='VALID' AND m.sequence_no<=? ORDER BY m.sequence_no DESC,src.id DESC LIMIT 300",
				identity.tenantId(), identity.userId(), sessionId, historyEnd);
		var inheritedKeys = new HashSet<String>(directKeys);
		for (var source : inherited) {
			String key = source.get("source_key").toString();
			if (!inheritedKeys.add(key))
				continue;
			jdbc.update(
					"INSERT INTO zf_run_source(id,tenant_id,owner_user_id,run_id,source_key,source_kind,reference_snapshot_id,capsule_id,capsule_revision_id,memory_id,consent_id,consent_version,content_hash,use_role,derived_from_message_id,citation_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'HISTORY_DERIVED',?,?)",
					IdWorker.getId(), identity.tenantId(), identity.userId(), runId, key, source.get("source_kind"),
					source.get("reference_snapshot_id"), source.get("capsule_id"), source.get("capsule_revision_id"),
					source.get("memory_id"), source.get("consent_id"), source.get("consent_version"),
					source.get("content_hash"), source.get("derived_from_message_id") == null
							? source.get("response_message_id") : source.get("derived_from_message_id"),
					source.get("citation_key"));
		}
		if (directKeys.size() > 12 || inherited.size() >= 300)
			throw error(HttpStatus.CONFLICT, "PRIVATE_CONTEXT_TOO_LARGE");
	}

	private void insert(IdentityEnvelope identity, String runId, Map<String, Object> grant, String kind, Object memory,
			String hash, String role, Object parent, Set<String> keys) {
		String key = ZenflowRunAuthorization
			.hash(kind + ":" + grant.get("capsule_id") + ":" + grant.get("capsule_revision_id") + ":" + grant.get("id")
					+ ":" + grant.get("version") + ":" + Objects.toString(memory, ""));
		if (!keys.add(key))
			return;
		jdbc.update(
				"INSERT INTO zf_run_source(id,tenant_id,owner_user_id,run_id,source_key,source_kind,capsule_id,capsule_revision_id,memory_id,consent_id,consent_version,content_hash,use_role,derived_from_message_id,citation_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
				IdWorker.getId(), identity.tenantId(), identity.userId(), runId, key, kind, grant.get("capsule_id"),
				grant.get("capsule_revision_id"), memory, grant.get("id"), grant.get("version"), hash, role, parent,
				"PRIVATE_" + key.substring(0, 24));
	}

}
