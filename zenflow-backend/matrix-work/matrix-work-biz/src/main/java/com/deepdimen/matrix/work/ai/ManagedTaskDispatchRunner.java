package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Executes one durable dispatch suggestion under an already verified worker scope.
 */
@Component
@RequiredArgsConstructor
public class ManagedTaskDispatchRunner {

	private final ObjectProvider<SkillBindingResolver> resolvers;

	private final ObjectProvider<SkillExecutionService> executors;

	private final ObjectProvider<TaskSkillResolver> sourceResolvers;

	private final TaskSkillResultStore results;

	private final TaskDispatchSkillInputs inputs;

	private final ObjectMapper json;

	public Suggestion execute() {
		var resolver = resolvers.getIfAvailable();
		var executor = executors.getIfAvailable();
		var sources = sourceResolvers.getIfAvailable();
		if (resolver == null || executor == null || sources == null)
			throw new IllegalStateException("EXECUTOR_UNAVAILABLE");
		var run = inputs.prepare();
		String businessId = run.getId().toString();
		String correlation = "work-task-dispatch:" + businessId;
		var identity = new Identity(run.getTenantId().toString(), run.getOwnerUserId().toString(),
				run.getOwnerUserId().toString(), correlation);
		var consumer = new Consumer("TASK", run.getPolicyId().toString(), "TASK_DISPATCH", "default");
		Resolution resolution = null;
		ExecuteResult result = null;
		try {
			String input = results.authorizeInput(identity, consumer, businessId, run.getInputSnapshot());
			var source = sources.resolve(run.getTenantId(), run.getSourcePackageId());
			resolution = resolver.resolve(new ResolveRequest(identity, consumer, null, businessId, "{}"));
			var frozen = resolution.snapshot();
			if (!Objects.equals(source.packageId(), run.getSourcePackageId())
					|| !Objects.equals(source.prompt(), frozen.instructionSnapshot()))
				throw new IllegalArgumentException("TASK_SKILL_INSTRUCTION_DRIFT");
			if (frozen.runtimeKind() != RuntimeKind.PROMPT || !"PROMPT".equals(frozen.executorRef().executorKey())
					|| !Objects.equals(run.getModelId().toString(), frozen.executorRef().resourceId()))
				throw new IllegalArgumentException("TASK_MODEL_BINDING_CHANGED");
			result = executor.execute(new ExecuteRequest(identity, consumer, resolution.resolvedSnapshotToken(), input,
					null, correlation, null));
			if (result.status() != RunStatus.SUCCEEDED)
				throw new IllegalStateException("SKILL_RUN_" + result.status());
			if (!frozen.releaseId().equals(result.actualReleaseId()))
				throw new IllegalStateException("EXECUTOR_VERSION_DRIFT");
			String persisted = results.read(identity, consumer, businessId, result.skillRunId(), result.resultRef());
			var output = TaskDispatchOutputValidator.validate(json, persisted, run.getInputSnapshot());
			if (!output.equals(TaskDispatchOutputValidator.validate(json, result.resultJson(), run.getInputSnapshot())))
				throw new IllegalStateException("TASK_SKILL_RESULT_CONFLICT");
			Long assignee = output.path("assigneeId").isNull() ? null
					: Long.valueOf(output.path("assigneeId").textValue());
			return new Suggestion(assignee, output, source.packageSha256(), businessId, frozen.skillId(),
					result.actualReleaseId(), result.skillRunId());
		}
		catch (RuntimeException failure) {
			if (failure instanceof SkillExecutionFailure recorded)
				result = recorded.result();
			throw new ManagedTaskDispatchFailure(businessId,
					resolution == null ? null : resolution.snapshot().skillId(), result, failure);
		}
	}

	public record Suggestion(Long assigneeId, JsonNode output, String sourcePackageHash, String businessRunId,
			String skillId, String actualReleaseId, String skillRunId) {
	}

}
