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
 * Runs one authorized parse and returns persisted suggestions for explicit confirmation.
 */
@Component
@RequiredArgsConstructor
public class ManagedTaskParseRunner {

	private final ObjectProvider<SkillBindingResolver> resolvers;

	private final ObjectProvider<SkillExecutionService> executors;

	private final ObjectProvider<TaskSkillResolver> sourceResolvers;

	private final TaskSkillResultStore results;

	private final ObjectMapper json;

	public Preview execute(Long policyId, Long parentId, String text) {
		var resolver = resolvers.getIfAvailable();
		var executor = executors.getIfAvailable();
		var sources = sourceResolvers.getIfAvailable();
		if (resolver == null || executor == null || sources == null)
			throw new IllegalStateException("EXECUTOR_UNAVAILABLE");
		var run = results.prepareParse(policyId, parentId, text);
		String businessId = run.getId().toString();
		String correlation = "work-task-parse:" + businessId;
		var identity = new Identity(run.getTenantId().toString(), run.getOwnerUserId().toString(),
				run.getOwnerUserId().toString(), correlation);
		var consumer = new Consumer("TASK", run.getPolicyId().toString(), "TASK_PARSE", "default");
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
			var output = TaskParseOutputValidator.validate(json, persisted, run.getParentTaskId());
			if (!output.equals(TaskParseOutputValidator.validate(json, result.resultJson(), run.getParentTaskId())))
				throw new IllegalStateException("TASK_SKILL_RESULT_CONFLICT");
			JsonNode warnings = output.has("warnings") ? output.get("warnings") : json.createArrayNode();
			return new Preview(output.get("tasks"), warnings, source.packageId().toString(), source.packageSha256(),
					run.getModelId().toString(), businessId, frozen.skillId(), result.actualReleaseId(),
					result.skillRunId(), true);
		}
		catch (RuntimeException failure) {
			if (failure instanceof SkillExecutionFailure recorded)
				result = recorded.result();
			throw new ManagedTaskParseFailure(businessId, resolution == null ? null : resolution.snapshot().skillId(),
					result == null ? null : result.actualReleaseId(), result == null ? null : result.skillRunId(),
					failure);
		}
	}

	public record Preview(JsonNode drafts, JsonNode warnings, String skillPackageId, String skillHash, String modelId,
			String businessRunId, String skillId, String actualReleaseId, String skillRunId,
			boolean confirmationRequired) {
	}

}
