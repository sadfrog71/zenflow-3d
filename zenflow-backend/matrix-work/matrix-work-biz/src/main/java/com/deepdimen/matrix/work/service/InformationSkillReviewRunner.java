package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Two sequential, fixed-version stages; no fallback or publication side effects. */
@Component
@RequiredArgsConstructor
public class InformationSkillReviewRunner {

	private final ObjectProvider<SkillBindingResolver> resolvers;

	private final ObjectProvider<SkillExecutionService> executors;

	private final InformationSkillResultStore results;

	private final ObjectMapper json;

	public InformationReviewService.ReviewResult review(InformationSkillInput input) {
		var resolver = resolvers.getIfAvailable();
		var executor = executors.getIfAvailable();
		if (resolver == null || executor == null)
			throw new IllegalStateException("EXECUTOR_UNAVAILABLE");
		var analyze = stage(results.prepareAnalysis(input), resolver, executor, null);
		if (!analyze.output().path("eligible").booleanValue())
			return InformationReviewService.ReviewResult.quarantined(analyze.output().path("reason").asText());
		var reviewed = stage(results.prepareReview(analyze.invocationId(), analyze.result()), resolver, executor,
				analyze.result().skillRunId());
		if (!reviewed.output().path("approved").booleanValue())
			return InformationReviewService.ReviewResult.quarantined(reviewed.output().path("reason").asText());
		var value = analyze.output();
		return new InformationReviewService.ReviewResult(true, value.path("contentType").asText(),
				value.path("title").asText(), value.path("summary").asText(), value.path("tags").asText(),
				value.path("confidence").doubleValue(), reviewed.output().path("reason").asText());
	}

	private Stage stage(InformationSkillInvocationEntity row, SkillBindingResolver resolver,
			SkillExecutionService executor, String parentRunId) {
		String businessId = row.getId().toString(), correlation = "work-information:" + businessId;
		var identity = new Identity(row.getTenantId().toString(), row.getActorId(), row.getActorId(), correlation);
		var consumer = new Consumer("INFORMATION", row.getPolicyId().toString(), row.getSlotCode(), "default");
		String input = results.authorizeInput(identity, consumer, businessId, row.getInputSnapshot());
		var resolved = resolver.resolve(new ResolveRequest(identity, consumer, null, businessId, "{}"));
		var frozen = resolved.snapshot();
		if (!InformationSkillInstructions.source(row.getSlotCode()).instruction().equals(frozen.instructionSnapshot()))
			throw new IllegalArgumentException("INFORMATION_INSTRUCTION_DRIFT");
		InformationSkillContract.validate(json, row.getSlotCode(), frozen);
		ExecuteResult result;
		try {
			result = executor.execute(new ExecuteRequest(identity, consumer, resolved.resolvedSnapshotToken(), input,
					null, correlation, parentRunId));
		}
		catch (SkillExecutionFailure failure) {
			throw new StageFailure(row.getSlotCode(), businessId, failure.result(), failure);
		}
		if (result.status() != RunStatus.SUCCEEDED)
			throw new StageFailure(row.getSlotCode(), businessId, result, null);
		if (!frozen.releaseId().equals(result.actualReleaseId()))
			throw new StageFailure(row.getSlotCode(), businessId, result,
					new IllegalStateException("EXECUTOR_VERSION_DRIFT"));
		String saved = results.read(identity, consumer, businessId, result.skillRunId(), result.resultRef());
		JsonNode output = InformationSkillOutput.validate(json, row.getSlotCode(), saved);
		if (!output.equals(InformationSkillOutput.validate(json, row.getSlotCode(), result.resultJson())))
			throw new StageFailure(row.getSlotCode(), businessId, result,
					new IllegalStateException("INFORMATION_RESULT_CONFLICT"));
		return new Stage(row.getId(), result, output);
	}

	private record Stage(Long invocationId, ExecuteResult result, JsonNode output) {
	}

	public static final class StageFailure extends IllegalStateException {

		private final String slot, businessRunId;

		private final ExecuteResult result;

		StageFailure(String slot, String businessRunId, ExecuteResult result, Throwable cause) {
			super("INFORMATION_STAGE_NOT_ACCEPTED", cause);
			this.slot = slot;
			this.businessRunId = businessRunId;
			this.result = result;
		}

		public String slot() {
			return slot;
		}

		public String businessRunId() {
			return businessRunId;
		}

		public ExecuteResult result() {
			return result;
		}

	}

}
