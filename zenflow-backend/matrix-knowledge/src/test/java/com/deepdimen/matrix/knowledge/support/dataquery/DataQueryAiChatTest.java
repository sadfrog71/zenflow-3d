package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling;
import com.deepdimen.matrix.knowledge.support.provider.*;
import com.deepdimen.matrix.knowledge.support.rule.DatabaseChatRule;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real platform repository + JDBC SELECT + original chat/function path; only AI/provider
 * and chart scheduling are synthetic.
 */
class DataQueryAiChatTest {

	final Identity owner = new Identity("1", "2");

	final Clock clock = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);

	final ObjectMapper json = new ObjectMapper();

	DriverManagerDataSource source;

	JdbcTemplate jdbc;

	DataQueryMetadataRepository metadata;

	DataQueryAiChatService service;

	AtomicInteger executions;

	DataQueryPilotGate gate;

	ChatMemoryAdvisorProvider memories;

	AiChartGenerateService charts;

	AiDataService data;

	AiDatasourceService legacy;

	ModelProvider provider;

	ChatModel model;

	DatabaseChatRule rule;

	final String url = "jdbc:mysql://synthetic.invalid:13306/top_device_biz";

	@BeforeEach
	void setup() throws Exception {
		source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa",
				"");
		try (var c = source.getConnection()) {
			ScriptUtils.executeSqlScript(c, new FileSystemResource("../docs/dataquery/platform-schema.sql"));
			ScriptUtils.executeSqlScript(c, new FileSystemResource("../docs/dataquery/access-schema.sql"));
		}
		jdbc = new JdbcTemplate(source);
		jdbc.execute(
				"CREATE TABLE ai_datasource(id BIGINT,tenant_id BIGINT,name VARCHAR(64),url VARCHAR(1000),username VARCHAR(64),password VARCHAR(64),ds_type VARCHAR(64),del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE ai_data(data_id BIGINT,tenant_id BIGINT,ds_name VARCHAR(64),del_flag CHAR(1),table_name VARCHAR(1000))");
		jdbc.execute(
				"CREATE TABLE ai_data_table(table_id BIGINT,tenant_id BIGINT,ds_name VARCHAR(64),table_name VARCHAR(64),table_comment VARCHAR(64),virtual_comment VARCHAR(64))");
		jdbc.execute(
				"CREATE TABLE ai_data_field(table_id BIGINT,tenant_id BIGINT,ds_name VARCHAR(64),field_name VARCHAR(64),field_type VARCHAR(64),field_comment VARCHAR(64),virtual_comment VARCHAR(64))");
		jdbc.execute(
				"CREATE TABLE ai_chat_record(record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),question_text TEXT,answer_text TEXT,ext_details TEXT,del_flag CHAR(1),conversation_id VARCHAR(255) DEFAULT 'shared-conversation')");
		jdbc.execute(
				"CREATE TABLE sys_user(user_id BIGINT,tenant_id BIGINT,username VARCHAR(64),lock_flag CHAR(1),del_flag CHAR(1))");
		jdbc.execute("CREATE TABLE sys_tenant_user(tenant_id BIGINT,user_id BIGINT)");
		jdbc.update("INSERT INTO sys_user VALUES(2,1,'reader','0','0'),(3,1,'editor','0','0')");
		jdbc.update("INSERT INTO sys_tenant_user VALUES(1,2),(1,3)");
		MatrixAccessFixture.organization(jdbc);
		jdbc.update("INSERT INTO ai_datasource VALUES(20,1,'synthetic',?,'reader','synthetic-only','mysql','0')", url);
		jdbc.update("INSERT INTO ai_data VALUES(10,1,'synthetic','0','dev_patrol_record,dev_device')");
		jdbc.update("INSERT INTO ai_dataquery_binding VALUES(1,'top.patrol',10,20,'binding',?,1,9,?)",
				DataQueryMetadataRepository.connectionFingerprint(10, 20, url, "reader", "mysql"),
				LocalDateTime.of(2026, 8, 1, 0, 0));
		jdbc.update("INSERT INTO ai_dataquery_access VALUES(1,'top.patrol',10,'access',?, ?,9,?)",
				"[{\"type\":\"user\",\"targetId\":\"2\"}]", LocalDateTime.of(2026, 10, 1, 0, 0),
				LocalDateTime.of(2026, 8, 1, 0, 0));
		for (int i = 0; i < 2; i++) {
			jdbc.update("INSERT INTO ai_data_table VALUES(?,1,'synthetic',?,'physical','preserve-table-comment')",
					100 + i, i == 0 ? "dev_patrol_record" : "dev_device");
			jdbc.update(
					"INSERT INTO ai_data_field VALUES(?,1,'synthetic','company_id','varchar','physical','preserve-field-comment')",
					100 + i);
		}
		jdbc.execute("CREATE TABLE dev_device(company_id VARCHAR(64))");
		jdbc.execute("CREATE TABLE dev_patrol_record(company_id VARCHAR(64))");
		jdbc.update("INSERT INTO dev_device VALUES('A'),('A'),('B')");
		jdbc.update("INSERT INTO dev_patrol_record VALUES('A'),('B')");
		metadata = new DataQueryMetadataRepository(source, json, clock);
		executions = new AtomicInteger();
		service = new DataQueryAiChatService(metadata, (identity, scope, sql) -> {
			executions.incrementAndGet();
			try (var c = source.getConnection()) {
				return DataQuerySqlExecutor.read(c, sql);
			}
			catch (java.sql.SQLException e) {
				throw DataQuerySqlExecutor.failure(e);
			}
		});
		var beans = new StaticListableBeanFactory();
		beans.addBean("ai", service);
		gate = new DataQueryPilotGate("10", beans.getBeanProvider(DataQueryQuestionService.class));
		ReflectionTestUtils.setField(gate, "aiChats", beans.getBeanProvider(DataQueryAiChatService.class));
		memories = mock(ChatMemoryAdvisorProvider.class);
		when(memories.get(any())).thenReturn(MessageWindowChatMemory.withMaxMessages(12));
		charts = mock(AiChartGenerateService.class);
		data = mock(AiDataService.class);
		legacy = mock(AiDatasourceService.class);
		when(data.queryTerminology(10L)).thenReturn("合成术语备注");
		provider = mock(ModelProvider.class);
		model = mock(ChatModel.class);
		when(provider.getAiJSONAssistant("synthetic-model")).thenReturn(Triple.of(model, null,
				com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums.OPENAI.getFormat()));
		var function = new Chat2SqlFunctionCalling(memories, charts, data, gate, legacy);
		rule = new DatabaseChatRule(function, memories,
				mock(com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper.class), data, gate, provider);
		login(1, 2, "reader");
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
		ChatMessageContextHolder.clear();
	}

	void login(long tenant, long id, String username) {
		var user = mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		when(user.getId()).thenReturn(id);
		when(user.getTenantId()).thenReturn(tenant);
		when(user.getUsername()).thenReturn(username);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "USER"));
	}

	ChatMessageDTO message(long id, String question) {
		jdbc.update(
				"INSERT INTO ai_chat_record(record_id,tenant_id,username,question_text,answer_text,ext_details,del_flag) VALUES(?,1,'reader',?,NULL,'{\"dataId\":\"10\"}','0')",
				id, question);
		var message = new ChatMessageDTO();
		message.setMessageKey(id);
		message.setContent("do not trust client text");
		message.setConversationId("shared-conversation");
		message.setModelName("synthetic-model");
		var ext = new ChatMessageDTO.ExtDetails();
		ext.setDataId(10L);
		message.setExtDetails(ext);
		return message;
	}

	void modelSql(String sql, int chart) throws Exception {
		String args = json.writeValueAsString(
				Map.of("sql", sql, "chartOutput", chart > 0, "chartType", chart, "messageKey", "999999999"));
		when(model.chat(any(ChatRequest.class)))
			.thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(args)).build());
	}

	private String sqlReply(String sql) throws Exception {
		return json
			.writeValueAsString(Map.of("sql", sql, "chartOutput", true, "chartType", 3, "messageKey", "untrusted"));
	}

	private ChatResponse reply(String text) {
		return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
	}

	private DataQuerySecurity.ManagementActor publisher(String tenant) {
		return new DataQuerySecurity.ManagementActor(new Identity(tenant, "3"),
				Set.of("knowledge_dataquery_publish", "knowledge_dataquery_human_review"));
	}

	private String brokenSql(String company) {
		return "SELECT COUNT(*) AS total FROM dev_device d WHERE dev_device.company_id='" + company + "'";
	}

	private void repairModel(String sql) throws Exception {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(sqlReply(sql)),
                reply("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\"}"));
    }

	@Test
	void snapshotAndManualSelectionSurviveRepositoryReopenWithoutSourceExecution() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id ORDER BY company_id", 0);
		var answer = rule.process(message(9902, "按公司统计设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var history = metadata.findOwnedChatReferences(owner, "reader", List.of(9902L)).get(9902L).dataQuery();
		assertThat(history).containsKey("visualization").doesNotContainKeys("columns", "rows", "snapshot");
		var visuals = metadata.visualizations();
		var original = visuals.read(owner, id);
		assertThat(original.status()).isEqualTo("AVAILABLE");
		assertThat(original.snapshot().columns()).isEqualTo(answer.getDataQuery().get("columns"));
		assertThat(original.snapshot().rows()).isEqualTo(answer.getDataQuery().get("rows"));
		var pie = new DataQueryVisualizations.Selection("pie", 0, 1);
		var update = new DataQueryVisualizations.Update(0, pie);
		assertThat(visuals.update(owner, id, update).revision()).isEqualTo(1);
		assertThat(visuals.update(owner, id, update).revision()).isEqualTo(1);
		var reopened = new DataQueryMetadataRepository(source, json, clock).visualizations().read(owner, id);
		assertThat(reopened.selection()).isEqualTo(pie);
		assertThat(reopened.snapshot()).isEqualTo(original.snapshot());
		assertThatThrownBy(() -> visuals.update(owner, id,
				new DataQueryVisualizations.Update(0, new DataQueryVisualizations.Selection("bar", 0, 1))))
			.hasMessage("VISUALIZATION_CHANGED");
		assertThatThrownBy(() -> visuals.update(owner, id,
				new DataQueryVisualizations.Update(1, new DataQueryVisualizations.Selection("bar", 0, 99))))
			.hasMessage("INVALID_REQUEST");
		visuals.update(owner, id, new DataQueryVisualizations.Update(1, null));
		assertThat(visuals.read(owner, id).selection()).isNull();
		assertThat(executions.get()).isEqualTo(1);
		verify(model, times(1)).chat(any(ChatRequest.class));
	}

	@Test
	void snapshotUsesActiveTenantMembershipRatherThanUserHomeTenant() throws Exception {
		jdbc.update("UPDATE sys_user SET tenant_id=99 WHERE user_id=2");
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var answer = rule.process(message(9908, "按公司统计设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var visuals = metadata.visualizations();
		assertThat(visuals.read(owner, id).status()).isEqualTo("AVAILABLE");
		assertThat(visuals
			.update(owner, id,
					new DataQueryVisualizations.Update(0, new DataQueryVisualizations.Selection("pie", 0, 1)))
			.revision()).isEqualTo(1);
		jdbc.update("DELETE FROM sys_tenant_user WHERE tenant_id=1 AND user_id=2");
		assertThatThrownBy(() -> visuals.read(owner, id)).hasMessage("QUERY_NOT_FOUND");
		assertThatThrownBy(() -> visuals.update(owner, id, new DataQueryVisualizations.Update(1, null)))
			.hasMessage("QUERY_NOT_FOUND");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void snapshotStillRejectsChangedMessageOwnerDeletionAndLockedUsers() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var answer = rule.process(message(9909, "按公司统计设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var visuals = metadata.visualizations();
		jdbc.update("UPDATE ai_chat_record SET username='editor' WHERE record_id=9909");
		assertThatThrownBy(() -> visuals.read(owner, id)).hasMessage("QUERY_NOT_FOUND");
		jdbc.update("UPDATE ai_chat_record SET username='reader',del_flag='1' WHERE record_id=9909");
		assertThatThrownBy(() -> visuals.read(owner, id)).hasMessage("QUERY_NOT_FOUND");
		jdbc.update("UPDATE ai_chat_record SET del_flag='0' WHERE record_id=9909");
		jdbc.update("UPDATE sys_user SET lock_flag='1' WHERE user_id=2");
		assertThatThrownBy(() -> visuals.read(owner, id)).isInstanceOf(DataQueryException.class);
		assertThatThrownBy(() -> visuals.update(owner, id, new DataQueryVisualizations.Update(0, null)))
			.isInstanceOf(DataQueryException.class);
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void snapshotReadAndUpdateEnforceOwnershipCurrentAccessAndChatIdentity() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var answer = rule.process(message(9903, "按公司统计设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var visuals = metadata.visualizations();
		for (var intruder : List.of(new Identity("2", "2"), new Identity("1", "3"))) {
			assertThatThrownBy(() -> visuals.read(intruder, id)).hasMessage("QUERY_NOT_FOUND");
			assertThatThrownBy(() -> visuals.update(intruder, id, new DataQueryVisualizations.Update(0, null)))
				.hasMessage("QUERY_NOT_FOUND");
		}
		jdbc.update("UPDATE ai_chat_record SET question_text='changed' WHERE record_id=9903");
		assertThatThrownBy(() -> visuals.read(owner, id)).hasMessage("QUERY_NOT_FOUND");
		jdbc.update("UPDATE ai_chat_record SET question_text='按公司统计设备数' WHERE record_id=9903");
		jdbc.update("UPDATE ai_dataquery_access SET access_revision='revoked'");
		assertThatThrownBy(() -> visuals.read(owner, id)).isInstanceOf(DataQueryException.class);
		assertThatThrownBy(() -> visuals.update(owner, id, new DataQueryVisualizations.Update(0, null)))
			.isInstanceOf(DataQueryException.class);
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void absentOrCorruptSnapshotDoesNotTriggerAQueryOrFallbackToModelProse() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var answer = rule.process(message(9904, "按公司统计设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		jdbc.update("UPDATE ai_dataquery_visualization SET snapshot_json='{}' WHERE query_id=?", id);
		assertThatThrownBy(() -> metadata.visualizations().read(owner, id)).hasMessage("VISUALIZATION_UNAVAILABLE");
		jdbc.update("DELETE FROM ai_dataquery_visualization WHERE query_id=?", id);
		assertThat(metadata.visualizations().read(owner, id).status()).isEqualTo("NOT_RECORDED");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void unavailableSnapshotStorageDoesNotTurnARealQueryIntoFailure() throws Exception {
		jdbc.execute("DROP TABLE ai_dataquery_visualization");
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var answer = rule.process(message(9905, "按公司统计设备数")).blockFirst();
		assertThat(answer.getDataQuery()).containsEntry("status", "SUCCEEDED");
		assertThat(((Map<?, ?>) answer.getDataQuery().get("visualization")).get("status")).isEqualTo("UNAVAILABLE");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void snapshotPreservesEmptyCellsAndLargeDecimalStringsAndRefusesOversizeCopies() throws Exception {
		modelSql(
				"SELECT company_id,CAST('9007199254740993' AS VARCHAR) AS big_value,CAST(NULL AS VARCHAR) AS empty_value FROM dev_device ORDER BY company_id",
				0);
		var msg = message(9906, "列出设备公司和说明");
		var answer = rule.process(msg).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var original = metadata.visualizations().read(owner, id);
		assertThat(original.snapshot().rows().get(0)).contains("9007199254740993");
		assertThat(original.snapshot().rows().get(0).get(2)).isNull();
		var many = Map.<String, Object>of("columns", List.of("x"), "rows", Collections.nCopies(501, List.of("x")));
		assertThat(metadata.visualizations().capture(msg.getDataQueryContext(), many)).containsEntry("status",
				"TOO_LARGE");
		var huge = Map.<String, Object>of("columns", List.of("x"), "rows", List.of(List.of("字".repeat(400000))));
		assertThat(metadata.visualizations().capture(msg.getDataQueryContext(), huge)).containsEntry("status",
				"TOO_LARGE");
		assertThat(metadata.visualizations().read(owner, id).snapshot()).isEqualTo(original.snapshot());
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void visualizationHttpContractRequiresTrustedIdentityAndDoesNotCacheResults() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var answer = rule.process(message(9907, "按公司统计设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
			.standaloneSetup(new DataQueryExecutionController(metadata, json))
			.build();
		String url = "/data-query/queries/" + id + "/visualization";
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string("Cache-Control", "no-store"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
				.jsonPath("$.snapshot.rows.length()")
				.value(2));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
			.contentType("application/json")
			.content("{\"revision\":0,\"selection\":{\"view\":\"pie\",\"category\":0,\"metric\":1}}"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
			.andExpect(
					org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.revision").value(1));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
			.contentType("application/json")
			.content("{\"revision\":0,\"selection\":null}"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
			.contentType("application/json")
			.content("{\"selection\":null}"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
		SecurityContextHolder.clearContext();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void everySuccessfulResultAdvertisesASavedSnapshotEvenWithoutAnAiChart() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id ORDER BY company_id", 0);
		var answer = rule.process(message(9901, "按公司列出设备数，不要生成图表")).blockFirst();
		assertThat(answer.getDataQuery()).containsEntry("status", "SUCCEEDED").containsKey("visualization");
		assertThat(((Map<?, ?>) answer.getDataQuery().get("visualization")).get("status")).isEqualTo("AVAILABLE");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void failingSqlIsActuallyRepairedLearnedReusedWithNewDataAndDisabledWithoutRestrictingValidSql() throws Exception {
		String firstSql = brokenSql("A");
		try (var connection = source.getConnection()) {
			assertThatThrownBy(() -> DataQuerySqlExecutor.read(connection,
					new DataQuerySqlGuard().prepare(firstSql, Set.of("dev_device"))))
				.isInstanceOf(java.sql.SQLException.class);
		}
		repairModel(firstSql);
		var first = rule.process(message(601, "统计A设备数量")).blockFirst();
		assertThat(first.getDataQuery()).containsEntry("status", "SUCCEEDED");
		var firstRepair = (Map<?, ?>) first.getDataQuery().get("repair");
		assertThat(firstRepair.get("status")).isEqualTo("REPAIRED");
		assertThat(firstRepair.get("attemptCount")).isEqualTo(2);
		assertThat(firstRepair.get("verificationLevel")).isEqualTo("TECHNICAL_ONLY");
		String version = firstRepair.get("experienceVersion").toString();
		assertThat(first.getMessage()).contains("2", "业务含义仍需核对");
		assertThat(executions.get()).isEqualTo(2);
		verify(model, times(2)).chat(any(ChatRequest.class));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_release_event WHERE action='AUTO_TECHNICAL'",
				Integer.class))
			.isEqualTo(1);
		var owned = metadata.findOwnedQuery(owner, first.getDataQuery().get("queryId").toString()).orElseThrow();
		assertThat(owned.candidateJson()).contains("REPAIRED", version, "proofDigest")
			.doesNotContain("SELECT", "'A'", "parameters");
		var feedbacks = new DataQueryCorrectionService(source, metadata, json, clock);
		assertThat(feedbacks.feedback(owner, owned.queryId(), DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"合成反馈：仍需核对口径", 601L))
			.isNotBlank();
		assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(601L)).get(601L).dataQuery().get("repair"))
			.isNotNull();
		assertThat(feedbacks.workbench(publisher("1")).feedback().get(0))
			.containsEntry("technical_experience_version", version)
			.doesNotContainKey("candidate_json");

		// Fresh service/repository proves persisted experience, not a Java map or cached
		// old answer.
		var fresh = new DataQueryMetadataRepository(source, json, clock);
		var freshService = new DataQueryAiChatService(fresh, (id, scope, sql) -> {
			executions.incrementAndGet();
			try (var connection = source.getConnection()) {
				return DataQuerySqlExecutor.read(connection, sql);
			}
			catch (java.sql.SQLException error) {
				throw DataQuerySqlExecutor.failure(error);
			}
		});
		jdbc.update("INSERT INTO dev_device VALUES('B'),('B')");
		var reused = freshService.execute(freshService.begin(owner, "reader", message(602, "换成B，查询最新数量")),
				brokenSql("B"), facts -> {
					throw new AssertionError("learned repair must not invoke another model");
				});
		assertThat(reused.getDataQuery()).containsEntry("status", "SUCCEEDED");
		assertThat(((Map<?, ?>) reused.getDataQuery().get("repair")).get("status")).isEqualTo("REUSED");
		assertThat(reused.getDataQuery().get("queryId")).isNotEqualTo(first.getDataQuery().get("queryId"));
		assertThat(reused.getDataQuery().get("rows")).isEqualTo(List.of(List.of(Integer
			.toString(jdbc.queryForObject("SELECT COUNT(*) FROM dev_device WHERE company_id='B'", Integer.class)))));
		assertThat(executions.get()).isEqualTo(3);
		fresh.technicalExperiences().disable(publisher("1"), version, "合成测试回滚，停用自动复用");
		var disabled = freshService.execute(freshService.begin(owner, "reader", message(603, "B数量")), brokenSql("B"),
				facts -> {
					throw new AssertionError("disabled experience cannot be relearned");
				});
		assertThat(disabled.getDataQuery().get("code")).isEqualTo("SQL_COLUMN_REFERENCE_INVALID");
		assertThat(((Map<?, ?>) disabled.getDataQuery().get("repair")).get("status")).isEqualTo("DISABLED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_rule_version WHERE definition_key=?",
				Integer.class, DataQuerySqlRepair.VERSION))
			.isEqualTo(1);
		assertThat(freshService
			.execute(freshService.begin(owner, "reader", message(604, "设备总数量")), "SELECT COUNT(*) FROM dev_device")
			.getDataQuery()
			.get("status")).isEqualTo("SUCCEEDED");
		verifyZeroInteractions(legacy);
	}

	@Test
	void technicalExperienceIsTenantScopedAndCannotBeDisabledByAnOrdinaryUserOrModel() throws Exception {
		repairModel(brokenSql("A"));
		var first = rule.process(message(610, "A数量")).blockFirst();
		String version = ((Map<?, ?>) first.getDataQuery().get("repair")).get("experienceVersion").toString();
		var store = metadata.technicalExperiences();
		assertThat((List<?>) store.list(publisher("2")).get("items")).isEmpty();
		assertThatThrownBy(() -> store.disable(publisher("2"), version, "other tenant")).hasMessage("DRAFT_NOT_FOUND");
		assertThatThrownBy(
				() -> store.disable(new DataQuerySecurity.ManagementActor(owner, Set.of()), version, "model"))
			.hasMessage("MANAGEMENT_FORBIDDEN");
		assertThat((List<?>) store.list(publisher("1")).get("items")).hasSize(1);
	}

	@Test
	void missingRepairMigrationDoesNotBreakValidOriginalQueriesOrPretendRepairIsAvailable() throws Exception {
		jdbc.execute("ALTER TABLE ai_dataquery_rule_version DROP COLUMN technical_payload_json");
		modelSql("SELECT COUNT(*) FROM dev_device", 0);
		assertThat(rule.process(message(620, "设备数")).blockFirst().getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		repairModel(brokenSql("A"));
		var failed = rule.process(message(621, "A设备数")).blockFirst();
		assertThat(failed.getDataQuery().get("code")).isEqualTo("SQL_COLUMN_REFERENCE_INVALID");
		assertThat(((Map<?, ?>) failed.getDataQuery().get("repair")).get("status")).isEqualTo("UNAVAILABLE");
		assertThat(metadata.technicalExperiences().list(publisher("1"))).containsEntry("available", false);
	}

	@Test void modelCannotSmuggleSemanticChangesAndUnknownFieldsAreNotGuessed() throws Exception {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(sqlReply(brokenSql("A"))),reply("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\",\"sql\":\"SELECT * FROM dev_device\"}"));
        var failed=rule.process(message(630,"A设备数")).blockFirst();
        assertThat(failed.getDataQuery().get("status")).isEqualTo("ERROR");
        assertThat(executions.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_rule_version",Integer.class)).isZero();
        modelSql("SELECT dev_device.wrong_field FROM dev_device d",0);
        var unknown=rule.process(message(631,"未知字段")).blockFirst();
        assertThat(((Map<?,?>)unknown.getDataQuery().get("repair")).get("status")).isEqualTo("NOT_SUPPORTED");
        assertThat(executions.get()).isEqualTo(2);
    }

	@Test
	void unpublishedOrChangedExperiencesCannotExecuteAndMetadataChangesRequireFreshEvidence() throws Exception {
		repairModel(brokenSql("A"));
		var result = rule.process(message(640, "A设备数")).blockFirst();
		String version = ((Map<?, ?>) result.getDataQuery().get("repair")).get("experienceVersion").toString();
		jdbc.update("UPDATE ai_dataquery_rule_version SET state='DRAFT' WHERE rule_version=?", version);
		modelSql(brokenSql("A"), 0);
		assertThat(rule.process(message(641, "A设备数")).blockFirst().getDataQuery().get("status")).isEqualTo("ERROR");
		jdbc.update("UPDATE ai_dataquery_rule_version SET state='PUBLISHED' WHERE rule_version=?", version);
		jdbc.update("UPDATE ai_data_field SET virtual_comment='new-metadata' WHERE table_id=101");
		var c = service.begin(owner, "reader", message(642, "A设备数"));
		var d = new DataQuerySqlRepair().diagnose(brokenSql("A"), c.inventory()).orElseThrow();
		assertThat(metadata.technicalExperiences().lookup(c, d).status()).isEqualTo("STALE");
		assertThat(service.execute(c, brokenSql("A"), null).getDataQuery().get("status")).isEqualTo("ERROR");
	}

	@Test void revocationDuringRepairResearchDiscardsSuggestionWithoutASecondQuery() throws Exception {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(sqlReply(brokenSql("A")))).thenAnswer(call -> {
            jdbc.update("DELETE FROM ai_dataquery_access");
            return reply("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\"}");
        });
        var failed=rule.process(message(650,"A设备数")).blockFirst();
        assertThat(failed.getDataQuery().get("status")).isEqualTo("ERROR");
        assertThat(executions.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_rule_version",Integer.class)).isZero();
        assertThat(failed.getChartId()).isNull();
    }

	@Test
	void noThirdAttemptOrLearningWhenRepairedExecutionStillFails() {
		AtomicInteger attempts = new AtomicInteger();
		var broken = new DataQueryAiChatService(metadata, (id, scope, sql) -> {
			if (attempts.incrementAndGet() == 1)
				throw new DataQueryException("SQL_COLUMN_REFERENCE_INVALID");
			throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
		});
		var response = broken.execute(broken.begin(owner, "reader", message(660, "A设备数")), brokenSql("A"),
				facts -> "{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\"}");
		assertThat(attempts.get()).isEqualTo(2);
		assertThat(response.getDataQuery()).containsEntry("status", "ERROR")
			.containsEntry("code", "RESULT_LIMIT_EXCEEDED");
		assertThat(((Map<?, ?>) response.getDataQuery().get("repair")).get("status")).isEqualTo("REPAIR_FAILED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_rule_version", Integer.class)).isZero();
	}

	@Test
	void disableDuringReusedQueryWithholdsRowsAndDoesNotLeaveSuccessHistory() throws Exception {
		repairModel(brokenSql("A"));
		var learned = rule.process(message(670, "A设备数")).blockFirst();
		String version = ((Map<?, ?>) learned.getDataQuery().get("repair")).get("experienceVersion").toString();
		var stopping = new DataQueryAiChatService(metadata, (id, scope, sql) -> {
			metadata.technicalExperiences().disable(publisher("1"), version, "in-flight regression");
			return new DataQuerySqlExecutor.Rows(List.of("value"), List.of(List.of("synthetic-withheld-value")));
		});
		var result = stopping.execute(stopping.begin(owner, "reader", message(671, "A设备数")), brokenSql("A"));
		assertThat(result.getDataQuery()).containsEntry("status", "ERROR")
			.containsEntry("code", "REPAIR_EXPERIENCE_CHANGED");
		assertThat(result.getMessage()).doesNotContain("synthetic-withheld-value");
		var query = metadata.findOwnedQuery(owner, result.getDataQuery().get("queryId").toString()).orElseThrow();
		assertThat(query.resultCode()).isEqualTo("REPAIR_EXPERIENCE_CHANGED");
		assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=671", String.class))
			.doesNotContain("synthetic-withheld-value");
	}

	@Test
	void monthParametersAndEmptyResultsAreNotBroadenedToMakeRepairLookSuccessful() throws Exception {
		jdbc.execute("ALTER TABLE dev_patrol_record ADD start_date DATE");
		jdbc.update(
				"INSERT INTO ai_data_field VALUES(100,1,'synthetic','start_date','date','synthetic','synthetic-date')");
		jdbc.update("UPDATE dev_patrol_record SET start_date='2026-08-03'");
		jdbc.update("INSERT INTO dev_patrol_record VALUES('C','2026-09-03')");
		String august = "SELECT COUNT(*) AS total FROM dev_patrol_record p WHERE dev_patrol_record.start_date>='2026-08-01' AND dev_patrol_record.start_date<'2026-09-01'";
		when(model.chat(any(ChatRequest.class))).thenReturn(reply(sqlReply(august)),
				reply("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_patrol_record\",\"alias\":\"p\"}"));
		var first = rule.process(message(680, "2026年8月记录数")).blockFirst();
		assertThat(first.getDataQuery().get("rows")).isEqualTo(List.of(List.of("2")));
		jdbc.update("INSERT INTO dev_patrol_record VALUES('D','2026-09-04'),('E','2026-10-01')");
		String september = august.replace("2026-09-01", "2026-10-01").replace("2026-08-01", "2026-09-01");
		var next = service.execute(service.begin(owner, "reader", message(681, "改查2026年9月")), september);
		assertThat(((Map<?, ?>) next.getDataQuery().get("repair")).get("status")).isEqualTo("REUSED");
		assertThat(next.getDataQuery().get("rows")).isEqualTo(List.of(List.of(jdbc.queryForObject(
				"SELECT CAST(COUNT(*) AS VARCHAR) FROM dev_patrol_record WHERE start_date>=? AND start_date<?",
				String.class, "2026-09-01", "2026-10-01"))));
		String empty = "SELECT dev_patrol_record.company_id FROM dev_patrol_record p WHERE dev_patrol_record.start_date>='2027-01-01'";
		var zero = service.execute(service.begin(owner, "reader", message(682, "2027年记录明细")), empty);
		assertThat(zero.getDataQuery()).containsEntry("status", "SUCCEEDED").containsEntry("rowCount", 0);
		assertThat(zero.getDataQuery().get("rows")).isEqualTo(List.of());
	}

	@Test
	void screenshotSeptemberTopTenUsesOriginalModelSqlAndExactChartRowsWithoutAPublishedRule() throws Exception {
		jdbc.execute("ALTER TABLE dev_patrol_record ADD patrol_dept_id VARCHAR(64)");
		jdbc.execute("ALTER TABLE dev_patrol_record ADD patrol_dept_name VARCHAR(64)");
		jdbc.execute("ALTER TABLE dev_patrol_record ADD start_date TIMESTAMP");
		for (String field : List.of("patrol_dept_id", "patrol_dept_name", "start_date"))
			jdbc.update("INSERT INTO ai_data_field VALUES(100,1,'synthetic',?,'varchar','synthetic','synthetic')",
					field);
		// Twelve distinct department keys; two have the same name, and one is null.
		for (int dept = 0; dept < 12; dept++) {
			String name = dept < 2 ? "生产运行部" : dept == 2 ? null : "合成部门" + dept;
			for (int count = 0; count < 12 - dept; count++)
				jdbc.update("INSERT INTO dev_patrol_record VALUES('A',?,?,'2026-09-15 12:00:00')", "D" + dept, name);
		}
		jdbc.update(
				"INSERT INTO dev_patrol_record VALUES('A','outside','不应出现','2026-08-31 23:59:59'),('A','outside','不应出现','2026-10-01 00:00:00')");
		String sql = "SELECT patrol_dept_name AS department,COUNT(*) AS total FROM dev_patrol_record "
				+ "WHERE start_date >= '2026-09-01' AND start_date < '2026-10-01' "
				+ "GROUP BY patrol_dept_id,patrol_dept_name ORDER BY total DESC,patrol_dept_id LIMIT 10";
		modelSql(sql, 3);
		var msg = message(800, "2026年9月各部门巡视记录最多的前十");
		var answer = rule.process(msg).blockFirst();
		assertThat(answer.getDataQuery()).containsEntry("status", "SUCCEEDED")
			.containsEntry("queryMode", "AI_SQL")
			.containsEntry("ruleVersion", null)
			.containsEntry("rowCount", 10);
		assertThat(answer.getMessage()).doesNotContain("请提供明确", "不应出现");
		var request = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
		verify(model).chat(request.capture());
		assertThat(request.getValue().messages().toString()).contains("2026年9月各部门巡视记录最多的前十");
		assertThat(executions.get()).isEqualTo(1);
		verifyZeroInteractions(legacy);
		var chartProvider = mock(ModelProvider.class);
		var chartModel = mock(ChatModel.class);
		when(chartProvider.getAiJSONAssistant(anyString())).thenReturn(Triple.of(chartModel, null,
				com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums.OPENAI.getFormat()));
		when(chartModel.chat(any(ChatRequest.class)))
			.thenReturn(reply("{\"series\":[{\"type\":\"bar\",\"data\":[999999]}]}"));
		var generator = chartGenerator(chartProvider);
		generator.generateChart(msg, answer.getMessage());
		var option = json.readTree(generator.getChart(answer.getChartId()));
		assertThat(option.path("series").get(0).path("data").toString()).isEqualTo("[12,11,10,9,8,7,6,5,4,3]");
		assertThat(option.path("xAxis").path("data").size()).isEqualTo(10);
		assertThat(option.path("xAxis").path("data").get(0).asText()).contains("生产运行部", "第1行");
		assertThat(option.path("xAxis").path("data").get(1).asText()).contains("生产运行部", "第2行");
		assertThat(option.path("xAxis").path("data").get(2).asText()).contains("空值", "第3行");
		verifyZeroInteractions(chartProvider, chartModel);
		// Fetching chart/history is not another SELECT, and cannot cross tenant/user.
		assertThat(generator.getChart(answer.getChartId())).isNotBlank();
		assertThat(executions.get()).isEqualTo(1);
		login(2, 2, "reader");
		assertThatThrownBy(() -> generator.getChart(answer.getChartId())).hasMessage("QUERY_NOT_FOUND");
		login(1, 3, "editor");
		assertThatThrownBy(() -> generator.getChart(answer.getChartId())).hasMessage("QUERY_NOT_FOUND");
	}

	private com.deepdimen.matrix.knowledge.service.impl.AiChartGenerateServiceImpl chartGenerator(
			ModelProvider models) {
		var generator = new com.deepdimen.matrix.knowledge.service.impl.AiChartGenerateServiceImpl(
				mock(com.deepdimen.matrix.knowledge.mapper.AiModelMapper.class), models);
		var beans = new StaticListableBeanFactory();
		beans.addBean("ai", service);
		ReflectionTestUtils.setField(generator, "dataQueryChats", beans.getBeanProvider(DataQueryAiChatService.class));
		return generator;
	}

	@Test
	void originalModelAndAllThreeChartTypesAreConnectedWithoutAPublishedBusinessRule() throws Exception {
		for (int chart = 1; chart <= 3; chart++) {
			modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id ORDER BY total DESC",
					chart);
			var message = message(40 + chart, "设备分布，请用图表展示");
			var result = rule.process(message).blockFirst();
			assertThat(result.getDataQuery()).containsEntry("status", "SUCCEEDED")
				.containsEntry("ruleVersion", null)
				.containsEntry("queryMode", "AI_SQL");
			assertThat(result.getChartType()).isEqualTo(Integer.toString(chart));
			assertThat(result.getChartId()).isEqualTo("dq-" + result.getDataQuery().get("queryId"));
			assertThat(result.getMessage()).contains("A", "B", "2");
			var query = metadata.findOwnedQuery(owner, result.getDataQuery().get("queryId").toString()).orElseThrow();
			assertThat(query.originalQuestion()).isEqualTo("设备分布，请用图表展示");
			assertThat(query.candidateJson()).contains("AI_SQL", "sqlDigest").doesNotContain("SELECT", "parameters");
			assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=?", String.class,
					40 + chart))
				.isEqualTo(result.getMessage());
			var feedbacks = new DataQueryCorrectionService(source, metadata, json, clock);
			assertThat(feedbacks.feedback(owner, query.queryId(),
					DataQueryCorrectionService.Category.BUSINESS_DEFINITION, "合成问题反馈", 40L + chart))
				.isNotBlank();
			assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(40L + chart))
				.get(40L + chart)
				.dataQuery()).containsEntry("executionVersion", DataQueryAiChatService.EXECUTION_VERSION);
			verify(charts).generateChart(same(message), eq(result.getMessage()));
		}
		assertThat(executions.get()).isEqualTo(3);
		verifyZeroInteractions(legacy);
		verify(data, never()).queryDataSchema(anyLong());
		verify(memories, never()).get("shared-conversation");
	}

	@Test
	void capabilityOverviewUsesSelectedModelOnMetadataAndRestoresWithoutSql() throws Exception {
		jdbc.update("UPDATE ai_data_table SET table_comment=? WHERE table_name=?", "巡视记录", "dev_patrol_record");
		jdbc.update("UPDATE ai_data_table SET table_comment=? WHERE table_name=?", "设备卡片", "dev_device");
		modelAnswer("DATASET_GUIDANCE", "2 张表：巡视记录和设备卡片，可以按已有字段提问。", "");
		for (int i = 0; i < 2; i++) {
			var response = rule.process(message(170 + i, i == 0 ? "你能查哪些数据" : "你这个数据集能查什么")).blockFirst();
			assertThat(response.getDataQuery()).containsEntry("status", "ANSWERED")
				.containsEntry("responseType", "DATASET_GUIDANCE");
			assertThat(response.getMessage()).contains("2 张表", "巡视记录", "设备卡片").doesNotContain("查询未执行", "查询失败", "1683");
			var id = response.getDataQuery().get("queryId").toString();
			var details = new DataQueryExecutionDetails(metadata, json).read(owner, id);
			assertThat(details.sql().status()).isEqualTo("NOT_EXECUTED");
			assertThat(details.steps()).extracting(DataQueryExecutionTrace.Step::phase)
				.contains("AUTHORIZE_DATASET", "LOAD_METADATA", "MODEL_GENERATE", "PARSE_MODEL_RESPONSE",
						"RESPOND_WITHOUT_QUERY")
				.doesNotContain("BUILD_PROMPT", "JDBC_QUERY");
			assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(170L + i)).get(170L + i).dataQuery())
				.containsEntry("responseType", "DATASET_GUIDANCE");
		}
		assertThat(executions.get()).isZero();
		verify(model, times(2)).chat(any(ChatRequest.class));
		verifyZeroInteractions(charts, legacy);
	}

	@Test
	void overviewRejectsRevokedAccessAndChangedMetadataBeforeResponding() {
		var msg = message(172, "你能查哪些数据");
		var context = service.begin(owner, "reader", msg);
		jdbc.update("UPDATE ai_data_table SET table_comment='changed' WHERE table_name='dev_device'");
		assertThatThrownBy(() -> service.tryDatasetOverview(context, (q, inventory, recheck) -> {
			throw new AssertionError();
		})).isInstanceOf(DataQueryException.class);
		jdbc.update("DELETE FROM ai_dataquery_access");
		var result = rule.process(message(173, "你能查哪些数据")).blockFirst();
		assertThat(result.getDataQuery().get("status")).isNotEqualTo("ANSWERED");
		assertThat(executions.get()).isZero();
		verifyZeroInteractions(model, provider, charts, legacy);
	}

	@Test
	void similarDataRequestsAndCompoundQuestionsKeepTheNormalModelPath() throws Exception {
		for (String question : List.of("有哪些设备", "查一下某部门设备", "你能查哪些数据，顺便统计九月巡视总数")) {
			modelAnswer("CLARIFICATION", "synthetic-model-response", "");
			var result = rule.process(message(174, question)).blockFirst();
			assertThat(result.getMessage()).contains("synthetic-model-response");
			jdbc.update("DELETE FROM ai_chat_record WHERE record_id=174");
		}
		verify(model, times(3)).chat(any(ChatRequest.class));
	}

	@Test
    void truncatedGenerationCanRecoverOnceAndStillUsesSqlValidationAndDefaultPreview() throws Exception {
        when(model.defaultRequestParameters()).thenReturn(dev.langchain4j.model.openai.OpenAiChatRequestParameters
                .builder().maxOutputTokens(2048).build());
        var partial = ChatResponse.builder().aiMessage(AiMessage.from("{\"sql\":\"SELECT broken"))
                .finishReason(dev.langchain4j.model.output.FinishReason.LENGTH)
                .tokenUsage(new dev.langchain4j.model.output.TokenUsage(100, 2048, 2148)).build();
        var complete = reply(json.writeValueAsString(Map.of("responseType", "QUERY", "sql",
                "SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id ORDER BY total DESC",
                "chartOutput", false, "chartType", 0)));
        when(model.chat(any(ChatRequest.class))).thenReturn(partial).thenReturn(complete);
        var result = rule.process(message(9900, "各公司设备数量排名")).blockFirst();
        assertThat(result.getDataQuery()).containsEntry("code", "OK");
        assertThat(executions.get()).isEqualTo(1);
        var id = result.getDataQuery().get("queryId").toString();
        var details = new DataQueryExecutionDetails(metadata, json).read(owner, id);
        assertThat(details.steps()).extracting(DataQueryExecutionTrace.Step::phase)
                .contains("MODEL_GENERATE", "MODEL_REGENERATE", "PARSE_MODEL_RESPONSE");
        var evidence = json.readTree(metadata.findOwnedQuery(owner, id).orElseThrow().candidateJson()).path("resultPreview");
        assertThat(evidence.path("limit").asInt()).isEqualTo(10);
        assertThat(evidence.path("explicitLimit").asBoolean()).isFalse();
        assertThat(evidence.path("aggregateInputLimited").asBoolean()).isFalse();
        verify(model, times(2)).chat(any(ChatRequest.class));
    }

	@Test
    void revokedPermissionBetweenGenerationAndRetryPreventsTheSecondModelCall() {
        when(model.defaultRequestParameters()).thenReturn(dev.langchain4j.model.openai.OpenAiChatRequestParameters
                .builder().maxOutputTokens(2048).build());
        when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            jdbc.update("DELETE FROM ai_dataquery_access");
            return ChatResponse.builder().aiMessage(AiMessage.from("{}"))
                    .finishReason(dev.langchain4j.model.output.FinishReason.LENGTH).build();
        });
        var result = rule.process(message(9901, "统计设备总数")).blockFirst();
        assertThat(result.getDataQuery().get("code")).isNotEqualTo("OK");
        assertThat(executions.get()).isZero();
        verify(model).chat(any(ChatRequest.class));
    }

	@Test
	void truncatedOrInvalidStructuredModelResponseDoesNotReachSqlExecution() {
		when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
			.aiMessage(AiMessage.from("{\"responseType\":\"QUERY\",\"sql\":\"SELECT COUNT(*) FROM dev_device\"}"))
			.finishReason(dev.langchain4j.model.output.FinishReason.LENGTH).build());
		var truncated = rule.process(message(175, "设备总数")).blockFirst();
		assertThat(truncated.getDataQuery()).containsEntry("code", "AI_RESPONSE_TRUNCATED");
		var trace = new DataQueryExecutionDetails(metadata, json)
			.read(owner, truncated.getDataQuery().get("queryId").toString());
		assertThat(trace.steps().stream().filter(x -> "PARSE_MODEL_RESPONSE".equals(x.phase())).findFirst().orElseThrow().code())
			.isEqualTo("AI_RESPONSE_TRUNCATED");
		when(model.chat(any(ChatRequest.class))).thenReturn(reply("[]"));
		var invalid = rule.process(message(176, "设备总数")).blockFirst();
		assertThat(invalid.getDataQuery()).containsEntry("code", "AI_RESPONSE_INVALID");
		assertThat(executions.get()).isZero();
		verifyZeroInteractions(charts, legacy);
	}

	@Test
	void metadataQuestionsAreAnsweredWithoutSqlAndSurviveHistoryRestore() throws Exception {
		for (int i = 0; i < 2; i++) {
			String question = i == 0 ? "帮我梳理下数据，罗列20个我可以去问的问题" : "你可以理解数据吗";
			String answer = "可以根据当前设备、巡视表的结构与备注，介绍数据含义并推荐可问的问题。";
			when(model.chat(any(ChatRequest.class))).thenReturn(reply(json.writeValueAsString(Map.of("responseType",
					"DATASET_GUIDANCE", "answer", answer, "sql", "", "chartOutput", true, "chartType", 3))));
			var response = rule.process(message(70 + i, question)).blockFirst();
			assertThat(response.getDataQuery()).containsEntry("status", "ANSWERED")
				.containsEntry("queryMode", "AI_ASSISTANCE")
				.containsEntry("responseType", "DATASET_GUIDANCE");
			assertThat(response.getMessage()).isEqualTo(answer).doesNotContain("未执行数据查询", "请补充时间范围");
			assertThat(response.getDataQuery()).doesNotContainKeys("rows", "rowCount", "explanation");
			assertThat(response.getChartId()).isNull();
			assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(70L + i)).get(70L + i).dataQuery())
				.containsEntry("status", "ANSWERED")
				.containsEntry("queryMode", "AI_ASSISTANCE")
				.containsEntry("responseType", "DATASET_GUIDANCE")
				.doesNotContainKey("explanation");
			var audit = metadata.findOwnedQuery(owner, response.getDataQuery().get("queryId").toString()).orElseThrow();
			assertThat(audit.resultCode()).isEqualTo("ASSISTANCE_COMPLETED");
			assertThat(json.readTree(audit.candidateJson()).has("sqlDigest")).isFalse();
			assertThat(audit.candidateJson()).doesNotContain("JDBC_QUERY");
			var details = new DataQueryExecutionDetails(metadata, json).read(owner, audit.queryId());
			assertThat(details.sql().status()).isEqualTo("NOT_EXECUTED");
			assertThat(details.sql().submission()).isEqualTo("NOT_APPLICABLE");
			assertThat(details.steps()).extracting(DataQueryExecutionTrace.Step::phase)
				.contains("MODEL_GENERATE", "RESPOND_WITHOUT_QUERY")
				.doesNotContain("JDBC_QUERY");
		}
		assertThat(executions.get()).isZero();
		verifyZeroInteractions(charts, legacy);
	}

	@Test
	void guidanceRemovesRepeatedInterfaceNoticesButKeepsBusinessCaveatsAndCleanHistory() throws Exception {
		String legacy = "基于当前数据集的表结构、备注和术语说明，未执行数据查询；推荐问题不代表已验证的统计结果。";
		String footer = "根据当前数据集说明整理，具体统计以实际查询结果为准。";
		String body = "可以按部门了解设备分布。\n\n注意：设备状态编码尚未确认，不能据此判断设备异常。";
		for (int i = 0; i < 3; i++) {
			String answer = i == 0 ? legacy + "\r\n\r\n" + legacy + "\n\n" + body
					: i == 1 ? body + "\n\n" + legacy + "\n" + footer : footer + "\n\n" + body;
			modelAnswer("DATASET_GUIDANCE", answer, "");
			var result = rule.process(message(80 + i, "介绍设备分布的可问方向" + i)).blockFirst();
			assertThat(result.getDataQuery()).containsEntry("status", "ANSWERED").containsEntry("message", footer);
			assertThat(result.getMessage()).isEqualTo(body);
			assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=?", String.class,
					80 + i))
				.isEqualTo(body);
		}
		var requests = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(3)).chat(requests.capture());
		assertThat(requests.getValue().messages().toString()).contains(body.split("\n")[0]).doesNotContain(legacy);
		assertThat(executions.get()).isZero();
		verifyZeroInteractions(charts, this.legacy);
	}

	@Test
	void guidanceDoesNotRemoveQuotedOrEmbeddedNoticesOrTurnNoticeOnlyTextIntoAnAnswer() throws Exception {
		String notice = "基于当前数据集的表结构、备注和术语说明，未执行数据查询；推荐问题不代表已验证的统计结果。";
		int id = 90;
		for (String body : List.of("> " + notice, "示例：" + notice, "术语原文：\n\n" + notice + "\n\n以上说明仅供解释。",
				"```text\n" + notice + "\n```")) {
			modelAnswer("DATASET_GUIDANCE", body, "");
			var result = rule.process(message(id++, "解释说明文本")).blockFirst();
			assertThat(result.getMessage()).isEqualTo(body);
		}
		modelAnswer("DATASET_GUIDANCE", notice + "\n\n" + notice, "");
		assertThat(rule.process(message(95, "理解数据")).blockFirst().getDataQuery())
			.containsEntry("code", "AI_RESPONSE_INVALID")
			.doesNotContainEntry("status", "ANSWERED");
		assertThat(executions.get()).isZero();
	}

	@Test
	void unboundedDetailResultsUseAnHonestTenRowPreviewWithoutModelRetryOrChart() throws Exception {
		jdbc.update("INSERT INTO dev_device SELECT CONCAT('device-',X) FROM SYSTEM_RANGE(1,1005)");
		modelSql("SELECT company_id FROM dev_device ORDER BY company_id", 3);
		var response = rule.process(message(110, "哪些设备符合条件？")).blockFirst();
		assertThat(response.getDataQuery()).containsEntry("status", "SUCCEEDED").containsEntry("rowCount", 10);
		assertThat((Map<String, Object>) response.getDataQuery().get("resultPreview")).containsEntry("hasMore", true)
			.containsEntry("limit", 10)
			.containsEntry("totalKnown", false)
			.containsEntry("orderSpecified", true);
		assertThat(response.getMessage()).contains("先展示10条", "还有更多", "不是完整清单");
		assertThat(response.getChartId()).isNull();
		assertThat(executions.get()).isEqualTo(1);
		verify(model).chat(any(ChatRequest.class));
		verifyZeroInteractions(charts, legacy);
		var history = metadata.findOwnedChatReferences(owner, "reader", List.of(110L)).get(110L).dataQuery();
		assertThat(history).containsEntry("resultPreview", response.getDataQuery().get("resultPreview"));
		assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=110", String.class))
			.isEqualTo(response.getMessage());
		var audit = metadata.findOwnedQuery(owner, response.getDataQuery().get("queryId").toString()).orElseThrow();
		assertThat(audit.candidateJson()).contains("resultPreview").doesNotContain("RESULT_LIMIT_EXCEEDED");
	}

	@Test
	void detailPreviewDoesNotChangeCountsExplicitLimitsOrPretendThereAreMoreRows() throws Exception {
		jdbc.update("INSERT INTO dev_device SELECT CONCAT('device-',X) FROM SYSTEM_RANGE(1,1005)");
		modelSql("SELECT COUNT(*) AS total FROM dev_device", 0);
		var count = rule.process(message(111, "共有多少设备？")).blockFirst();
		assertThat(count.getDataQuery()).containsEntry("rows", List.of(List.of("1008")))
			.doesNotContainKey("resultPreview");
		modelSql("SELECT company_id FROM dev_device ORDER BY company_id LIMIT 20", 0);
		var twenty = rule.process(message(112, "列出前20个设备")).blockFirst();
		assertThat(twenty.getDataQuery()).containsEntry("rowCount", 20);
		assertThat((Map<String, Object>) twenty.getDataQuery().get("resultPreview")).containsEntry("limit", 20)
			.containsEntry("hasMore", true);
		modelSql("SELECT company_id FROM dev_device WHERE company_id='A'", 0);
		var shortList = rule.process(message(113, "A公司的设备有哪些？")).blockFirst();
		assertThat(shortList.getDataQuery()).containsEntry("rowCount", 2);
		assertThat((Map<String, Object>) shortList.getDataQuery().get("resultPreview")).containsEntry("hasMore", false)
			.containsEntry("orderSpecified", false);
		assertThat(shortList.getMessage()).doesNotContain("还有更多", "不是完整清单");
	}

	@Test
	void explainingPreviewRetainsItsScopeWithoutRequeryingOrClaimingFullStatistics() throws Exception {
		jdbc.update("INSERT INTO dev_device SELECT CONCAT('device-',X) FROM SYSTEM_RANGE(1,20)");
		modelSql("SELECT company_id FROM dev_device", 0);
		var sourceResult = rule.process(message(114, "列出设备")).blockFirst();
		String sourceId = sourceResult.getDataQuery().get("queryId").toString();
		assertThat(sourceResult.getMessage()).contains("本次未指定排序", "不代表最低、最新或排名");
		modelAnswer("RESULT_EXPLANATION", "可以解释已展示的设备记录，但不能据此计算整体比例。", sourceId);
		var explanation = rule.process(message(115, "解释这些结果")).blockFirst();
		assertThat(explanation.getDataQuery()).containsEntry("status", "ANSWERED");
		assertThat((Map<String, Object>) explanation.getDataQuery().get("source")).containsEntry("resultPreview",
				sourceResult.getDataQuery().get("resultPreview"));
		assertThat(explanation.getDataQuery().get("message").toString()).contains("来源结果仅展示10条预览", "不能据此推断全量统计");
		assertThat(explanation.getMessage()).doesNotContain("来源结果仅展示10条预览");
		assertThat(executions.get()).isEqualTo(1);
		var requests = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(2)).chat(requests.capture());
		assertThat(requests.getAllValues().get(1).messages().toString()).contains("resultPreview", "hasMore");
		assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(115L)).get(115L).dataQuery())
			.containsEntry("source", explanation.getDataQuery().get("source"));
	}

	@Test
	void previewDoesNotTurnTimeoutOrWideFieldFailureIntoPartialSuccess() throws Exception {
		long id = 116;
		for (String code : List.of("QUERY_TIMEOUT", "DATASET_NOT_AUTHORIZED", "RESULT_LIMIT_EXCEEDED")) {
			var failing = new DataQueryAiChatService(metadata, (identity, authorization, query) -> {
				assertThat(query.sql()).contains("LIMIT 11");
				throw new DataQueryException(code);
			});
			var context = failing.begin(owner, "reader", message(id++, "查看设备记录"));
			var result = failing.execute(context, "SELECT company_id FROM dev_device");
			assertThat(result.getDataQuery()).containsEntry("status", "ERROR")
				.containsEntry("code", code)
				.doesNotContainKey("rows")
				.doesNotContainKey("resultPreview");
			assertThat(result.getMessage()).doesNotContain("先展示10条");
		}
		jdbc.execute("ALTER TABLE dev_device ALTER COLUMN company_id VARCHAR(10000)");
		jdbc.update("DELETE FROM dev_device");
		jdbc.update("INSERT INTO dev_device VALUES(?)", "private-wide-value" + "x".repeat(8193));
		modelSql("SELECT company_id FROM dev_device", 0);
		var wide = rule.process(message(119, "查看设备记录")).blockFirst();
		assertThat(wide.getDataQuery()).containsEntry("code", "RESULT_LIMIT_EXCEEDED")
			.containsEntry("status", "ERROR")
			.doesNotContainKey("rows")
			.doesNotContainKey("resultPreview");
		assertThat(wide.getMessage()).doesNotContain("private-wide-value");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void technicalRepairAndItsReuseStillApplyTheSameBoundedPreview() throws Exception {
		jdbc.update("INSERT INTO dev_device SELECT 'A' FROM SYSTEM_RANGE(1,1005)");
		String detail = "SELECT company_id FROM dev_device d WHERE dev_device.company_id='A'";
		repairModel(detail);
		var repaired = rule.process(message(120, "A公司设备有哪些")).blockFirst();
		assertThat(repaired.getDataQuery()).containsEntry("status", "SUCCEEDED").containsEntry("rowCount", 10);
		assertThat((Map<String, Object>) repaired.getDataQuery().get("resultPreview")).containsEntry("hasMore", true);
		assertThat((Map<String, Object>) repaired.getDataQuery().get("repair")).containsEntry("status", "REPAIRED");
		assertThat(executions.get()).isEqualTo(2);
		modelSql(detail, 0);
		var reused = rule.process(message(121, "再查看A公司设备")).blockFirst();
		assertThat(reused.getDataQuery()).containsEntry("status", "SUCCEEDED").containsEntry("rowCount", 10);
		assertThat((Map<String, Object>) reused.getDataQuery().get("resultPreview")).containsEntry("hasMore", true);
		assertThat((Map<String, Object>) reused.getDataQuery().get("repair")).containsEntry("status", "REUSED");
		assertThat(executions.get()).isEqualTo(3);
		verifyZeroInteractions(charts, legacy);
	}

	@Test
	void freshLookupAfterSimilarHistoryRetriesMisclassifiedExplanationWithoutReplayingOldAnswer() throws Exception {
		modelSql("SELECT company_id FROM dev_device WHERE company_id='A' LIMIT 1", 0);
		String sourceId = rule.process(message(130, "A公司的设备先列一条"))
			.blockFirst()
			.getDataQuery()
			.get("queryId")
			.toString();
		String wrong = json.writeValueAsString(Map.of("responseType", "RESULT_EXPLANATION", "answer",
				"stale-answer-must-not-escape", "sourceQueryId", sourceId, "sql", ""));
		var listener = new com.deepdimen.matrix.knowledge.support.handler.core.MatrixChatModelListener(
				mock(AiChatRecordService.class), mock(org.springframework.ai.tokenizer.TokenCountEstimator.class),
				mock(AiBillService.class));
		var replies = List.of(reply(wrong), reply(json.writeValueAsString(Map.of("responseType", "QUERY", "sql",
				"SELECT company_id FROM dev_device WHERE company_id='A'", "chartOutput", false))));
		var attributes = new ArrayList<Map<Object, Object>>();
		when(model.chat(any(ChatRequest.class))).thenAnswer(call -> {
			var observed = new HashMap<Object, Object>();
			listener.onRequest(new dev.langchain4j.model.chat.listener.ChatModelRequestContext(
					call.getArgument(0, ChatRequest.class), null, observed));
			assertThat(ChatMessageContextHolder.get()).isNull();
			attributes.add(observed);
			return replies.get(attributes.size() - 1);
		});
		var previousRequest = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
		AiMessageResultDTO result;
		try {
			var request = new org.springframework.mock.web.MockHttpServletRequest();
			request.addHeader("User-Agent", "dataquery-regression");
			org.springframework.web.context.request.RequestContextHolder
				.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
			result = rule.process(message(131, "哪些设备属于A公司？")).blockFirst();
		}
		finally {
			org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previousRequest);
		}
		assertThat(attributes).hasSize(2).allSatisfy(a -> assertThat(a).containsEntry("recordId", 131L));
		assertThat(ChatMessageContextHolder.get()).isNull();
		assertThat(result.getDataQuery()).containsEntry("status", "SUCCEEDED")
			.containsEntry("queryMode", "AI_SQL")
			.containsEntry("rowCount", 2);
		assertThat(result.getMessage()).doesNotContain("stale-answer", "基于本会话已有查询结果的解释");
		assertThat(executions.get()).isEqualTo(2);
		var calls = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(3)).chat(calls.capture());
		String retry = calls.getAllValues().get(2).messages().toString();
		assertThat(retry).contains("哪些设备属于A公司？", "RESULT_EXPLANATION", "本轮", "独立完整的新问题", "明确追问");
		var trace = new DataQueryExecutionDetails(metadata, json).read(owner,
				result.getDataQuery().get("queryId").toString());
		assertThat(trace.steps().stream().filter(s -> "MODEL_GENERATE".equals(s.phase())).count()).isEqualTo(2);
		assertThat(trace.steps().stream().filter(s -> "EXECUTE_QUERY".equals(s.phase())).count()).isEqualTo(1);
		verifyZeroInteractions(legacy, charts);
	}

	@Test
	void repeatedInvalidExplanationIsRejectedAfterOneCorrectionNotAcceptedOrRetriedForever() throws Exception {
		modelSql("SELECT COUNT(*) FROM dev_device", 0);
		String sourceId = rule.process(message(130, "设备总数")).blockFirst().getDataQuery().get("queryId").toString();
		modelAnswer("RESULT_EXPLANATION", "must-not-replay", sourceId);
		var result = rule.process(message(131, "哪些巡查记录的覆盖率低于80%？")).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("status", "ERROR").containsEntry("code", "AI_RESPONSE_INVALID");
		assertThat(result.getMessage()).doesNotContain("must-not-replay");
		assertThat(executions.get()).isEqualTo(1);
		verify(model, times(3)).chat(any(ChatRequest.class));
		verifyZeroInteractions(legacy, charts);
	}

	@Test
	void intentCorrectionDoesNotBypassPermissionRecheckBeforeQueryExecution() throws Exception {
		modelSql("SELECT COUNT(*) FROM dev_device", 0);
		String sourceId = rule.process(message(130, "设备总数")).blockFirst().getDataQuery().get("queryId").toString();
		String wrong = json.writeValueAsString(Map.of("responseType", "RESULT_EXPLANATION", "answer", "must-not-replay",
				"sourceQueryId", sourceId, "sql", ""));
		when(model.chat(any(ChatRequest.class))).thenReturn(reply(wrong)).thenAnswer(call -> {
			jdbc.update("DELETE FROM ai_dataquery_access WHERE tenant_id=1");
			return reply(
					json.writeValueAsString(Map.of("responseType", "QUERY", "sql", "SELECT COUNT(*) FROM dev_device")));
		});
		var result = rule.process(message(131, "刷新刚才的结果，重新查询后再解释")).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("status", "ERROR");
		assertThat(result.getMessage()).doesNotContain("must-not-replay");
		assertThat(executions.get()).isEqualTo(1);
		verify(model, times(3)).chat(any(ChatRequest.class));
		verifyZeroInteractions(legacy, charts);
	}

	@Test
	void explanationNoticeEchoIsRemovedFromSavedBodyAndOldConversationContextButSourceIsRetained() throws Exception {
		modelSql("SELECT COUNT(*) FROM dev_device", 0);
		String sourceId = rule.process(message(130, "设备总数")).blockFirst().getDataQuery().get("queryId").toString();
		modelAnswer("RESULT_EXPLANATION", "只分析这次已返回的总数。", sourceId);
		var first = rule.process(message(131, "解释刚才的结果，不要重新查询")).blockFirst();
		String notice = first.getDataQuery().get("message").toString();
		String body = "这些已返回的记录不能证明问题持续存在。";
		jdbc.update("UPDATE ai_chat_record SET answer_text=? WHERE record_id=131",
				notice + "\n\n" + notice + "\n\n" + body);
		modelAnswer("RESULT_EXPLANATION", notice + "\n\n" + notice + "\n\n" + body, sourceId);
		var result = rule.process(message(132, "只解读刚才的结果，不要重查")).blockFirst();
		assertThat(result.getMessage()).isEqualTo(body);
		assertThat(result.getDataQuery().get("source")).isEqualTo(first.getDataQuery().get("source"));
		assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=132", String.class))
			.isEqualTo(body);
		var requests = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(3)).chat(requests.capture());
		assertThat(requests.getAllValues().get(2).messages().toString()).doesNotContain(notice);
		var history = json
			.readTree(metadata.conversationContext(service.begin(owner, "reader", message(133, "解释刚才结果"))));
		var last = history.get(history.size() - 1);
		assertThat(last.path("responseType").asText()).isEqualTo("RESULT_EXPLANATION");
		assertThat(json.convertValue(last.path("source"), Map.class)).isEqualTo(result.getDataQuery().get("source"));
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void explanationIntentCheckPreservesExplicitExplanationAndNegatedRefreshWithoutChangingOtherReplyTypes() {
		long messageId = 150;
		for (String question : List.of("只解释刚才的结果，不要重新查询", "仅分析这10条，无需重查", "解释一下，别再查数据库",
				"Please explain that result, don't refresh")) {
			var context = service.begin(owner, "reader", message(messageId++, question));
			assertThat(service.explanationConflictsWithQuestion(context, "RESULT_EXPLANATION")).as(question).isFalse();
		}
		for (String question : List.of("哪些巡查记录的覆盖率低于80%？", "再查一次相同的条件", "刷新结果后再解释", "帮我分析并查一下9月有多少记录")) {
			var context = service.begin(owner, "reader", message(messageId++, question));
			assertThat(service.explanationConflictsWithQuestion(context, "RESULT_EXPLANATION")).as(question).isTrue();
			assertThat(service.explanationConflictsWithQuestion(context, "DATASET_GUIDANCE")).isFalse();
			assertThat(service.explanationConflictsWithQuestion(context, "QUERY")).isFalse();
		}
		assertThat(executions.get()).isZero();
	}

	void modelAnswer(String type, String answer, String sourceId) throws Exception {
		when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
			.aiMessage(AiMessage.from(json.writeValueAsString(Map.of("responseType", type, "answer", answer,
				"sourceQueryId", sourceId, "sql", "", "chartOutput", false, "chartType", 0))))
			.tokenUsage(new dev.langchain4j.model.output.TokenUsage(100, 12, 112)).build());
	}

	@Test
	void explanationUsesOnlySuccessfulOwnedHistoryWithoutRequeryAndNewStatisticsStillExecute() throws Exception {
		modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id", 0);
		var sourceResult = rule.process(message(60, "设备按公司分布")).blockFirst();
		String sourceId = sourceResult.getDataQuery().get("queryId").toString();
		modelAnswer("RESULT_EXPLANATION", "在刚才返回的结果中，A公司的设备数高于B公司；这不说明原因。", sourceId);
		var explanation = rule.process(message(61, "分析一下刚才的结果")).blockFirst();
		assertThat(explanation.getDataQuery()).containsEntry("status", "ANSWERED")
			.containsEntry("queryMode", "AI_ASSISTANCE");
		assertThat(explanation.getDataQuery().get("message").toString()).contains(sourceId, "查询时间", "未执行数据查询");
		assertThat(explanation.getMessage()).contains("A公司").doesNotContain(sourceId, "未执行数据查询");
		assertThat(executions.get()).isEqualTo(1);
		var detail = new DataQueryExecutionDetails(metadata, json).read(owner,
				explanation.getDataQuery().get("queryId").toString());
		assertThat(detail.sql().status()).isEqualTo("NOT_EXECUTED");
		assertThat(detail.steps()
			.stream()
			.filter(s -> "MODEL_GENERATE".equals(s.phase()))
			.findFirst()
			.orElseThrow()
			.usage()
			.totalTokens()).isEqualTo(112);
		assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(61L)).get(61L).dataQuery())
			.containsEntry("source", explanation.getDataQuery().get("source"));
		var request = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(2)).chat(request.capture());
		assertThat(request.getValue().messages().toString()).contains(sourceId, "answerIsExcerpt", "RESULT_EXPLANATION",
				"preserve-table-comment");
		modelSql("SELECT COUNT(*) AS total FROM dev_device WHERE company_id='B'", 0);
		assertThat(rule.process(message(62, "再查B公司的设备数")).blockFirst().getDataQuery()).containsEntry("status",
				"SUCCEEDED");
		assertThat(executions.get()).isEqualTo(2);
		verifyZeroInteractions(charts, legacy);
	}

	@Test
	void assistanceSchemaDoesNotChangeTheLegacySqlToolContract() {
		var function = new Chat2SqlFunctionCalling(memories, charts, data, gate, legacy);
		var original = com.deepdimen.matrix.knowledge.support.util.ToolSpecificationsUtils
			.parametersFrom(cn.hutool.core.util.ReflectUtil.getFields(function.getGenericType()));
		assertThat(original.properties()).containsKeys("sql", "chartOutput", "chartType")
			.doesNotContainKeys("responseType", "answer", "sourceQueryId");
		var controlled = com.deepdimen.matrix.knowledge.support.util.ToolSpecificationsUtils.parametersFrom(
				cn.hutool.core.util.ReflectUtil.getFields(Chat2SqlFunctionCalling.ConversationRequest.class));
		assertThat(controlled.properties()).containsKeys("sql", "chartOutput", "chartType", "responseType", "answer",
				"sourceQueryId");
	}

	@Test
	void targetedClarificationKeepsTheActualQuestionInsteadOfGenericSqlRefusal() throws Exception {
		modelAnswer("CLARIFICATION", "这里的完成率是到期任务执行率，还是按期执行率？", "");
		var result = rule.process(message(60, "查完成率")).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("status", "NEEDS_CLARIFICATION")
			.containsEntry("queryMode", "AI_ASSISTANCE");
		assertThat(result.getMessage()).contains("到期任务执行率").doesNotContain("请补充时间范围");
		assertThat(metadata.findOwnedChatReferences(owner, "reader", List.of(60L)).get(60L).dataQuery())
			.containsEntry("status", "NEEDS_CLARIFICATION")
			.doesNotContainKey("explanation");
		assertThat(executions.get()).isZero();
	}

	@Test
	void inventedFailedGuidanceAndOtherConversationSourcesCannotBeUsedAsQueryResults() throws Exception {
		modelAnswer("RESULT_EXPLANATION", "不能发布这个解释", UUID.randomUUID().toString());
		assertThat(rule.process(message(60, "分析结果")).blockFirst().getDataQuery()).containsEntry("code",
				"RESULT_CONTEXT_UNAVAILABLE");
		modelAnswer("DATASET_GUIDANCE", "可以介绍已选表含义。", "");
		String guidance = rule.process(message(61, "你能理解数据吗")).blockFirst().getDataQuery().get("queryId").toString();
		modelAnswer("RESULT_EXPLANATION", "不能把介绍当成真实统计", guidance);
		assertThat(rule.process(message(62, "解释刚才的结果")).blockFirst().getDataQuery()).containsEntry("code",
				"RESULT_CONTEXT_UNAVAILABLE");
		modelSql("SELECT * FROM sys_user", 0);
		String failure = rule.process(message(63, "未获准查询")).blockFirst().getDataQuery().get("queryId").toString();
		modelAnswer("RESULT_EXPLANATION", "不能把失败当成真实统计", failure);
		assertThat(rule.process(message(64, "解释刚才的结果")).blockFirst().getDataQuery()).containsEntry("code",
				"RESULT_CONTEXT_UNAVAILABLE");
		modelSql("SELECT COUNT(*) FROM dev_device", 0);
		String good = rule.process(message(65, "设备数")).blockFirst().getDataQuery().get("queryId").toString();
		jdbc.update("UPDATE ai_chat_record SET conversation_id='other-conversation' WHERE record_id=65");
		modelAnswer("RESULT_EXPLANATION", "不能读取别的会话", good);
		var result = rule.process(message(66, "分析结果")).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("code", "RESULT_CONTEXT_UNAVAILABLE");
		assertThat(result.getMessage()).doesNotContain("不能读取别的会话");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void nonQueryResponsesCannotHideSqlOrTurnFailedQueriesIntoSuccessfulAnswers() throws Exception {
		when(model.chat(any(ChatRequest.class))).thenReturn(reply(json.writeValueAsString(Map.of(
			"responseType", "DATASET_GUIDANCE", "answer", "pretend-success", "sql", "SELECT COUNT(*) FROM dev_device"))));
		var mixed = rule.process(message(60, "介绍数据")).blockFirst();
		assertThat(mixed.getDataQuery()).containsEntry("code", "AI_RESPONSE_INVALID");
		assertThat(mixed.getMessage()).doesNotContain("pretend-success");
		when(model.chat(any(ChatRequest.class))).thenReturn(reply(json.writeValueAsString(Map.of(
			"responseType", "QUERY", "answer", "pretend-success", "sql", "SELECT * FROM sys_user"))));
		assertThat(rule.process(message(61, "查受限数据")).blockFirst().getDataQuery()).containsEntry("code", "SQL_TABLE_NOT_ALLOWED");
		for (String type : List.of("UNKNOWN", "DATASET_GUIDANCE")) {
			modelAnswer(type, type.equals("UNKNOWN") ? "pretend-success" : "", "");
			assertThat(rule.process(message(type.equals("UNKNOWN") ? 62 : 63, "介绍数据")).blockFirst().getDataQuery())
				.containsEntry("code", "AI_RESPONSE_INVALID");
		}
		assertThat(executions.get()).isZero();
		verifyZeroInteractions(charts, legacy);
	}

	@Test
	void explanationCannotReuseAnotherIdentityOrAnEditedOriginalQuestion() throws Exception {
		modelSql("SELECT COUNT(*) FROM dev_device", 0);
		String id = rule.process(message(60, "设备数")).blockFirst().getDataQuery().get("queryId").toString();
		modelAnswer("RESULT_EXPLANATION", "private-source-must-not-be-released", id);
		jdbc.update("UPDATE ai_dataquery_query SET user_id=3 WHERE query_id=?", id);
		assertThat(rule.process(message(61, "分析结果")).blockFirst().getDataQuery()).containsEntry("code",
				"RESULT_CONTEXT_UNAVAILABLE");
		jdbc.update("UPDATE ai_dataquery_query SET user_id=2,tenant_id=2 WHERE query_id=?", id);
		assertThat(rule.process(message(62, "分析结果")).blockFirst().getDataQuery()).containsEntry("code",
				"RESULT_CONTEXT_UNAVAILABLE");
		jdbc.update("UPDATE ai_dataquery_query SET tenant_id=1 WHERE query_id=?", id);
		jdbc.update("UPDATE ai_chat_record SET question_text='edited-question' WHERE record_id=60");
		var response = rule.process(message(63, "分析结果")).blockFirst();
		assertThat(response.getDataQuery()).containsEntry("code", "RESULT_CONTEXT_UNAVAILABLE");
		assertThat(response.getMessage()).doesNotContain("private-source-must-not-be-released");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void nonQueryAnswerIsWithheldIfAuthorizationChangesDuringModelCall() throws Exception {
		when(model.chat(any(ChatRequest.class))).thenAnswer(call -> {
			jdbc.update("DELETE FROM ai_dataquery_access");
			return reply("{\"responseType\":\"DATASET_GUIDANCE\",\"answer\":\"withhold-this\",\"sql\":\"\"}");
		});
		var result = rule.process(message(60, "你能理解数据吗")).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("status", "ERROR");
		assertThat(result.getMessage()).doesNotContain("withhold-this");
		assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=60", String.class)).doesNotContain("withhold-this");
		assertThat(executions.get()).isZero();
	}

	@Test
	void unsafeSqlFailsWithFeedbackReferenceAndCannotFallbackOrGenerateCharts() throws Exception {
		modelSql("SELECT * FROM sys_user", 3);
		var result = rule.process(message(77, "查询设备")).blockFirst();
		assertThat(result.getDataQuery().get("code")).isEqualTo("SQL_TABLE_NOT_ALLOWED");
		assertThat(result.getDataQuery().get("queryId")).isNotNull();
		assertThat(result.getMessage()).doesNotContain("sys_user");
		assertThat(executions.get()).isZero();
		verifyZeroInteractions(legacy, charts);
		assertThat(result.getChartId()).isNull();
	}

	@Test
	void loginOwnershipAndDatasetAclAreCheckedBeforeModelOrMetadataDisclosure() {
		var message = message(77, "查询设备");
		login(2, 2, "reader");
		assertThat(rule.process(message).blockFirst().getDataQuery().get("code")).isEqualTo("CHAT_RECORD_NOT_OWNED");
		login(1, 3, "editor");
		assertThat(rule.process(message).blockFirst().getDataQuery().get("code")).isEqualTo("CHAT_RECORD_NOT_OWNED");
		login(1, 2, "reader");
		jdbc.update("DELETE FROM ai_dataquery_access");
		assertThat(rule.process(message).blockFirst().getDataQuery().get("code")).isEqualTo("DATASET_NOT_AUTHORIZED");
		verifyZeroInteractions(model, data, legacy, charts);
		assertThat(executions.get()).isZero();
	}

	@Test
	void missingSelectedTableAndSameNameInOtherTenantCannotSupplySchema() {
		jdbc.update("UPDATE ai_data_table SET tenant_id=2 WHERE table_name='dev_device'");
		assertThatThrownBy(() -> metadata.inventory(owner, metadata.binding(owner)))
			.hasMessage("DATASET_METADATA_REQUIRED");
		verifyZeroInteractions(model, legacy);
	}

	@Test
	void authorizationAndMetadataAreRecheckedAfterModelAndBeforeResultRelease() {
		var c = service.begin(owner, "reader", message(77, "查询设备"));
		jdbc.update("UPDATE ai_data SET table_name='dev_patrol_record' WHERE data_id=10");
		assertThat(service.execute(c, "SELECT COUNT(*) FROM dev_device").getDataQuery().get("code"))
			.isEqualTo("DATASET_METADATA_CHANGED");
		assertThat(executions.get()).isZero();
		var next = service.begin(owner, "reader", message(78, "查询记录"));
		var revoking = new DataQueryAiChatService(metadata, (id, scope, sql) -> {
			jdbc.update("DELETE FROM ai_dataquery_access");
			return new DataQuerySqlExecutor.Rows(List.of("name"), List.of(List.of("do-not-release")));
		});
		var response = revoking.execute(next, "SELECT company_id FROM dev_patrol_record");
		assertThat(response.getDataQuery().get("status")).isEqualTo("ERROR");
		assertThat(response.getMessage()).doesNotContain("do-not-release");
	}

	@Test
	void chartStorageSurvivesServiceRecreationAndRejectsCrossUserTenantAndRevocation() throws Exception {
		modelSql("SELECT COUNT(*) FROM dev_device", 1);
		var msg = message(77, "设备总数");
		var response = rule.process(msg).blockFirst();
		service.saveChart(msg.getDataQueryContext(), "{\"series\":[]}");
		var recreated = new DataQueryAiChatService(metadata);
		assertThat(recreated.chart(owner, response.getChartId())).isEqualTo("{\"series\":[]}");
		assertThatThrownBy(() -> recreated.chart(new Identity("2", "2"), response.getChartId()))
			.hasMessage("QUERY_NOT_FOUND");
		assertThatThrownBy(() -> recreated.chart(new Identity("1", "3"), response.getChartId()))
			.hasMessage("QUERY_NOT_FOUND");
		jdbc.update("DELETE FROM ai_dataquery_access");
		assertThatThrownBy(() -> recreated.chart(owner, response.getChartId())).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void jdbcLimitsAreErrorsNotPartialResultsAndNullsAreNotInventedNames() throws Exception {
		var guard = new DataQuerySqlGuard();
		try (var c = source.getConnection()) {
			var rows = DataQuerySqlExecutor.read(c,
					guard.prepare("SELECT NULL AS name,COUNT(*) AS total FROM dev_device", Set.of("dev_device")));
			assertThat(DataQueryAiChatService.markdown(rows)).contains("空值（源数据）", "3");
			jdbc.update("INSERT INTO dev_device SELECT 'x' FROM SYSTEM_RANGE(1,1001)");
			assertThatThrownBy(
					() -> DataQuerySqlExecutor.read(c, guard.prepare("SELECT * FROM dev_device", Set.of("dev_device"))))
				.hasMessage("RESULT_LIMIT_EXCEEDED");
			jdbc.update("DELETE FROM dev_device");
			jdbc.execute("ALTER TABLE dev_device ALTER COLUMN company_id VARCHAR(10000)");
			jdbc.update("INSERT INTO dev_device VALUES(?)", "x".repeat(8193));
			assertThatThrownBy(
					() -> DataQuerySqlExecutor.read(c, guard.prepare("SELECT * FROM dev_device", Set.of("dev_device"))))
				.hasMessage("RESULT_LIMIT_EXCEEDED");
		}
	}

	@Test
	void clientJsonCannotInjectServerExecutionContextOrQueryReference() throws Exception {
		var forged = json.readValue(
				"{\"dataQueryContext\":{\"identity\":{\"tenantId\":\"1\",\"userId\":\"2\"}},\"dataQuery\":{\"queryId\":\"forged\"}}",
				ChatMessageDTO.class);
		assertThat(forged.getDataQueryContext()).isNull();
		assertThat(forged.getDataQuery()).isNull();
	}

	void published(String version, String terms) {
		var t = LocalDateTime.of(2026, 8, 1, 0, 0);
		var termsList = Arrays.asList(terms.split(","));
		try {
			jdbc.update(
					"""
							INSERT INTO ai_dataquery_rule_version(tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,created_by,created_at,
							regression_digest,validated_at,approved_by,approved_at,published_by,published_at,intent_terms_json,content_digest)
							VALUES(1,'top.patrol',?,?,?,'PUBLISHED',3,?,?,?,4,?,4,?,?,?)
							""",
					version, DEFINITION, DataQueryMetadataRepository.DEFINITION_DIGEST, t, "a".repeat(64),
					t.plusHours(1), t.plusHours(2), t.plusHours(3), json.writeValueAsString(termsList),
					DataQueryMetadataRepository.ruleContentDigest(termsList));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void publishedIntentHintsAreReloadedForNewQueriesWhileDraftsAndStaleVersionsCannotBeUsed() {
		published("r1", "巡视");
		published("r2", "巡视,巡查");
		jdbc.update("UPDATE ai_dataquery_rule_version SET state='DRAFT' WHERE rule_version='r2'");
		jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,'top.patrol','r1')");
		var before = service.begin(owner, "reader", message(77, "2026年8月巡视记录数"));
		assertThat(service.prompt(before)).contains("巡视").doesNotContain("巡视、巡查");
		jdbc.update("UPDATE ai_dataquery_rule_version SET state='PUBLISHED' WHERE rule_version='r2'");
		jdbc.update("UPDATE ai_dataquery_rule_active SET rule_version='r2'");
		assertThat(service.execute(before, "SELECT COUNT(*) FROM dev_patrol_record").getDataQuery().get("code"))
			.isEqualTo("RULE_CHANGED");
		var after = service.begin(owner, "reader", message(78, "2026年8月巡查记录数"));
		assertThat(after.rule().intentTerms()).contains("巡查");
		assertThat(service.prompt(after)).contains(String.join("、", after.rule().intentTerms()));
		assertThat(after.ruleVersion()).isEqualTo("r2");
		jdbc.update("UPDATE ai_dataquery_rule_active SET rule_version='r1'");
		assertThat(service.prompt(service.begin(owner, "reader", message(79, "巡视记录")))).doesNotContain("巡视、巡查");
		jdbc.update("UPDATE ai_dataquery_rule_active SET rule_version='r2'");
		jdbc.update("UPDATE ai_dataquery_rule_version SET state='DRAFT' WHERE rule_version='r2'");
		assertThatThrownBy(() -> service.begin(owner, "reader", message(80, "巡视记录")))
			.hasMessage("METRIC_DEFINITION_UNCONFIRMED");
		assertThat(executions.get()).isZero();
	}

	@Test
	void unmigratedAuditStorageFailsClosedBeforeModel() {
		jdbc.execute("ALTER TABLE ai_dataquery_query DROP COLUMN chart_json");
		assertThat(rule.process(message(77, "查询设备")).blockFirst().getDataQuery().get("code"))
			.isEqualTo("AI_SQL_SCHEMA_REQUIRED");
		verifyZeroInteractions(model, legacy, charts);
	}

	@Test
	void existingChartGeneratorUsesTheSameThreeSchemasAndDurableProtectedStore() throws Exception {
		for (int type = 1; type <= 3; type++) {
			modelSql("SELECT COUNT(*) FROM dev_device", type);
			var msg = message(70 + type, "设备总数图表");
			var answer = rule.process(msg).blockFirst();
			var chartModel = mock(ChatModel.class);
			var chartProvider = mock(ModelProvider.class);
			when(chartProvider.getAiJSONAssistant("synthetic-model")).thenReturn(Triple.of(chartModel, null,
					com.deepdimen.matrix.knowledge.support.constant.ModelProviderFormatEnums.OPENAI.getFormat()));
			when(chartModel.chat(any(ChatRequest.class)))
				.thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("{\"series\":[]}")).build());
			var generator = new com.deepdimen.matrix.knowledge.service.impl.AiChartGenerateServiceImpl(
					mock(com.deepdimen.matrix.knowledge.mapper.AiModelMapper.class), chartProvider);
			var beans = new StaticListableBeanFactory();
			beans.addBean("ai", service);
			ReflectionTestUtils.setField(generator, "dataQueryChats",
					beans.getBeanProvider(DataQueryAiChatService.class));
			generator.generateChart(msg, answer.getMessage());
			assertThat(generator.getChart(answer.getChartId())).isEqualTo("{\"series\":[]}");
			var request = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
			verify(chartModel).chat(request.capture());
			assertThat(request.getValue().responseFormat().jsonSchema()).isNotNull();
			login(2, 2, "reader");
			assertThatThrownBy(() -> generator.getChart(answer.getChartId())).hasMessage("QUERY_NOT_FOUND");
			login(1, 2, "reader");
		}
	}

}
