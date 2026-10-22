package com.deepdimen.matrix.knowledge.skill;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelPolicy;
import com.deepdimen.matrix.knowledge.assistant.LocalAssistantModelGateway;
import com.deepdimen.matrix.skill.api.SkillContract.*;

/** Internal single-turn generation using the existing governed model stream. */
@Component
public class KnowledgeSkillGeneration {

	private final KnowledgeSkillResources resources;

	private final KnowledgeSkillRetrieval retrieval;

	private final java.util.function.Supplier<LocalAssistantModelGateway> modelGateway;

	private final KnowledgeSkillInputPolicy inputPolicy;

	public KnowledgeSkillGeneration(KnowledgeSkillResources resources, KnowledgeSkillRetrieval retrieval,
			LocalAssistantModelGateway models, KnowledgeSkillInputPolicy inputPolicy) {
		this.resources = resources;
		this.retrieval = retrieval;
		this.modelGateway = () -> models;
		this.inputPolicy = inputPolicy;
	}

	@org.springframework.beans.factory.annotation.Autowired
	public KnowledgeSkillGeneration(KnowledgeSkillResources resources, KnowledgeSkillRetrieval retrieval,
			org.springframework.beans.factory.ObjectProvider<LocalAssistantModelGateway> models,
			KnowledgeSkillInputPolicy inputPolicy) {
		this.resources = resources;
		this.retrieval = retrieval;
		this.modelGateway = models::getIfAvailable;
		this.inputPolicy = inputPolicy;
	}

	public record Answer(String answer, String modelId, String configurationDigest, ProviderResult evidence) {
	}

	public Answer generate(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot release,
			String question, Runnable beforeInvocation) {
		return generate(runId, identity, consumer, release, question, beforeInvocation, delta -> {
		});
	}

	public Answer generate(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot release,
			String question, Runnable beforeInvocation,
			com.deepdimen.matrix.skill.api.AgentSkillStreamPort.Observer onDelta) {
		return generate(runId, identity, consumer, release, question, beforeInvocation, onDelta, null);
	}

	public Answer generate(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot release,
			String question, Runnable beforeInvocation,
			com.deepdimen.matrix.skill.api.AgentSkillStreamPort.Observer onDelta,
			com.deepdimen.matrix.skill.api.AgentSkillResultContextReader.Context reference) {
		java.util.Objects.requireNonNull(onDelta);
		if (runId == null || runId.isBlank() || question == null || question.isBlank() || question.length() > 16000
				|| beforeInvocation == null)
			throw new IllegalArgumentException("INPUT_INVALID");
		var fixed = resources.verify(identity, consumer, release);
		String policyReply = inputPolicy.fixedReply(identity, consumer, release, question);
		beforeInvocation.run();
		if (policyReply != null) {
			resources.verify(identity, consumer, release);
			return new Answer(policyReply, null, fixed.digest(), new ProviderResult(null,
					com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId.KNOWLEDGE,
					ProviderStatus.FORBIDDEN_SCOPE, java.time.Instant.now(), 0, 0, false, List.of(), List.of()));
		}
		onDelta.phase(com.deepdimen.matrix.skill.api.AgentSkillStreamPort.Phase.RETRIEVING);
		var evidence = retrieval.query(identity, consumer, release, question);
		if (evidence.status() == ProviderStatus.NO_MATCH) {
			String reply = emptyReply(identity, consumer, release, fixed);
			if (reply != null) {
				resources.verify(identity, consumer, release);
				return new Answer(reply, null, fixed.digest(), evidence);
			}
		}
		else if (evidence.status() != ProviderStatus.OK)
			throw new IllegalStateException("KNOWLEDGE_RETRIEVAL_UNAVAILABLE");
		var fragments = new ArrayList<ModelContextFragment>();
		for (var fragment : evidence.fragments()) {
			if (fragment.citation() == null || fragment.excerpt() == null || fragment.excerpt().isBlank())
				throw new IllegalStateException("KNOWLEDGE_EVIDENCE_INVALID");
			fragments.add(new ModelContextFragment("C" + (fragments.size() + 1), fragment.title(), fragment.excerpt(),
					fragment.dataClassification(),
					Map.of("provider", fragment.provider().value(), "sourceType", fragment.sourceType(), "sourceId",
							fragment.sourceId(), "citationId", fragment.citation().citationId())));
		}
		if (reference != null) {
			if (!release.releaseId().equals(reference.releaseId()) || reference.question() == null
					|| reference.answer() == null
					|| reference.question().length() + reference.answer().length() > 24000)
				throw new IllegalArgumentException("MARKET_RESULT_CONTEXT_INCOMPATIBLE");
			fragments.add(new ModelContextFragment("R1", "用户明确引用的历史执行结果",
					"原问题：\n" + reference.question() + "\n原回答（历史结果，可能已过时）：\n" + reference.answer(),
					DataClassification.SENSITIVE, Map.of("provider", "MARKET_RESULT", "skillRunId",
							reference.skillRunId(), "releaseId", reference.releaseId())));
		}
		var models = modelGateway.get();
		if (models == null)
			throw new IllegalArgumentException("AGENT_ADAPTER_UNAVAILABLE");
		var model = models.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, runId, fixed.identity(),
				fixed.modelId(), false, AssistantModelPolicy.effectiveContextClassification(fragments, List.of()),
				!fragments.isEmpty()));
		if (!fixed.modelId().equals(model.modelId()))
			throw new IllegalArgumentException("MODEL_BINDING_CHANGED");
		var request = new AssistantModelRequest(AssistantModelContract.VERSION, runId, identity.correlationId(),
				fixed.identity(), model, release.instructionSnapshot(), List.of(), fragments, question, null, null,
				null, false);
		AssistantModelPolicy.validateForStreaming(request);
		resources.verify(identity, consumer, release);
		onDelta.phase(com.deepdimen.matrix.skill.api.AgentSkillStreamPort.Phase.GENERATING);
		var state = models.stream(request).reduce(new Response(runId), (response, chunk) -> {
			response.accept(chunk);
			if (!chunk.delta().isEmpty())
				onDelta.accept(chunk.delta());
			return response;
		}).block(Duration.ofSeconds(90));
		if (state == null || !state.terminal || state.text.toString().isBlank())
			throw new IllegalStateException("KNOWLEDGE_RESULT_UNKNOWN");
		resources.verify(identity, consumer, release);
		return new Answer(state.text.toString(), model.modelId(), fixed.digest(), evidence);
	}

	/** Any dataset forbidding model fallback retains its configured fixed response. */
	private String emptyReply(Identity identity, Consumer consumer, ResolvedSkillSnapshot release,
			KnowledgeSkillResources.Capture fixed) {
		var replies = new java.util.LinkedHashSet<String>();
		for (String id : fixed.configuration().datasetIds()) {
			var dataset = resources.resolveRetrieval(identity, consumer, release, Long.valueOf(id)).dataset();
			if (com.deepdimen.matrix.common.core.constant.enums.YesNoEnum.NO.getCode()
				.equals(dataset.getEmptyLlmFlag())) {
				if (dataset.getEmptyDesc() == null || dataset.getEmptyDesc().isBlank())
					throw new IllegalArgumentException("KNOWLEDGE_EMPTY_REPLY_UNCONFIGURED");
				replies.add(dataset.getEmptyDesc());
			}
		}
		if (replies.isEmpty())
			return null;
		String reply = String.join("\n", replies);
		if (reply.length() > 16000)
			throw new IllegalArgumentException("OUTPUT_INVALID");
		return reply;
	}

	private static final class Response {

		private final String runId;

		private final StringBuilder text = new StringBuilder();

		private long sequence;

		private boolean terminal;

		private Response(String runId) {
			this.runId = runId;
		}

		private Response accept(ModelChunk chunk) {
			if (terminal || !runId.equals(chunk.runId()) || chunk.sequence() != sequence++
					|| text.length() + chunk.delta().length() > 32000
					|| (chunk.terminal() && !"STOP".equals(chunk.finishReason())))
				throw new IllegalStateException("KNOWLEDGE_RESULT_UNKNOWN");
			text.append(chunk.delta());
			terminal = chunk.terminal();
			return this;
		}

	}

}
