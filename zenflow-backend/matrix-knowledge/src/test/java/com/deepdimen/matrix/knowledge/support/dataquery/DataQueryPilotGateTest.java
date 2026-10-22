package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DataQueryPilotGateTest {

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	private void login() {
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(2L);
		when(user.getTenantId()).thenReturn(1L);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "USER"));
	}

	private DataQueryPilotGate gate() {
		return new DataQueryPilotGate("10",
				new StaticListableBeanFactory().getBeanProvider(DataQueryQuestionService.class));
	}

	@Test
	void selectedPilotRemainsFencedWhenControlledServiceIsDisabled() {
		login();
		var gate = gate();
		var message = new ChatMessageDTO();
		message.setMessageKey(77L);
		var ext = new ChatMessageDTO.ExtDetails();
		ext.setDataId(10L);
		message.setExtDetails(ext);
		var data = mock(com.deepdimen.matrix.knowledge.service.AiDataService.class);
		var model = mock(com.deepdimen.matrix.knowledge.support.provider.ModelProvider.class);
		var sql = mock(com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling.class);
		var rule = new com.deepdimen.matrix.knowledge.support.rule.DatabaseChatRule(sql, null, null, data, gate, model);
		assertThat(rule.process(message).blockFirst().getDataQuery().get("code"))
			.isEqualTo("CONTROLLED_SERVICE_UNAVAILABLE");
		verifyZeroInteractions(data, model, sql);
		assertThat(gate.isControlled(11L)).isFalse();
	}

	@Test
	void legacyFunctionCheckAndHandleCannotExecutePilotSql() throws Exception {
		var data = mock(com.deepdimen.matrix.knowledge.service.AiDataService.class);
		var sources = mock(com.deepdimen.matrix.knowledge.service.AiDatasourceService.class);
		var function = new com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling(null, null, data,
				gate(), sources);
		var ext = new ChatMessageDTO.ExtDetails();
		ext.setDataId(10L);
		// SqlRequest is package-private; invoke both concrete methods without
		// constructing SQL.
		for (var method : function.getClass().getDeclaredMethods()) {
			if (!method.isBridge() && (method.getName().equals("checkParams") || method.getName().equals("handle"))) {
				var result = (com.deepdimen.matrix.common.core.util.R<?>) method.invoke(function, null, null, ext);
				assertThat(result.getMsg()).isEqualTo("CONTROLLED_QUERY_REQUIRED");
			}
		}
		verifyZeroInteractions(data, sources);
	}

	@Test
	void anonymousExternalSqlIsFencedForPilotBeforeDatasourceLookup() {
		var data = mock(com.deepdimen.matrix.knowledge.mapper.AiDataMapper.class);
		var sources = mock(com.deepdimen.matrix.knowledge.service.AiDatasourceService.class);
		var controller = new com.deepdimen.matrix.knowledge.controller.DifyDataQueryController(data, null, null,
				sources, mock(com.deepdimen.matrix.common.security.component.ApiTokenValidator.class), gate());
		var response = controller.query(Map.of("dataset_id", 10, "sql", "SELECT * FROM device_task_record"),
				new org.springframework.mock.web.MockHttpServletRequest());
		assertThat(response.getStatusCode().value()).isEqualTo(403);
		verifyZeroInteractions(data, sources);
	}

	@Test
	void managementRejectsClientIdentityApprovalAndEvidenceFields() throws Exception {
		var source = mock(javax.sql.DataSource.class);
		var service = new DataQueryCorrectionService(source, null, new com.fasterxml.jackson.databind.ObjectMapper(),
				java.time.Clock.systemUTC());
		var mvc = MockMvcBuilders.standaloneSetup(new DataQueryCorrectionController(service)).build();
		for (String field : new String[] { "tenantId", "approvedBy", "passed", "regressionDigest" }) {
			mvc.perform(post("/data-query/manage/drafts/r1/approve").contentType("application/json")
				.content("{\"revision\":1,\"" + field + "\":\"forged\"}")).andExpect(status().isBadRequest());
		}
		mvc.perform(post("/data-query/manage/drafts/r1/approve").contentType("application/json")
			.content("{\"revision\":1}")).andExpect(status().isUnauthorized());
		verifyZeroInteractions(source);
	}

	@Test
	void sharedQuestionHttpRequiresEndUserAndRejectsInjectedSqlIdentity() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(new DataQueryQuestionController(null)).build();
		mvc.perform(post("/data-query/question").contentType("application/json")
			.content("{\"dataId\":\"10\",\"question\":\"x\"}")).andExpect(status().isUnauthorized());
		login();
		for (String field : new String[] { "tenantId", "sql", "ruleVersion", "messageId" }) {
			mvc.perform(post("/data-query/question").contentType("application/json")
				.content("{\"dataId\":\"10\",\"question\":\"x\",\"" + field + "\":\"forged\"}"))
				.andExpect(status().isBadRequest());
		}
	}

}
