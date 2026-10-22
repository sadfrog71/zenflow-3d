package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataQueryLegacyAnswerStoreTest {

	JdbcTemplate jdbc;

	DataQueryLegacyAnswerStore store;

	ChatMessageDTO message;

	@BeforeEach
	void setup() {
		var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa",
				"");
		jdbc = new JdbcTemplate(ds);
		store = new DataQueryLegacyAnswerStore(ds, new ObjectMapper());
		jdbc.execute(
				"CREATE TABLE ai_chat_record(record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),dataset_id BIGINT,question_text TEXT,answer_text TEXT,ext_details TEXT,conversation_id VARCHAR(100),del_flag CHAR(1))");
		jdbc.update(
				"INSERT INTO ai_chat_record VALUES(1,1,'reader',-2,'标准前5条',NULL,'{\"dataId\":100}','owned-conversation','0')");
		message = new ChatMessageDTO();
		message.setMessageKey(1L);
		message.setDatasetId(-2L);
		message.setConversationId("owned-conversation");
		message.setContent("标准前5条");
		message.getExtDetails().setDataId(100L);
		login(1, 2, "reader");
	}

	void login(long tenant, long id, String name) {
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(id);
		when(user.getTenantId()).thenReturn(tenant);
		when(user.getUsername()).thenReturn(name);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "USER"));
	}

	AiChatRecordEntity record() {
		var r = new AiChatRecordEntity();
		r.setRecordId(1L);
		r.setTenantId("1");
		r.setUsername("reader");
		r.setDatasetId(-2L);
		r.setQuestionText(message.getContent());
		r.setConversationId(message.getConversationId());
		r.setExtDetails("{\"dataId\":100}");
		return r;
	}

	String saved() {
		return jdbc.queryForObject("SELECT answer_text FROM ai_chat_record", String.class);
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void savesRenderedAnswerAndKeepsChartMetadata() {
		var run = store.begin(message);
		assertThat(store.state(record())).isEqualTo("RUNNING");
		jdbc.update("UPDATE ai_chat_record SET ext_details=?",
				"{\"dataId\":100,\"chartId\":\"chart-1\",\"metadataGuidance\":{\"source\":\"test\"}}");
		var answer = new AiMessageResultDTO("| 标准 |\n|---|\n| 合成标准 |");
		store.complete(run, answer);
		store.end(run);
		assertThat(saved()).isEqualTo(answer.getMessage());
		assertThat(jdbc.queryForObject("SELECT ext_details FROM ai_chat_record", String.class)).contains("chart-1",
				"metadataGuidance");
		assertThat(store.state(record())).isNull();
	}

	@Test
	void savesFailureTextWithoutPretendingSuccess() {
		var run = store.begin(message);
		store.complete(run, new AiMessageResultDTO("本次未执行数据查询：字段元数据未准备完成。"));
		assertThat(saved()).contains("未执行");
		store.end(run);
	}

	@Test
	void duplicateInFlightIsRejected() {
		var run = store.begin(message);
		assertThatThrownBy(() -> store.begin(message)).hasMessage("QUERY_ALREADY_RUNNING");
		store.end(run);
	}

	@Test
	void rejectsCrossTenantUserAndModifiedDataset() {
		var run = store.begin(message);
		login(2, 2, "reader");
		assertThat(store.state(record())).isNull();
		assertThatThrownBy(() -> store.complete(run, new AiMessageResultDTO("wrong")))
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		login(1, 3, "reader");
		assertThat(store.state(record())).isNull();
		assertThatThrownBy(() -> store.complete(run, new AiMessageResultDTO("wrong")))
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		login(1, 2, "reader");
		jdbc.update("UPDATE ai_chat_record SET ext_details='{\"dataId\":101}'");
		assertThatThrownBy(() -> store.complete(run, new AiMessageResultDTO("wrong")))
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		assertThat(saved()).isNull();
		store.end(run);
	}

	@Test
	void missingDeletedOrEmptyAnswersAreNotInvented() {
		assertThat(store.state(record())).isNull();
		var run = store.begin(message);
		assertThatThrownBy(() -> store.complete(run, new AiMessageResultDTO(" "))).hasMessage("AI_RESPONSE_INVALID");
		jdbc.update("UPDATE ai_chat_record SET del_flag='1'");
		assertThatThrownBy(() -> store.complete(run, new AiMessageResultDTO("wrong")))
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		store.end(run);
	}

	@Test
	void streamingRulePersistsFinalToolResultEvenAfterBrowserLeaves() throws Exception {
		var gate = new DataQueryPilotGate("", new org.springframework.beans.factory.support.StaticListableBeanFactory()
			.getBeanProvider(DataQueryQuestionService.class));
		var models = mock(com.deepdimen.matrix.knowledge.support.provider.ModelProvider.class);
		var model = mock(dev.langchain4j.model.chat.ChatModel.class);
		when(models.getAiJSONAssistant(any()))
			.thenReturn(org.apache.commons.lang3.tuple.Triple.of(model, null, "openai"));
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenAnswer(call -> {
			entered.countDown();
			if (!release.await(5, TimeUnit.SECONDS))
				throw new IllegalStateException("test timeout");
			return dev.langchain4j.model.chat.response.ChatResponse.builder()
				.aiMessage(dev.langchain4j.data.message.AiMessage
					.from("{\"sql\":\"SELECT id FROM sys_standard LIMIT 5\",\"chartOutput\":false}"))
				.build();
		});
		var data = mock(com.deepdimen.matrix.knowledge.service.AiDataService.class);
		when(data.queryDataSchema(100L)).thenReturn("synthetic schema");
		when(data.queryTerminology(100L)).thenReturn("");
		var memories = mock(com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider.class);
		when(memories.get(any())).thenReturn(dev.langchain4j.memory.chat.MessageWindowChatMemory.withMaxMessages(10));
		var mapper = mock(com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper.class);
		var tool = mock(com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling.class);
		when(tool.functionName()).thenReturn("Chat2SqlFunctionCalling");
		doReturn(com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling.ConversationRequest.class)
			.when(tool)
			.getGenericType();
		when(tool.executeFromChat(any(), any(), any()))
			.thenReturn(com.deepdimen.matrix.common.core.util.R.ok("| 标准 |\n|---|\n| 合成标准 |"));
		var rule = new com.deepdimen.matrix.knowledge.support.rule.DatabaseChatRule(tool, memories, mapper, data, gate,
				models);
		var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		beans.addBean("store", store);
		org.springframework.test.util.ReflectionTestUtils.setField(rule, "legacyAnswers",
				beans.getBeanProvider(DataQueryLegacyAnswerStore.class));
		var subscription = rule.processStreaming(message).subscribe();
		try {
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(store.state(record())).isEqualTo("RUNNING");
			subscription.dispose();
			release.countDown();
			for (int n = 0; n < 100 && saved() == null; n++)
				Thread.sleep(30);
			assertThat(saved()).contains("合成标准").doesNotContain("SELECT", "sql");
			verify(tool, times(1)).executeFromChat(any(), any(), any());
		}
		finally {
			release.countDown();
			subscription.dispose();
		}
	}

}
