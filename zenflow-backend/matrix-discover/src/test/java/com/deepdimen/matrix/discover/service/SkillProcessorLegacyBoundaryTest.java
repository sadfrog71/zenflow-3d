package com.deepdimen.matrix.discover.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SkillProcessorLegacyBoundaryTest {

	@Test
	void legacyChatExplicitlyIgnoresEnabledSkillsWithoutExecutingPromptInjection() {
		ObjectMapper objectMapper = new ObjectMapper();
		SkillProcessor processor = new SkillProcessor();
		ObjectNode body = objectMapper.createObjectNode();
		body.put("query", "original");
		body.putArray("enabled_skills").add("legacy-prompt");
		body.putObject("inputs").put("extended_system_prompt", "caller supplied prompt");

		processor.apply(body);

		assertFalse(body.has("enabled_skills"));
		assertFalse(body.path("inputs").has("extended_system_prompt"));
		assertEquals("original", body.path("query").asText());
	}

}
