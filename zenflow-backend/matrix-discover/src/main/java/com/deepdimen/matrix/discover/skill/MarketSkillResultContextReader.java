package com.deepdimen.matrix.discover.skill;

import org.springframework.stereotype.Component;
import com.deepdimen.matrix.skill.api.AgentSkillResultContextReader;
import com.deepdimen.matrix.skill.api.SkillRunMetadataReader;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reuses the same authorized ledger and result reader as recovery; never reserves work.
 */
@Component
public class MarketSkillResultContextReader implements AgentSkillResultContextReader {

	private final MarketSkillRequestStore requests;

	private final SkillRunMetadataReader metadata;

	private final MarketSkillResultStore results;

	private final ObjectMapper json;

	public MarketSkillResultContextReader(MarketSkillRequestStore requests, SkillRunMetadataReader metadata,
			MarketSkillResultStore results, ObjectMapper json) {
		this.requests = requests;
		this.metadata = metadata;
		this.results = results;
		this.json = json;
	}

	@Override
	public Context read(Identity identity, Consumer consumer, ResolvedSkillSnapshot release, String clientRequestId) {
		if (identity == null || consumer == null || release == null || !identity.actorId().equals(identity.subjectId())
				|| !"MARKET_AGENT".equals(consumer.type()) || !"CHAT".equals(consumer.slotCode()))
			throw denied();
		var row = requests.findByClientRequest(Long.valueOf(consumer.id()), clientRequestId);
		if (row == null || row.getId() == null || !identity.tenantId().equals(String.valueOf(row.getTenantId()))
				|| !identity.actorId().equals(String.valueOf(row.getActorId()))
				|| !consumer.id().equals(String.valueOf(row.getAgentId()))
				|| !consumer.bindingKey().equals(row.getBindingKey()))
			throw denied();
		String businessId = row.getId().toString();
		var run = metadata.find(identity.tenantId(), identity.actorId(), consumer, businessId);
		if (run == null || !"SUCCEEDED".equals(run.status()) || !release.releaseId().equals(run.releaseId())
				|| !release.bindingRevisionId().equals(run.bindingRevisionId()))
			throw new IllegalArgumentException("MARKET_RESULT_CONTEXT_INCOMPATIBLE");
		var prior = new Identity(identity.tenantId(), identity.actorId(), identity.subjectId(),
				"market-skill:" + businessId);
		String output = results.read(prior, consumer, businessId, run.skillRunId(),
				"market-skill:" + businessId + ":" + run.skillRunId());
		var verified = requests.load(prior, consumer, businessId);
		try {
			String question = json.readTree(verified.getInputSnapshot()).path("query").asText("");
			String answer = json.readTree(output).path("answer").asText("");
			if (question.isBlank() || answer.isBlank() || question.length() + answer.length() > 24000)
				throw new IllegalArgumentException("MARKET_RESULT_CONTEXT_TOO_LARGE_OR_EMPTY");
			return new Context(question, answer, run.skillRunId(), run.releaseId());
		}
		catch (java.io.IOException failure) {
			throw new IllegalArgumentException("OUTPUT_INVALID");
		}
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

}
