package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.mapper.MarketSkillRequestMapper;
import com.deepdimen.matrix.skill.api.SkillResultStore;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketSkillResultStore implements SkillResultStore {

	private final MarketSkillRequestStore requests;

	private final MarketSkillRequestMapper rows;

	private final ObjectMapper json;

	public String consumerType() {
		return "MARKET_AGENT";
	}

	public boolean supportsConsumer(Consumer consumer) {
		return consumerType().equals(consumer.type()) && "CHAT".equals(consumer.slotCode());
	}

	public String authorizeInput(Identity identity, Consumer consumer, String businessRunId, String inputJson) {
		return requests.authorizeInput(identity, consumer, businessRunId, inputJson);
	}

	public String save(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String outputJson) {
		var row = requests.load(identity, consumer, businessRunId);
		Long run = positive(skillRunId);
		String output = output(outputJson).toString();
		rows.saveOnce(row.getTenantId(), row.getActorId(), row.getId(), row.getInputDigest(), run, output);
		var saved = requests.load(identity, consumer, businessRunId);
		if (!run.equals(saved.getSkillRunId()) || !output(output).equals(output(saved.getSkillOutputJson())))
			throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
		return reference(businessRunId, skillRunId);
	}

	public String read(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String resultRef) {
		var row = requests.load(identity, consumer, businessRunId);
		if (!positive(skillRunId).equals(row.getSkillRunId())
				|| !reference(businessRunId, skillRunId).equals(resultRef))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		return output(row.getSkillOutputJson()).toString();
	}

	private JsonNode output(String text) {
		if (text == null || text.length() > 65536)
			throw new IllegalArgumentException("OUTPUT_INVALID");
		try {
			var value = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(text);
			if (value == null || !value.isObject())
				throw new IllegalArgumentException("OUTPUT_INVALID");
			return value;
		}
		catch (java.io.IOException failure) {
			throw new IllegalArgumentException("OUTPUT_INVALID", failure);
		}
	}

	private static Long positive(String id) {
		try {
			long value = Long.parseLong(id);
			if (value > 0)
				return value;
		}
		catch (RuntimeException ignored) {
		}
		throw new IllegalArgumentException("INPUT_INVALID");
	}

	private static String reference(String business, String run) {
		return "market-skill:" + business + ":" + run;
	}

}
