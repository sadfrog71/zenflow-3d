package com.deepdimen.matrix.zenflow.companion;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.discover.service.impl.ManagedSkillBindingService;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Creates only owned ADMIN_USER preview sessions and independent Skill bindings. */
@Service
public class ZenflowPreviewService {

	private final JdbcTemplate jdbc;

	private final ZenflowAdminAccess access;

	private final ObjectMapper json;

	private final ManagedSkillBindingService bindings;

	private final AssistantSessionService sessions;

	private final AssistantRunEngine engine;

	private final ZenflowPreviewRuntime runtime;

	private final ZenflowReferenceSnapshots snapshots;

	private final TransactionTemplate tx;

	public ZenflowPreviewService(JdbcTemplate jdbc, ZenflowAdminAccess access, ObjectMapper json,
			ManagedSkillBindingService bindings, AssistantSessionService sessions, AssistantRunEngine engine,
			ZenflowPreviewRuntime runtime, ZenflowReferenceSnapshots snapshots, PlatformTransactionManager manager) {
		this.jdbc = jdbc;
		this.access = access;
		this.json = json;
		this.bindings = bindings;
		this.sessions = sessions;
		this.engine = engine;
		this.runtime = runtime;
		this.snapshots = snapshots;
		this.tx = new TransactionTemplate(manager);
	}

	private IdentityEnvelope identity() {
		var user = access.require();
		return new IdentityEnvelope(user.getTenantId().toString(), user.getId().toString(), null, List.of(), List.of(),
				List.of(), UUID.randomUUID().toString(), ZenflowPreviewSkillPolicy.PURPOSE, "ADMIN_USER");
	}

	public Map<String, Object> create(Map<String, Object> body) {
		if (body == null || !Set.of("target", "revisionId", "expectedVersion", "question", "locale", "history")
			.containsAll(body.keySet()))
			throw error(HttpStatus.BAD_REQUEST, "PREVIEW_UNKNOWN_FIELD");
		String target = Objects.toString(body.get("target"), "");
		if (!Set.of("ONLINE", "REVISION").contains(target))
			throw error(HttpStatus.BAD_REQUEST, "PREVIEW_TARGET_INVALID");
		var input = ZenflowPreviewInput.parse(body.get("question"), body.get("locale"),
				body.getOrDefault("history", List.of()));
		var identity = identity();
		if (!runtime.supports(identity))
			throw error(HttpStatus.SERVICE_UNAVAILABLE, "PREVIEW_RUNTIME_UNAVAILABLE");
		return tx.execute(status -> {

			// Reserve administrator trial slots without taking the online configuration
			// lock.
			var administrators = jdbc.queryForList(
					"SELECT user_id FROM sys_user WHERE tenant_id=? AND user_id=? AND del_flag='0' FOR UPDATE",
					identity.tenantId(), identity.userId());
			if (administrators.size() != 1)
				throw error(HttpStatus.FORBIDDEN, "PREVIEW_NOT_AUTHORIZED");
			var configs = jdbc.queryForList(
					"SELECT id FROM zf_companion_config WHERE tenant_id=? AND config_key='default'",
					identity.tenantId());
			if (configs.size() != 1)
				throw error(HttpStatus.CONFLICT, "COMPANION_NOT_READY");
			Long busy = jdbc.queryForObject(
					"SELECT COUNT(*) FROM zf_companion_preview p LEFT JOIN ai_assistant_execution_run r ON r.id=p.run_id AND r.tenant_id=p.tenant_id WHERE p.tenant_id=? AND p.administrator_id=? AND p.status IN ('CREATED','ACTIVE') AND p.expires_at>UTC_TIMESTAMP(3) AND (r.id IS NULL OR r.status NOT IN ('COMPLETED','FAILED','CANCELLED'))",
					Long.class, identity.tenantId(), identity.userId());
			if (busy != null && busy >= 2)
				throw error(HttpStatus.CONFLICT, "PREVIEW_BUSY");
			List<Map<String, Object>> revisions;
			if (target.equals("ONLINE")) {
				if (body.containsKey("revisionId") || body.containsKey("expectedVersion"))
					throw error(HttpStatus.BAD_REQUEST, "PREVIEW_TARGET_INVALID");
				revisions = jdbc.queryForList(
						"SELECT r.* FROM zf_companion_config c JOIN ai_skill_binding b ON b.id=c.skill_binding_id AND b.tenant_id=c.tenant_id JOIN ai_skill_binding_revision br ON br.id=b.active_revision_id AND br.binding_id=b.id AND br.tenant_id=b.tenant_id AND br.enabled=1 JOIN zf_companion_revision r ON r.config_id=c.id AND r.tenant_id=c.tenant_id AND r.skill_release_id=br.release_id AND JSON_UNQUOTE(JSON_EXTRACT(br.parameter_json,'$.companionRevisionId'))=CAST(r.id AS CHAR) WHERE c.tenant_id=? AND c.id=? AND c.status='ENABLED' AND r.state='READY'",
						identity.tenantId(), configs.get(0).get("id"));
			}
			else {
				String revision = Objects.toString(body.get("revisionId"), "");
				if (!revision.matches("[1-9][0-9]{0,18}") || !(body.get("expectedVersion") instanceof Number version)
						|| version.longValue() < 1 || version.doubleValue() != version.longValue())
					throw error(HttpStatus.BAD_REQUEST, "PREVIEW_VERSION_REQUIRED");
				revisions = jdbc.queryForList(
						"SELECT * FROM zf_companion_revision WHERE tenant_id=? AND config_id=? AND id=? AND state='READY' AND version=?",
						identity.tenantId(), configs.get(0).get("id"), revision, version.longValue());
			}
			if (revisions.size() != 1)
				throw error(HttpStatus.CONFLICT, "COMPANION_PREPARE_REQUIRED");
			var revision = revisions.get(0);
			long preview = IdWorker.getId();
			jdbc.update(
					"INSERT INTO zf_companion_preview(id,tenant_id,administrator_id,companion_revision_id,skill_release_id,release_digest,model_id,model_config_digest,locale,question,history_json,input_digest,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 24 HOUR))",
					preview, identity.tenantId(), identity.userId(), revision.get("id"),
					revision.get("skill_release_id"), revision.get("release_digest"), revision.get("model_id"),
					revision.get("model_config_digest"), input.locale(), input.question(),
					serialize(input.historyPayload()), input.digest(json));
			var consumer = ZenflowPreviewSkillPolicy.consumer(Long.toString(preview));
			long binding = bindings.createIdentityDraft(consumer);
			long bindingRevision = bindings.revise(binding, 0,
					new ManagedSkillBindingService.RevisionInput(
							((Number) revision.get("skill_release_id")).longValue(), true, "{}", "ON_DEMAND",
							serialize(Map.of("companionRevisionId", revision.get("id").toString())), "{}"));
			jdbc.update(
					"UPDATE zf_companion_preview SET skill_binding_id=?,binding_revision_id=? WHERE id=? AND tenant_id=?",
					binding, bindingRevision, preview, identity.tenantId());
			bindings.activate(binding, bindingRevision, 0);
			var session = sessions.create(identity,
					new CreateSessionRequest("Invelle administrator preview", List.of(), "INVELLE_COMPANION_PREVIEW"));
			var run = engine.startOrGet(identity, session, UUID.randomUUID().toString(), input.question(), List.of(),
					true, null, List.of(), null, null, null);
			if (jdbc.update(
					"UPDATE zf_companion_preview SET session_id=?,run_id=?,status='ACTIVE',updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? AND status='CREATED'",
					session.assistantSessionId(), run.runId(), preview, identity.tenantId()) != 1)
				throw error(HttpStatus.CONFLICT, "PREVIEW_CREATE_FAILED");
			return Map.of("previewId", Long.toString(preview), "runId", run.runId(), "revisionId",
					revision.get("id").toString(), "revisionNo", revision.get("revision_no"), "modelId",
					revision.get("model_id"), "status", run.status().name());
		});
	}

	private Map<String, Object> owned(IdentityEnvelope identity, String id) {
		if (id == null || !id.matches("[1-9][0-9]{0,18}"))
			throw error(HttpStatus.NOT_FOUND, "PREVIEW_NOT_FOUND");
		var rows = jdbc.queryForList(
				"SELECT p.id,p.session_id,p.run_id,p.companion_revision_id,p.model_id,p.release_digest,p.reference_selection_json,r.revision_no,r.retrieval_policy_json,r.reference_digest FROM zf_companion_preview p JOIN zf_companion_revision r ON r.id=p.companion_revision_id AND r.tenant_id=p.tenant_id WHERE p.id=? AND p.tenant_id=? AND p.administrator_id=? AND p.expires_at>UTC_TIMESTAMP(3)",
				id, identity.tenantId(), identity.userId());
		if (rows.size() != 1)
			throw error(HttpStatus.NOT_FOUND, "PREVIEW_NOT_FOUND");
		sessions.get(identity, rows.get(0).get("session_id").toString());
		return rows.get(0);
	}

	public Map<String, Object> detail(String id) {
		var identity = identity();
		var preview = owned(identity, id);
		return Map.of("previewId", id, "revisionId", preview.get("companion_revision_id").toString(), "revisionNo",
				preview.get("revision_no"), "modelId", preview.get("model_id"), "releaseDigest",
				preview.get("release_digest"), "references", referenceView(preview), "run",
				sessions.runSnapshot(identity, preview.get("session_id").toString(), preview.get("run_id").toString()));
	}

	private Map<String, Object> referenceView(Map<String, Object> preview) {
		if (preview.get("reference_selection_json") == null)
			return Map.of("state", "PENDING", "items", List.of());
		try {
			var hashes = json.readValue(preview.get("reference_selection_json").toString(),
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
					});
			if (hashes.size() > 26)
				throw new IllegalArgumentException("REFERENCE_RETRIEVAL_INVALID");
			var policy = ZenflowReferencePolicy.parse(json.readValue(preview.get("retrieval_policy_json").toString(),
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
					}));
			var sources = hashes.isEmpty() ? List.<ZenflowReferenceSnapshots.Snapshot>of()
					: snapshots.selected(preview.get("companion_revision_id").toString(), policy,
							preview.get("reference_digest").toString(), hashes);
			var items = sources.stream()
				.map(source -> Map.of("snapshotId", source.id(), "documentId", source.documentId(), "title",
						source.title(), "citationVisibility", source.citationVisibility(), "excerpt",
						"PUBLIC".equals(source.citationVisibility())
								? source.content().substring(0, Math.min(1000, source.content().length())) : "",
						"sourceLocator",
						"PUBLIC".equals(source.citationVisibility()) ? Objects.toString(source.sourceLocator(), "")
								: ""))
				.toList();
			return Map.of("state", "READY", "items", items);
		}
		catch (ResponseStatusException unavailable) {
			if (unavailable.getStatusCode().value() == 401 || unavailable.getStatusCode().value() == 403)
				throw unavailable;
			return Map.of("state", "UNAVAILABLE", "items", List.of());
		}
		catch (java.io.IOException | RuntimeException unavailable) {
			return Map.of("state", "UNAVAILABLE", "items", List.of());
		}
	}

	public Object cancel(String id) {
		var identity = identity();
		var preview = owned(identity, id);
		return sessions.cancelRun(identity, preview.get("session_id").toString(), preview.get("run_id").toString());
	}

	public record Observation(String runId, Runnable guard) {
	}

	public Observation observe(String id) {
		var identity = identity();
		var preview = owned(identity, id);
		Runnable guard = () -> access.runDelegated(Long.parseLong(identity.userId()), () -> owned(identity, id));
		return new Observation(preview.get("run_id").toString(), guard);
	}

	private String serialize(Object value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (java.io.IOException failure) {
			throw error(HttpStatus.BAD_REQUEST, "PREVIEW_INPUT_INVALID");
		}
	}

	private static ResponseStatusException error(HttpStatus status, String code) {
		return new ResponseStatusException(status, code);
	}

}
