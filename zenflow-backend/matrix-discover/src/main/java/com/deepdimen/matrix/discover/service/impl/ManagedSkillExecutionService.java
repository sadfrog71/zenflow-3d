package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import com.deepdimen.matrix.discover.skill.*;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * External execution never runs inside a database transaction or an automatic retry loop.
 */
@Service
@RequiredArgsConstructor
public class ManagedSkillExecutionService implements SkillExecutionService {

	private final SkillResolutionTokenStore tokens;

	private final ManagedSkillBindingResolver resolver;

	private final ManagedSkillRunIndex index;

	private final ManagedPromptSkillExecutor prompt;

	private final List<SkillResultStore> results;

	private final ObjectMapper json;

	@Override
	public boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot) {
		return prompt.available(consumer, snapshot)
				&& results.stream().filter(r -> r.consumerType().equals(consumer.type())).count() == 1
				&& results.stream().anyMatch(r -> r.supportsConsumer(consumer));
	}

	@Override
	public boolean fallbackAllowed(ExecuteRequest request, String mode) {
		if (!"WORK_BRIEF".equals(request.consumer().type()) || !"DETERMINISTIC".equals(mode))
			return false;
		var resolved = tokens.require(request.resolvedSnapshotToken(), request.identity(), request.consumer());
		var binding = resolver.revalidate(resolved);
		var stores = results.stream().filter(r -> r.consumerType().equals(request.consumer().type())).toList();
		if (stores.size() != 1 || request.authorizedInputRef() != null)
			return false;
		stores.get(0)
			.authorizeInput(request.identity(), request.consumer(), resolved.businessRunId(), request.inputJson());
		try {
			var fallback = json.reader()
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(binding.fallbackPolicyJson());
			return fallback != null && fallback.isObject() && fallback.size() == 1
					&& mode.equals(fallback.path("mode").asText());
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("FALLBACK_POLICY_INVALID", ex);
		}
	}

	@Override
	public ExecuteResult execute(ExecuteRequest request) {
		var resolved = tokens.require(request.resolvedSnapshotToken(), request.identity(), request.consumer());
		resolver.revalidate(resolved);
		var stores = results.stream().filter(r -> r.consumerType().equals(request.consumer().type())).toList();
		if (stores.size() != 1 || !available(request.consumer(), resolved.snapshot()))
			throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
		if (request.authorizedInputRef() != null)
			throw new IllegalArgumentException("INPUT_REFERENCE_UNSUPPORTED");
		var store = stores.get(0);
		String input = store.authorizeInput(request.identity(), request.consumer(), resolved.businessRunId(),
				request.inputJson());
		try {
			var inputNode = json.readTree(input);
			var params = json.readTree(resolved.parametersJson());
			if (inputNode == null || !inputNode.isObject() || params == null || !params.isObject())
				throw new IllegalArgumentException("INPUT_INVALID");
			var effective = ((com.fasterxml.jackson.databind.node.ObjectNode) inputNode).deepCopy();
			params.fields().forEachRemaining(field -> {
				// Business snapshots are authoritative. Parameters cannot overwrite their
				// fields.
				if (effective.has(field.getKey()))
					throw new IllegalArgumentException("INPUT_PARAMETER_COLLISION");
				effective.set(field.getKey(), field.getValue());
			});
			new ManagedSkillSchemaValidator().validateValue(json.readTree(resolved.snapshot().inputSchemaJson()),
					effective);
			input = effective.toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("INPUT_INVALID", ex);
		}
		var run = index.reserve(request, resolved, input);
		if (!index.claim(run))
			return replay(request, resolved, store, run);
		boolean[] invoked = { false };
		try {
			resolver.revalidate(resolved);
			var output = prompt.execute(run.getId().toString(), request.identity(), request.consumer(),
					resolved.snapshot(), input, () -> invoked[0] = true);
			if (!invoked[0])
				throw new IllegalStateException("EXECUTOR_INVOCATION_NOT_REPORTED");
			resolver.revalidate(resolved);
			String ref = store.save(request.identity(), request.consumer(), resolved.businessRunId(),
					run.getId().toString(), output.outputJson());
			String usage = json.createObjectNode()
				.put("modelId", output.modelId())
				.put("usageStatus", "UNAVAILABLE")
				.toString();
			index.finish(run, RunStatus.SUCCEEDED, ref, null, output.executorVersion(), usage);
			return new ExecuteResult(run.getId().toString(), RunStatus.SUCCEEDED, output.outputJson(), ref, List.of(),
					resolved.snapshot().releaseId(), output.executorVersion(), usage, null);
		}
		catch (RuntimeException failure) {
			boolean invalidOutput = invoked[0]
					&& (failure instanceof com.deepdimen.matrix.assistant.run.AssistantJsonSchemaValidator.SchemaViolationException
							|| (failure instanceof IllegalArgumentException && failure.getMessage() != null
									&& java.util.Set.of("OUTPUT_INVALID", "OUTPUT_INFORMATION_INVALID")
										.contains(failure.getMessage())));
			RunStatus status = invoked[0] && !invalidOutput ? RunStatus.UNKNOWN : RunStatus.FAILED;
			ErrorCode error = invalidOutput ? ErrorCode.OUTPUT_INVALID
					: (invoked[0] ? ErrorCode.RESULT_UNKNOWN : preparationError(failure));
			try {
				index.finish(run, status, null, error, null, null);
			}
			catch (RuntimeException persistenceFailure) {
				failure.addSuppressed(persistenceFailure);
				throw failure;
			}
			throw new SkillExecutionFailure(new ExecuteResult(run.getId().toString(), status, null, null, List.of(),
					resolved.snapshot().releaseId(), null, null, error), failure);
		}
	}

	private ErrorCode preparationError(RuntimeException failure) {
		if (failure instanceof com.deepdimen.matrix.assistant.run.AssistantJsonSchemaValidator.SchemaViolationException)
			return ErrorCode.INPUT_INVALID;
		String code = failure.getMessage();
		if ("MODEL_BINDING_CHANGED".equals(code) || "TASK_MODEL_BINDING_CHANGED".equals(code))
			return ErrorCode.EXECUTOR_VERSION_DRIFT;
		if ("TASK_POLICY_UNAVAILABLE".equals(code))
			return ErrorCode.CONSUMER_NOT_AUTHORIZED;
		if (code == null)
			return ErrorCode.EXECUTOR_UNAVAILABLE;
		return switch (code) {
			case "INPUT_INVALID" -> ErrorCode.INPUT_INVALID;
			case "CONSUMER_NOT_AUTHORIZED" -> ErrorCode.CONSUMER_NOT_AUTHORIZED;
			case "RELEASE_REVOKED" -> ErrorCode.RELEASE_REVOKED;
			case "RELEASE_NOT_AVAILABLE" -> ErrorCode.RELEASE_NOT_AVAILABLE;
			case "BINDING_NOT_ACTIVE" -> ErrorCode.BINDING_NOT_ACTIVE;
			case "BINDING_REVISION_CONFLICT" -> ErrorCode.BINDING_REVISION_CONFLICT;
			case "AGENT_SKILL_UNSUPPORTED" -> ErrorCode.AGENT_SKILL_UNSUPPORTED;
			default -> ErrorCode.EXECUTOR_UNAVAILABLE;
		};
	}

	private ExecuteResult replay(ExecuteRequest request, SkillResolutionTokenStore.StoredResolution resolved,
			SkillResultStore store, AiSkillRunEntity run) {
		RunStatus status = RunStatus.valueOf(run.getStatus());
		String output = null;
		if (status == RunStatus.SUCCEEDED || status == RunStatus.DEGRADED) {
			output = store.read(request.identity(), request.consumer(), resolved.businessRunId(),
					run.getId().toString(), run.getResultRef());
		}
		// A lost claim may leave this local snapshot QUEUED; report RUNNING, never
		// execute again.
		if (status == RunStatus.QUEUED)
			status = RunStatus.RUNNING;
		return new ExecuteResult(run.getId().toString(), status, output, run.getResultRef(), List.of(),
				run.getReleaseId().toString(), run.getExecutorVersion(), run.getUsageJson(),
				run.getErrorCode() == null ? null : ErrorCode.valueOf(run.getErrorCode()));
	}

}
