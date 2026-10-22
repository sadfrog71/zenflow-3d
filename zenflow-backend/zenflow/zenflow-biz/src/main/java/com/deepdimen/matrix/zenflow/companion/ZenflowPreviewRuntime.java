package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.run.*;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.zenflow.admin.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Isolated administrator trial of a prepared release through the original Engine and
 * Skill.
 */
@Component
public class ZenflowPreviewRuntime implements AssistantGovernedTextExecutor, ZenflowPreviewLease {

	private final ThreadLocal<Scope> active = new ThreadLocal<>();

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final ZenflowProperties product;

	private final ZenflowAdminAccess administrators;

	private final ZenflowModelService models;

	private final ZenflowModelGateway gateway;

	private final ZenflowReferenceContextAdapter references;

	private final ZenflowReferenceSnapshots snapshots;

	private final ObjectProvider<SkillBindingResolver> resolver;

	private final ObjectProvider<SkillExecutionService> execution;

	private final TransactionTemplate tx;

	public ZenflowPreviewRuntime(JdbcTemplate jdbc, ObjectMapper json, ZenflowProperties product,
			ZenflowAdminAccess administrators, ZenflowModelService models, ZenflowModelGateway gateway,
			ZenflowReferenceContextAdapter references, ZenflowReferenceSnapshots snapshots,
			ObjectProvider<SkillBindingResolver> resolver, ObjectProvider<SkillExecutionService> execution,
			PlatformTransactionManager manager) {
		this.jdbc = jdbc;
		this.json = json;
		this.product = product;
		this.administrators = administrators;
		this.models = models;
		this.gateway = gateway;
		this.references = references;
		this.snapshots = snapshots;
		this.resolver = resolver;
		this.execution = execution;
		this.tx = new TransactionTemplate(manager);
	}

	public boolean supports(IdentityEnvelope identity) {
		return "ADMIN_USER".equals(identity.subjectType())
				&& ZenflowPreviewSkillPolicy.PURPOSE.equals(identity.purpose())
				&& Long.toString(product.getTenantId()).equals(identity.tenantId());
	}

	private <T> T delegated(AssistantRunRecord run, Supplier<T> action) {
		var value = new java.util.concurrent.atomic.AtomicReference<T>();
		administrators.runDelegated(Long.parseLong(run.ownerUserId()), () -> value.set(action.get()));
		return value.get();
	}

	public Outcome execute(AssistantRunRecord run, Lease lease, java.util.function.Consumer<ModelChunk> observer) {
		if (!supports(run.input().identity()) || active.get() != null)
			throw failed("PREVIEW_NOT_AUTHORIZED");
		lease.current();
		return delegated(run, () -> {
			var context = authorizeRun(run, false);
			var identity = new Identity(run.tenantId(), run.ownerUserId(), run.ownerUserId(),
					run.input().identity().correlationId());
			var scope = new Scope(run, lease, identity,
					ZenflowPreviewSkillPolicy.consumer(context.get("id").toString()), observer);
			active.set(scope);
			try {
				var resolution = resolver.getObject()
					.resolve(new ResolveRequest(identity, scope.consumer, null, run.runId(), "{}"));
				scope.snapshot = resolution.snapshot();
				var result = execution.getObject()
					.execute(new ExecuteRequest(identity, scope.consumer, resolution.resolvedSnapshotToken(),
							serialize(
									Map.of("assistantRunId", run.runId(), "inputDigest", context.get("input_digest"))),
							null, "invelle-preview:" + run.runId(), null));
				if (result.status() != SkillContract.RunStatus.SUCCEEDED || !scope.finished || scope.model == null)
					throw failed("PREVIEW_RESULT_UNAVAILABLE");
				lease.current();
				authorizeRun(run, false);
				return new Outcome(scope.model,
						new AssistantExecutionTrace(run.runId(), ExecutionPlan.PRIMARY_ONLY, "NATIVE_MANAGED",
								context.get("companion_revision_id").toString(), null, null, null,
								List.of(scope.snapshot.skillId()), null, null, null, scope.model.provider(),
								scope.model.modelName(), null, null, null, "NATIVE_GATEWAY", identity.correlationId(),
								"SUCCEEDED", List.of(scope.snapshot.skillId()),
								Long.valueOf(scope.snapshot.releaseId()), null, null, scope.snapshot.releaseDigest()));
			}
			catch (RuntimeException failure) {
				var nativeFailure = ZenflowCompanionFailures.modelFailure(failure);
				if (nativeFailure != null)
					throw nativeFailure;
				throw failed("PREVIEW_EXECUTION_FAILED");
			}
			finally {
				active.remove();
			}
		});
	}

	private Map<String, Object> authorizeRun(AssistantRunRecord run, boolean lock) {
		if (!supports(run.input().identity()) || !run.ownerUserId().equals(run.input().identity().userId()))
			throw failed("PREVIEW_NOT_AUTHORIZED");
		var administrator = administrators.require();
		if (!run.ownerUserId().equals(administrator.getId().toString())
				|| !run.tenantId().equals(administrator.getTenantId().toString()))
			throw failed("PREVIEW_NOT_AUTHORIZED");
		var rows = jdbc.queryForList(
				"SELECT p.* FROM zf_companion_preview p JOIN ai_assistant_session s ON s.id=p.session_id AND s.tenant_id=p.tenant_id AND s.owner_user_id=p.administrator_id AND s.owner_subject_type='ADMIN_USER' WHERE p.tenant_id=? AND p.administrator_id=? AND p.session_id=? AND p.run_id=? AND p.status='ACTIVE' AND p.expires_at>UTC_TIMESTAMP(3) AND s.status='ACTIVE'"
						+ (lock ? " FOR UPDATE" : ""),
				run.tenantId(), run.ownerUserId(), run.sessionId(), run.runId());
		if (rows.size() != 1)
			throw failed("PREVIEW_NOT_AUTHORIZED");
		var preview = rows.get(0);
		var input = input(preview);
		if (!input.question().equals(run.input().content()) || !input.digest(json).equals(preview.get("input_digest"))
				|| !run.input().providerIds().isEmpty() || !run.input().attachments().isEmpty())
			throw failed("PREVIEW_INPUT_CHANGED");
		var revision = revision(preview);
		for (String field : List.of("skill_release_id", "release_digest", "model_id", "model_config_digest"))
			if (!Objects.toString(preview.get(field), "").equals(Objects.toString(revision.get(field), "")))
				throw failed("PREVIEW_REVISION_CHANGED");
		if (!new ZenflowCompanionPackage(json)
			.modelDigest(preview.get("model_id").toString(),
					models.publishedSnapshot(preview.get("model_id").toString()))
			.equals(preview.get("model_config_digest")))
			throw failed("PREVIEW_MODEL_CHANGED");
		var bindings = jdbc.queryForList(
				"SELECT b.id FROM ai_skill_binding b JOIN ai_skill_binding_revision r ON r.binding_id=b.id AND r.tenant_id=b.tenant_id AND r.id=? WHERE b.id=? AND b.tenant_id=? AND b.consumer_type=? AND b.consumer_id=? AND b.slot_code='PREVIEW' AND b.binding_key='default' AND b.active_revision_id=r.id AND r.enabled=1 AND r.release_id=?",
				preview.get("binding_revision_id"), preview.get("skill_binding_id"), run.tenantId(),
				ZenflowPreviewSkillPolicy.TYPE, preview.get("id").toString(), preview.get("skill_release_id"));
		if (bindings.size() != 1)
			throw failed("PREVIEW_BINDING_CHANGED");
		selected(preview, revision);
		return preview;
	}

	private Map<String, Object> revision(Map<String, Object> preview) {
		var rows = jdbc.queryForList(
				"SELECT r.* FROM zf_companion_revision r JOIN zf_companion_config c ON c.id=r.config_id AND c.tenant_id=r.tenant_id WHERE r.id=? AND r.tenant_id=? AND r.state='READY' AND c.config_key='default'",
				preview.get("companion_revision_id"), preview.get("tenant_id"));
		if (rows.size() != 1)
			throw failed("PREVIEW_REVISION_CHANGED");
		return rows.get(0);
	}

	private ZenflowPreviewInput input(Map<String, Object> preview) {
		try {
			return ZenflowPreviewInput.parse(preview.get("question"), preview.get("locale"),
					json.readValue(preview.get("history_json").toString(), List.class));
		}
		catch (java.io.IOException failure) {
			throw failed("PREVIEW_INPUT_INVALID");
		}
	}

	private ZenflowReferencePolicy policy(Map<String, Object> revision) {
		try {
			return ZenflowReferencePolicy.parse(json.readValue(revision.get("retrieval_policy_json").toString(),
					new TypeReference<Map<String, Object>>() {
					}));
		}
		catch (java.io.IOException failure) {
			throw failed("REFERENCE_POLICY_INVALID");
		}
	}

	private List<ZenflowReferenceSnapshots.Snapshot> selected(Map<String, Object> preview,
			Map<String, Object> revision) {
		if (preview.get("reference_selection_json") == null)
			return List.of();
		try {
			Map<String, String> selected = json.readValue(preview.get("reference_selection_json").toString(),
					new TypeReference<Map<String, String>>() {
					});
			if (selected.size() > 26)
				throw failed("REFERENCE_RETRIEVAL_INVALID");
			return selected.isEmpty() ? List.of() : snapshots.selected(revision.get("id").toString(), policy(revision),
					revision.get("reference_digest").toString(), selected);
		}
		catch (java.io.IOException failure) {
			throw failed("REFERENCE_RETRIEVAL_INVALID");
		}
	}

	private Checked require(Identity identity, Consumer consumer, String runId) {
		var scope = active.get();
		if (scope == null || !scope.identity.equals(identity) || !scope.consumer.equals(consumer)
				|| !scope.run.runId().equals(runId))
			throw failed("PREVIEW_NOT_AUTHORIZED");
		scope.lease.current();
		return new Checked(scope, authorizeRun(scope.run, false));
	}

	public boolean permits(Identity identity, Consumer consumer, String runId) {
		try {
			require(identity, consumer, runId);
			return true;
		}
		catch (RuntimeException failure) {
			return false;
		}
	}

	public String pinned(Identity identity, Consumer consumer, String runId) {
		return require(identity, consumer, runId).preview().get("binding_revision_id").toString();
	}

	public void authorize(Identity identity, Consumer consumer, SkillConsumerPolicy.BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		var scope = active.get();
		if (scope == null)
			throw failed("PREVIEW_NOT_AUTHORIZED");
		var preview = require(identity, consumer, scope.run.runId()).preview();
		if (!Objects.toString(preview.get("binding_revision_id")).equals(binding.revisionId())
				|| !Objects.toString(preview.get("skill_release_id")).equals(release.releaseId())
				|| !preview.get("release_digest").equals(release.releaseDigest())
				|| !preview.get("model_id").toString().equals(release.executorRef().resourceId()))
			throw failed("PREVIEW_REVISION_CHANGED");
	}

	public boolean available(Consumer consumer, ResolvedSkillSnapshot release) {
		if (!ZenflowPreviewSkillPolicy.TYPE.equals(consumer.type()) || !"PREVIEW".equals(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()) || release.runtimeKind() != RuntimeKind.PROMPT
				|| !"PROMPT".equals(release.executorRef().executorKey()) || release.executorRef().provider() != null
				|| !release.resourceRefs().isEmpty())
			return false;
		try {
			models.publishedSnapshot(release.executorRef().resourceId());
			return true;
		}
		catch (RuntimeException failure) {
			return false;
		}
	}

	public String authorizeInput(Identity identity, Consumer consumer, String runId, String inputJson) {
		var preview = require(identity, consumer, runId).preview();
		try {
			var body = json.readTree(inputJson);
			if (body == null || !body.isObject() || body.size() != 2
					|| !runId.equals(body.path("assistantRunId").asText())
					|| !preview.get("input_digest").equals(body.path("inputDigest").asText()))
				throw failed("PREVIEW_INPUT_INVALID");
			return body.toString();
		}
		catch (java.io.IOException failure) {
			throw failed("PREVIEW_INPUT_INVALID");
		}
	}

	public SkillPromptInvocationPort.Output invoke(String skillRunId, Identity identity, Consumer consumer,
			ResolvedSkillSnapshot release, String inputJson, Runnable beforeInvocation) {
		var scope = active.get();
		if (scope == null)
			throw failed("PREVIEW_NOT_AUTHORIZED");
		var checked = require(identity, consumer, scope.run.runId());
		if (!release.equals(scope.snapshot))
			throw failed("PREVIEW_REVISION_CHANGED");
		var preview = checked.preview();
		var input = input(preview);
		var revision = revision(preview);
		var model = gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, scope.run.runId(),
				scope.run.input().identity(), preview.get("model_id").toString(), true, DataClassification.PUBLIC,
				false));
		var hits = references.retrieve(revision, policy(revision), input.question(), () -> {
			scope.lease.current();
			authorizeRun(scope.run, false);
		});
		var hashes = new LinkedHashMap<String, String>();
		for (var hit : hits)
			hashes.put(hit.id(), hit.contentHash());
		tx.executeWithoutResult(status -> {
			scope.lease.current();
			authorizeRun(scope.run, true);
			if (jdbc.update(
					"UPDATE zf_companion_preview SET reference_selection_json=?,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? AND status='ACTIVE'",
					serialize(hashes), preview.get("id"), scope.run.tenantId()) != 1)
				throw failed("PREVIEW_NOT_AUTHORIZED");
		});
		var currentPreview = authorizeRun(scope.run, false);
		var fragments = new ArrayList<ModelContextFragment>();
		for (var hit : selected(currentPreview, revision))
			fragments.add(new ModelContextFragment(ZenflowReferenceRunSources.citationKey(hit.id()), hit.title(),
					hit.content(), DataClassification.PUBLIC, Map.of("sourceType", ZenflowReferenceRunSources.KIND,
							"snapshotId", hit.id(), "citationVisibility", hit.citationVisibility())));
		var request = new AssistantModelRequest(AssistantModelContract.VERSION, scope.run.runId(),
				identity.correlationId(), scope.run.input().identity(), model,
				release.instructionSnapshot() + "\n仅使用管理员手工测试材料，不推断知道任何真实用户记录。以本轮明确语言 " + input.locale()
						+ " 回应。只在采用提供的资料时使用 [citationKey]，没有命中时不编造依据。",
				input.history(), fragments, input.question(), null, null, null, true);
		scope.lease.current();
		authorizeRun(scope.run, false);
		scope.lease.beforeInvocation(model);
		beforeInvocation.run();
		scope.model = model;
		boolean terminal = false;
		long sequence = 0;
		for (var chunk : gateway.stream(request).takeUntilOther(scope.lease.cancellation()).toIterable()) {
			scope.lease.current();
			authorizeRun(scope.run, false);
			if (terminal || sequence++ != chunk.sequence() || !scope.run.runId().equals(chunk.runId()))
				throw failed("MODEL_STREAM_INVALID");
			if (chunk.terminal()) {
				if (!"STOP".equalsIgnoreCase(chunk.finishReason()))
					throw failed("MODEL_STREAM_INCOMPLETE");
				terminal = true;
			}
			else {
				scope.content.append(chunk.delta());
				if (scope.content.length() > 128000)
					throw failed("MODEL_OUTPUT_TOO_LARGE");
			}
			scope.observer.accept(chunk);
		}
		if (!terminal || scope.content.isEmpty())
			throw failed("MODEL_STREAM_INCOMPLETE");
		scope.finished = true;
		return new SkillPromptInvocationPort.Output(output(scope), model.modelId(), null);
	}

	private String output(Scope scope) {
		return output(scope, ZenflowRunAuthorization.hash(scope.content.toString()));
	}

	private String output(Scope scope, String hash) {
		return serialize(
				Map.of("responseMessageId", scope.run.responseMessageId(), "resultHash", hash, "finishReason", "STOP"));
	}

	private boolean sameOutput(String expected, String outputJson) {
		try {
			return json.readTree(expected).equals(json.readTree(outputJson));
		}
		catch (java.io.IOException failure) {
			return false;
		}
	}

	public String save(Identity identity, Consumer consumer, String runId, String skillRunId, String outputJson) {
		var scope = require(identity, consumer, runId).scope();
		return tx.execute(status -> {
			scope.lease.current();
			var preview = authorizeRun(scope.run, true);
			String hash = ZenflowRunAuthorization.hash(scope.content.toString());
			if (!scope.finished || !sameOutput(output(scope, hash), outputJson))
				throw failed("PREVIEW_OUTPUT_INVALID");
			verifyResponse(scope.run, hash);
			if (jdbc.update(
					"UPDATE zf_companion_preview SET skill_run_id=?,validated_result_hash=?,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? AND status='ACTIVE'",
					skillRunId, hash, preview.get("id"), scope.run.tenantId()) != 1)
				throw failed("PREVIEW_RESULT_UNAVAILABLE");
			return "invelle-preview-response:" + scope.run.responseMessageId();
		});
	}

	public String read(Identity identity, Consumer consumer, String runId, String skillRunId, String reference) {
		var checked = require(identity, consumer, runId);
		var scope = checked.scope();
		var preview = checked.preview();
		if (!reference.equals("invelle-preview-response:" + scope.run.responseMessageId())
				|| !skillRunId.equals(preview.get("skill_run_id"))
				|| !ZenflowRunAuthorization.hash(scope.content.toString()).equals(preview.get("validated_result_hash")))
			throw failed("PREVIEW_RESULT_UNAVAILABLE");
		return output(scope);
	}

	private void verifyResponse(AssistantRunRecord run, String hash) {
		var rows = jdbc.queryForList(
				"SELECT content FROM ai_assistant_message WHERE id=? AND session_id=? AND status='ACCEPTED' FOR UPDATE",
				run.responseMessageId(), run.sessionId());
		if (rows.size() != 1 || hash == null
				|| !hash.equals(ZenflowRunAuthorization.hash(rows.get(0).get("content").toString())))
			throw failed("PREVIEW_RESULT_CHANGED");
	}

	public boolean complete(AssistantRunRecord run, Lease lease, Supplier<Boolean> engineCompletion) {
		lease.current();
		return delegated(run, () -> Boolean.TRUE.equals(tx.execute(status -> {
			lease.current();
			var preview = authorizeRun(run, true);
			verifyResponse(run, Objects.toString(preview.get("validated_result_hash"), null));
			var success = jdbc.queryForList(
					"SELECT id FROM ai_skill_run WHERE id=? AND tenant_id=? AND actor_id=? AND subject_id=? AND actor_type='ADMIN_USER' AND subject_type='ADMIN_USER' AND business_run_id=? AND status='SUCCEEDED' FOR UPDATE",
					preview.get("skill_run_id"), run.tenantId(), run.ownerUserId(), run.ownerUserId(), run.runId());
			if (success.size() != 1)
				throw failed("PREVIEW_RESULT_UNAVAILABLE");
			boolean completed = engineCompletion.get();
			if (!completed)
				status.setRollbackOnly();
			else if (jdbc.update(
					"UPDATE zf_companion_preview SET status='COMPLETED',updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? AND status='ACTIVE'",
					preview.get("id"), run.tenantId()) != 1)
				throw failed("PREVIEW_RESULT_UNAVAILABLE");
			return completed;
		})));
	}

	private String serialize(Object value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (java.io.IOException failure) {
			throw failed("PREVIEW_INPUT_INVALID");
		}
	}

	private static AssistantModelException failed(String code) {
		return new AssistantModelException(code, "试聊未完成，请检查版本和模型配置");
	}

	private record Checked(Scope scope, Map<String, Object> preview) {
	}

	private static class Scope {

		final AssistantRunRecord run;

		final Lease lease;

		final Identity identity;

		final Consumer consumer;

		final java.util.function.Consumer<ModelChunk> observer;

		final StringBuilder content = new StringBuilder();

		ResolvedSkillSnapshot snapshot;

		ResolvedAssistantModel model;

		boolean finished;

		Scope(AssistantRunRecord run, Lease lease, Identity identity, Consumer consumer,
				java.util.function.Consumer<ModelChunk> observer) {
			this.run = run;
			this.lease = lease;
			this.identity = identity;
			this.consumer = consumer;
			this.observer = observer;
		}

	}

}
