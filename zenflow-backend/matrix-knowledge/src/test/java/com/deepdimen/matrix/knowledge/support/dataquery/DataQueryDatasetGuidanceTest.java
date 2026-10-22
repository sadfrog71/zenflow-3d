package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No controlled-query table, binding, business rule or source database is installed. */
class DataQueryDatasetGuidanceTest {

	JdbcTemplate jdbc;

	DataQueryDatasetGuidance service;

	ObjectMapper json = new ObjectMapper();

	@BeforeEach
	void setup() {
		var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
				"sa", "");
		jdbc = new JdbcTemplate(source);
		service = new DataQueryDatasetGuidance(source, json);
		jdbc.execute(
				"CREATE TABLE sys_user(user_id BIGINT,tenant_id BIGINT,username VARCHAR(64),lock_flag CHAR(1),del_flag CHAR(1))");
		jdbc.execute("CREATE TABLE sys_tenant_user(tenant_id BIGINT,user_id BIGINT)");
		jdbc.execute("CREATE TABLE sys_role(role_id BIGINT,tenant_id BIGINT,del_flag CHAR(1))");
		jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT,tenant_id BIGINT)");
		jdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
		jdbc.execute("CREATE TABLE sys_menu(menu_id BIGINT,tenant_id BIGINT,permission VARCHAR(80),del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE ai_data(data_id BIGINT,tenant_id BIGINT,ds_name VARCHAR(64),table_name VARCHAR(1000),del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE ai_data_table(table_id BIGINT,tenant_id BIGINT,ds_name VARCHAR(64),table_name VARCHAR(64),table_comment VARCHAR(80),virtual_comment VARCHAR(200))");
		jdbc.execute(
				"CREATE TABLE ai_data_field(table_id BIGINT,tenant_id BIGINT,ds_name VARCHAR(64),field_name VARCHAR(64),field_type VARCHAR(64),field_comment VARCHAR(80),virtual_comment VARCHAR(200))");
		jdbc.execute(
				"CREATE TABLE ai_chat_record(record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),dataset_id BIGINT,question_text TEXT,answer_text TEXT,ext_details TEXT,del_flag CHAR(1))");
		jdbc.update("INSERT INTO sys_user VALUES(2,1,'reader','0','0'),(3,2,'reader','0','0')");
		jdbc.update("INSERT INTO sys_tenant_user VALUES(1,2),(2,3)");
		jdbc.update("INSERT INTO sys_role VALUES(10,1,'0')");
		jdbc.update("INSERT INTO sys_user_role VALUES(2,10,1)");
		jdbc.update("INSERT INTO sys_role_menu VALUES(10,20)");
		jdbc.update("INSERT INTO sys_menu VALUES(20,1,'knowledge_aiData_view','0')");
		jdbc.update(
				"INSERT INTO ai_data VALUES(100,1,'standards','sys_standard','0'),(101,1,'devices','dev_device','0'),(102,2,'other','private_table','0')");
		jdbc.update(
				"INSERT INTO ai_data_table VALUES(1,1,'standards','sys_standard','标准配置','standard only'),(2,1,'devices','dev_device','设备台账','device only'),(3,2,'other','private_table','跨租户秘密','private')");
		jdbc.update(
				"INSERT INTO ai_data_field VALUES(1,1,'standards','id','varchar','主键',''),(2,1,'devices','id','varchar','主键',''),(3,2,'other','id','varchar','主键','')");
		login(1L, 2L, "reader");
	}

	private Optional<com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO> respond(ChatMessageDTO message) {
		return service.respond(message, (question, inventory, recheck) -> "合成模型分析（非真实统计）：" + inventory.schema());
	}

	private void login(long tenant, long userId, String username) {
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(userId);
		when(user.getTenantId()).thenReturn(tenant);
		when(user.getUsername()).thenReturn(username);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "USER"));
	}

	private ChatMessageDTO message(long id, long dataset, String question) {
		jdbc.update("INSERT INTO ai_chat_record VALUES(?,1,'reader',-2,?,NULL,?,'0')", id, question,
				"{\"dataId\":\"" + dataset + "\",\"funcName\":\"preserve-me\"}");
		var message = new ChatMessageDTO();
		message.setMessageKey(id);
		message.setContent("do not trust client content");
		var ext = new ChatMessageDTO.ExtDetails();
		ext.setDataId(dataset);
		message.setExtDetails(ext);
		return message;
	}

	private AiChatRecordEntity record(long id) {
		var row = jdbc.queryForMap("SELECT * FROM ai_chat_record WHERE record_id=?", id);
		var record = new AiChatRecordEntity();
		record.setRecordId(id);
		record.setTenantId(row.get("tenant_id").toString());
		record.setUsername((String) row.get("username"));
		record.setDatasetId(-2L);
		record.setQuestionText((String) row.get("question_text"));
		record.setAnswerText((String) row.get("answer_text"));
		record.setExtDetails((String) row.get("ext_details"));
		return record;
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void anySelectedDatasetWorksWithoutPilotSetupAndDoesNotReuseOtherTables() throws Exception {
		var standard = respond(message(1, 100, "你能查哪些数据")).orElseThrow();
		assertThat(standard.getMessage()).contains("标准配置").doesNotContain("设备台账", "跨租户秘密");
		assertThat(standard.getDataQuery()).isNull();
		assertThat(standard.getMetadataGuidance()).containsEntry("dataId", "100")
			.containsEntry("sqlExecuted", false)
			.doesNotContainKey("queryId");
		assertThat(json.readTree(record(1).getExtDetails()).path("funcName").asText()).isEqualTo("preserve-me");
		assertThat(service.restore(record(1))).isEqualTo(standard.getMetadataGuidance());
		var devices = respond(message(2, 101, "你能查询哪些类型的数据")).orElseThrow();
		assertThat(devices.getMessage()).contains("设备台账").doesNotContain("标准配置");
		assertThat(service.restore(record(2))).isEqualTo(devices.getMetadataGuidance());
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) LIKE 'ai_dataquery_%'",
				Integer.class))
			.isZero();
	}

	@Test
	void legacyDatabaseChatUsesSelectedModelWithAuthorizedMetadataButNeverSql() {
		var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		beans.addBean("guidance", service);
		var gate = new DataQueryPilotGate("", beans.getBeanProvider(DataQueryQuestionService.class));
		org.springframework.test.util.ReflectionTestUtils.setField(gate, "datasetGuidance",
				beans.getBeanProvider(DataQueryDatasetGuidance.class));
		var data = mock(com.deepdimen.matrix.knowledge.service.AiDataService.class);
		var model = mock(com.deepdimen.matrix.knowledge.support.provider.ModelProvider.class);
		var chat = mock(dev.langchain4j.model.chat.ChatModel.class);
		when(model.getAiJSONAssistant(any()))
			.thenReturn(org.apache.commons.lang3.tuple.Triple.of(chat, null, "openai"));
		when(chat.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
			.thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
				.aiMessage(dev.langchain4j.data.message.AiMessage
					.from("{\"responseType\":\"DATASET_GUIDANCE\",\"answer\":\"标准配置可按主键检索，这是合成模型输出\"}"))
				.build());
		var sql = mock(com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling.class);
		var rule = new com.deepdimen.matrix.knowledge.support.rule.DatabaseChatRule(sql, null, null, data, gate, model);
		var request = message(1, 100, "你能查哪些数据");
		request.setContent("你能查哪些数据");
		assertThat(gate.isMetadataOverviewRequest(request)).isTrue();
		var answer = rule.process(request).blockFirst();
		assertThat(answer.getMessage()).contains("标准配置");
		assertThat(answer.getMetadataGuidance()).containsEntry("sqlExecuted", false);
		assertThat(answer.getDataQuery()).isNull();
		verify(data).queryTerminology(100L);
		verify(model).getAiJSONAssistant(any());
		var prompt = org.mockito.ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
		verify(chat).chat(prompt.capture());
		assertThat(prompt.getValue().messages().toString()).contains("标准配置", "standard only", "主键")
			.doesNotContain("设备台账", "跨租户秘密");
		verifyZeroInteractions(sql);
		request.setContent("查询标准总数");
		assertThat(gate.tryMetadataOverview(request, (question, inventory, recheck) -> {
			throw new AssertionError();
		})).isEmpty();
	}

	@Test
	void detailStatisticsAndCompoundRequestsAreNotConvertedToIntroductions() {
		assertThat(respond(message(1, 100, "有哪些标准项"))).isEmpty();
		assertThat(respond(message(2, 100, "你能查哪些数据，顺便查总数"))).isEmpty();
		assertThat(record(1).getAnswerText()).isNull();
	}

	@Test
	void changingClientDatasetOrImpersonatingChatOwnerIsRejected() {
		var request = message(1, 100, "你能查哪些数据");
		request.getExtDetails().setDataId(101L);
		assertThatThrownBy(() -> respond(request)).isInstanceOf(DataQueryException.class)
			.hasMessage("DATASET_NOT_AUTHORIZED");
		login(1L, 2L, "someone-else");
		assertThatThrownBy(() -> respond(request)).isInstanceOf(DataQueryException.class)
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		login(2L, 3L, "reader");
		assertThatThrownBy(() -> respond(request)).isInstanceOf(DataQueryException.class)
			.hasMessage("CHAT_RECORD_NOT_OWNED");
	}

	@Test
	void metadataReadPermissionIsNotInferredFromLoginAndRevocationInvalidatesReceipt() {
		respond(message(1, 100, "你这个数据集能查什么"));
		jdbc.update("DELETE FROM sys_role_menu");
		assertThat(service.restore(record(1))).isNull();
		assertThatThrownBy(() -> respond(message(2, 100, "你能查哪些数据"))).isInstanceOf(DataQueryException.class)
			.hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void deletedOrCrossTenantDatasetAndMissingMetadataAreNotDisclosed() {
		assertThatThrownBy(() -> respond(message(1, 102, "你能查哪些数据"))).isInstanceOf(DataQueryException.class);
		jdbc.update("UPDATE ai_data SET del_flag='1' WHERE data_id=100");
		assertThatThrownBy(() -> respond(message(2, 100, "你能查哪些数据"))).isInstanceOf(DataQueryException.class);
		jdbc.update("DELETE FROM ai_data_field WHERE table_id=2");
		assertThatThrownBy(() -> respond(message(3, 101, "你能查哪些数据"))).isInstanceOf(DataQueryException.class);
	}

	@Test
	void staleOrForgedHistoryMarkerCannotTurnArbitraryAnswerIntoMetadataEvidence() {
		respond(message(1, 100, "你能查哪些数据"));
		var record = record(1);
		record.setAnswerText("已查询得到999条");
		assertThat(service.restore(record)).isNull();
		record = record(1);
		record.setRecordId(99L);
		assertThat(service.restore(record)).isNull();
		jdbc.update("UPDATE ai_data_table SET table_comment='changed' WHERE table_id=1");
		assertThat(service.restore(record(1))).isNull();
	}

	@Test
	void rereadsSelectedFieldsAndNotesAndRejectsChangesDuringModelGeneration() {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var generator = (DataQueryDatasetGuidance.Generator) (question, inventory, recheck) -> {
			calls.incrementAndGet();
			return inventory.schema();
		};
		var first = service.respond(message(10, 100, "能不能生成一些问题我看看"), generator).orElseThrow();
		assertThat(first.getMetadataGuidance()).containsEntry("source", "AI_METADATA_ANALYSIS");
		jdbc.update("UPDATE ai_data_field SET virtual_comment='新增的字段业务解释' WHERE table_id=1");
		var second = service.respond(message(11, 100, "帮我推荐5个适合这个数据集的业务问题"), generator).orElseThrow();
		assertThat(first.getMessage()).doesNotContain("新增的字段业务解释");
		assertThat(second.getMessage()).contains("新增的字段业务解释");
		assertThat(calls.get()).isEqualTo(2);
		assertThat(service.restore(record(10))).isNull();
		assertThat(service.restore(record(11))).isNotNull();
		assertThatThrownBy(() -> service.respond(message(12, 100, "你能查哪些数据"), (q, inventory, recheck) -> {
			jdbc.update("DELETE FROM sys_role_menu");
			return "权限已变化";
		})).isInstanceOf(DataQueryException.class);
		assertThat(record(12).getAnswerText()).isNull();
	}

	@Test
	void newlySelectedTablesAreIncludedWithoutEditingAnyAnswerTemplate() {
		var first = respond(message(30, 100, "你能查哪些数据")).orElseThrow();
		jdbc.update("INSERT INTO ai_data_table VALUES(4,1,'standards','new_category','新分类表','分类备注')");
		jdbc.update("INSERT INTO ai_data_field VALUES(4,1,'standards','category_name','varchar','分类名称','展示用名称')");
		jdbc.update("UPDATE ai_data SET table_name='sys_standard,new_category' WHERE data_id=100");
		var next = respond(message(31, 100, "你能查哪些数据")).orElseThrow();
		assertThat(first.getMessage()).doesNotContain("new_category");
		assertThat(next.getMessage()).contains("new_category", "分类备注", "category_name", "展示用名称");
		assertThat(service.restore(record(30))).isNull();
		assertThat(service.restore(record(31))).isNotNull();
	}

	@Test
	void legacyTemplateHistoryStillRestoresWithoutCallingModel() throws Exception {
		message(20, 100, "你能查哪些数据");
		var inventory = DataQueryMetadataRepository.readInventory(jdbc, json,
				new ControlledQueryService.Identity("1", "2"), 100);
		String oldAnswer = DataQueryDatasetOverview.answer("你能查哪些数据", inventory).orElseThrow();
		var ext = json.createObjectNode().put("dataId", "100");
		ext.set("metadataGuidance",
				json.valueToTree(
						Map.of("version", "dataset-metadata-v1", "messageId", "20", "dataId", "100", "responseType",
								"DATASET_GUIDANCE", "source", "SELECTED_TABLE_METADATA", "sqlExecuted", false)));
		jdbc.update("UPDATE ai_chat_record SET answer_text=?,ext_details=? WHERE record_id=20", oldAnswer,
				ext.toString());
		assertThat(service.restore(record(20))).containsEntry("version", "dataset-metadata-v1");
	}

	@Test
	void nullAndTruncatedLegacyModelResponsesBecomeTypedErrorsNotNullPointers() {
		assertThatThrownBy(() -> DataQueryAiChatService.parseModelResponse(null)).isInstanceOf(DataQueryException.class)
			.hasMessage("AI_RESPONSE_INVALID");
		var response = dev.langchain4j.model.chat.response.ChatResponse.builder()
			.aiMessage(dev.langchain4j.data.message.AiMessage.from("{}"))
			.finishReason(dev.langchain4j.model.output.FinishReason.LENGTH)
			.build();
		assertThatThrownBy(() -> DataQueryAiChatService.parseModelResponse(response))
			.isInstanceOf(DataQueryException.class)
			.hasMessage("AI_RESPONSE_TRUNCATED");
	}

}
