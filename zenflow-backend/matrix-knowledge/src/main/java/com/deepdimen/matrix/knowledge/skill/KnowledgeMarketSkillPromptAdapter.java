package com.deepdimen.matrix.knowledge.skill;

import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.skill.api.AgentSkillPromptAdapter;
import com.deepdimen.matrix.skill.api.AgentSkillRequestReader;
import com.deepdimen.matrix.skill.api.KnowledgeSkillRelease;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Uses the shared market request ledger and the knowledge generation service. */
@Component
public class KnowledgeMarketSkillPromptAdapter implements AgentSkillPromptAdapter {

	private final KnowledgeSkillResources resources;

	private final KnowledgeSkillGeneration generation;

	private final ObjectProvider<AgentSkillRequestReader> requests;

	private final ObjectMapper json;

	private final ObjectProvider<com.deepdimen.matrix.skill.api.AgentSkillResultContextReader> contexts;

	private final ObjectProvider<com.deepdimen.matrix.skill.api.AgentSkillStreamPort> streams;

	public KnowledgeMarketSkillPromptAdapter(KnowledgeSkillResources resources, KnowledgeSkillGeneration generation,
			ObjectProvider<AgentSkillRequestReader> requests, ObjectMapper json,
			ObjectProvider<com.deepdimen.matrix.skill.api.AgentSkillStreamPort> streams,
			ObjectProvider<com.deepdimen.matrix.skill.api.AgentSkillResultContextReader> contexts) {
		this.resources = resources;
		this.generation = generation;
		this.requests = requests;
		this.json = json;
		this.streams = streams;
		this.contexts = contexts;
	}

	@Override
	public String agentType() {
		return "knowledge";
	}

	@Override
	public boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot) {
		try {
			var fixed = KnowledgeSkillRelease.from(snapshot);
			if (requests.getIfAvailable() == null || streams.getIfAvailable() == null || consumer == null
					|| !fixed.agentId().equals(consumer.id()))
				return false;
			return fixed.digest().equals(resources.capture(consumer, fixed.modelId()).digest());
		}
		catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException failure) {
			return false;
		}
	}

	@Override
	public Output execute(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot,
			String inputJson, Runnable beforeInvocation) {
		if (runId == null || !runId.matches("[1-9][0-9]{0,18}") || identity == null || identity.correlationId() == null
				|| !identity.correlationId().matches("market-skill:[1-9][0-9]{0,18}"))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var reader = requests.getIfAvailable();
		if (reader == null)
			throw new IllegalArgumentException("AGENT_ADAPTER_UNAVAILABLE");
		String businessId = identity.correlationId().substring("market-skill:".length());
		String authorized = reader.authorizeInput(identity, consumer, businessId, inputJson);
		var input = question(authorized);
		String question = input.question();
		var contextReader = contexts.getIfAvailable();
		if (input.reference() != null && contextReader == null)
			throw new IllegalArgumentException("AGENT_ADAPTER_UNAVAILABLE");
		var reference = input.reference() == null ? null
				: contextReader.read(identity, consumer, snapshot, input.reference());
		Runnable verifyReference = () -> {
			if (reference != null
					&& !reference.equals(contextReader.read(identity, consumer, snapshot, input.reference())))
				throw new IllegalArgumentException("MARKET_RESULT_CONTEXT_INCOMPATIBLE");
		};
		resources.verify(identity, consumer, snapshot);
		var stream = streams.getIfAvailable();
		if (stream == null)
			throw new IllegalArgumentException("AGENT_ADAPTER_UNAVAILABLE");
		var onDelta = stream.open(identity, consumer, businessId, inputJson, runId, snapshot.releaseId());
		var answer = generation.generate(runId, identity, consumer, snapshot, question, () -> {
			reader.authorizeInput(identity, consumer, businessId, inputJson);
			verifyReference.run();
			beforeInvocation.run();
		}, onDelta, reference);
		verifyReference.run();
		reader.authorizeInput(identity, consumer, businessId, inputJson);
		resources.verify(identity, consumer, snapshot);
		var output = json.createObjectNode().put("answer", answer.answer());
		if (reference != null)
			output.put("resultContextRunId", reference.skillRunId());
		output.set("citations",
				json.valueToTree(answer.evidence().fragments().stream().map(fragment -> fragment.citation()).toList()));
		output.put("response_kind", answer.modelId() == null ? "fixed_reply" : "model");
		if (answer.modelId() == null)
			output.put("response_reason", answer.evidence()
				.status() == com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE
						? "INPUT_POLICY" : "NO_MATCH");
		return new Output(output.toString(), answer.modelId(), answer.configurationDigest(),
				answer.modelId() == null
						? com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.InvocationMode.FIXED_REPLY
						: com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.InvocationMode.MODEL);
	}

	private record Input(String question, String reference) {
	}

	private Input question(String text) {
		try {
			var input = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(text);
			if (input == null || !input.isObject() || !input.path("query").isTextual()
					|| input.path("query").asText().isBlank() || input.path("query").asText().length() > 16000)
				throw new IllegalArgumentException("INPUT_INVALID");
			var fields = input.fieldNames();
			while (fields.hasNext())
				if (!Set.of("query", "inputs", "files", "conversation_id", "response_mode", "result_context_request_id")
					.contains(fields.next()))
					throw new IllegalArgumentException("INPUT_INVALID");
			if ((input.has("inputs") && (!input.path("inputs").isObject() || !input.path("inputs").isEmpty()))
					|| (input.has("files") && (!input.path("files").isArray() || !input.path("files").isEmpty()))
					|| (input.has("conversation_id") && (!input.path("conversation_id").isTextual()
							|| !input.path("conversation_id").asText().isEmpty())))
				throw new IllegalArgumentException("KNOWLEDGE_INPUT_UNSUPPORTED");
			if (input.has("response_mode")
					&& !Set.of("streaming", "blocking").contains(input.path("response_mode").asText()))
				throw new IllegalArgumentException("INPUT_INVALID");
			String reference = null;
			if (input.has("result_context_request_id")) {
				if (!input.path("result_context_request_id").isTextual()
						|| !input.path("result_context_request_id").asText().matches("[A-Za-z0-9_-]{1,64}"))
					throw new IllegalArgumentException("INPUT_INVALID");
				reference = input.path("result_context_request_id").asText();
			}
			return new Input(input.path("query").asText(), reference);
		}
		catch (java.io.IOException failure) {
			throw new IllegalArgumentException("INPUT_INVALID");
		}
	}

}
