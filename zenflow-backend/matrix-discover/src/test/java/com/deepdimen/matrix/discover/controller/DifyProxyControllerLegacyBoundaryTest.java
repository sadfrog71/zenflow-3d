package com.deepdimen.matrix.discover.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.dify.DocumentTextExtractor;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.DifyChatRecordService;
import com.deepdimen.matrix.discover.service.SkillProcessor;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DifyProxyControllerLegacyBoundaryTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void parameterDiscoveryOmitsRetiredPromptBypassField() throws Exception {
		DifyServiceClient difyServiceClient = mock(DifyServiceClient.class);
		AiAgentService aiAgentService = mock(AiAgentService.class);
		AiAgentPermissionService permissionService = mock(AiAgentPermissionService.class);
		AiAgentEntity agent = new AiAgentEntity();
		agent.setAgentType("dify");
		agent.setStatus("published");
		agent.setDifyApiKey("test-api-key");
		when(aiAgentService.getById(1L)).thenReturn(agent);
		when(permissionService.getPermission(1L)).thenReturn(null);
		when(difyServiceClient.getParameters("test-api-key")).thenReturn(Mono.just(objectMapper.readTree("""
				{
				  "opening_statement": "ready",
				  "user_input_form": [
				    {"text-input": {"variable": "matrix_query", "label": "Question"}},
				    {"paragraph": {"variable": "extended_system_prompt", "label": "Prompt"}}
				  ]
				}
				""")));
		DifyProxyController controller = new DifyProxyController(difyServiceClient, aiAgentService, permissionService,
				mock(DifyChatRecordService.class), mock(DocumentTextExtractor.class), objectMapper,
				new SkillProcessor(), mock(TenantAgentRouteService.class));

		R<JsonNode> response = controller.getAgentParameters(1L);

		JsonNode parameters = response.getData();
		assertEquals("ready", parameters.path("opening_statement").asText());
		assertEquals(1, parameters.path("user_input_form").size());
		assertEquals("matrix_query",
				parameters.path("user_input_form").get(0).path("text-input").path("variable").asText());
		assertFalse(parameters.toString().contains("extended_system_prompt"));
	}

}
