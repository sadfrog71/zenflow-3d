package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Uses only the existing information model boundary with explicit fixed-model support.
 */
@Component
@RequiredArgsConstructor
public class InformationSkillPromptPort implements SkillPromptInvocationPort {

	private final ObjectProvider<InformationReviewModel> models;

	private final InformationSkillCollectionScope scope;

	private final InformationSkillResultStore results;

	private final ObjectMapper json;

	public String consumerType() {
		return "INFORMATION";
	}

	public boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot) {
		if (!consumerType().equals(consumer.type()) || !Set.of("ANALYZE", "REVIEW").contains(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()) || snapshot.runtimeKind() != RuntimeKind.PROMPT
				|| !"PROMPT".equals(snapshot.executorRef().executorKey()) || snapshot.executorRef().resourceId() == null
				|| snapshot.executorRef().resourceId().isBlank() || !snapshot.resourceRefs().isEmpty()
				|| !snapshot.allowedParameters().isEmpty()
				|| !InformationSkillInstructions.source(consumer.slotCode())
					.instruction()
					.equals(snapshot.instructionSnapshot()))
			return false;
		var model = models.getIfAvailable();
		return model != null && model.supportsManagedModel(snapshot.executorRef().resourceId());
	}

	public Output execute(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot,
			String inputJson, Runnable beforeInvocation) {
		if (!available(consumer, snapshot))
			throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
		var context = scope.require();
		String prefix = "work-information:";
		if (identity.correlationId() == null || !identity.correlationId().startsWith(prefix))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		String authorized = results.authorizeInput(identity, consumer,
				identity.correlationId().substring(prefix.length()), inputJson);
		try {
			JsonNode policy = json.readTree(context.policySnapshot());
			if (!snapshot.executorRef().resourceId().equals(policy.path("modelName").asText()))
				throw new IllegalArgumentException("MODEL_BINDING_CHANGED");
			ObjectNode input = (ObjectNode) json.readTree(authorized);
			JsonNode candidate = input.remove("analysis");
			var evidence = InformationSkillInput.restore(json, input.toString());
			String prompt;
			if ("ANALYZE".equals(consumer.slotCode())) {
				if (candidate != null)
					throw new IllegalArgumentException("INPUT_INVALID");
				prompt = evidence.analysisPrompt();
			}
			else {
				var analysis = InformationSkillOutput.validate(json, "ANALYZE",
						candidate == null ? null : candidate.toString());
				if (!analysis.path("eligible").booleanValue())
					throw new IllegalArgumentException("INPUT_INVALID");
				prompt = evidence.reviewPrompt(analysis.toString());
			}
			var model = models.getIfAvailable();
			if (model == null || !model.supportsManagedModel(snapshot.executorRef().resourceId()))
				throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
			scope.require();
			beforeInvocation.run();
			var response = model.chatManaged(
					"work-info-" + consumer.slotCode().toLowerCase(java.util.Locale.ROOT) + "-" + runId,
					snapshot.executorRef().resourceId(), snapshot.instructionSnapshot(), prompt);
			if (response == null || !snapshot.executorRef().resourceId().equals(response.actualModelId()))
				throw new IllegalArgumentException("MODEL_BINDING_CHANGED");
			scope.require();
			return new Output(
					InformationSkillOutput.validate(json, consumer.slotCode(), response.outputJson()).toString(),
					response.actualModelId());
		}
		catch (java.io.IOException failure) {
			throw new IllegalArgumentException("INPUT_INVALID", failure);
		}
	}

}
