package com.deepdimen.matrix.discover.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiAgentSkillServiceWorkflowContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void extractsVariablesFromCurrentDifyUserInputFormShape() throws Exception {
		Set<String> variables = AiAgentSkillServiceImpl.extractInputVariables(objectMapper.readTree("""
				{
				  "user_input_form": [
				    {"variable": "matrix_query", "type": "text-input"},
				    {"variable": "matrix_context_json", "type": "paragraph"}
				  ]
				}
				"""));

		assertEquals(Set.of("matrix_query", "matrix_context_json"), variables);
	}

	@Test
	void keepsCompatibilityWithNestedExportShape() throws Exception {
		Set<String> variables = AiAgentSkillServiceImpl.extractInputVariables(objectMapper.readTree("""
				{"user_input_form": [{"field": {"variable": "request_id"}}]}
				"""));

		assertTrue(variables.contains("request_id"));
	}

}
