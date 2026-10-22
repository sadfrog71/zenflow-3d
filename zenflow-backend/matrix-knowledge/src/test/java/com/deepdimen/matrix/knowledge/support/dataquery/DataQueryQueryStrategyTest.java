package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic fixtures; no claim about live TOP department identities or names. */
class DataQueryQueryStrategyTest {

	DataQueryAiChatTest f;

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var c = f.source.getConnection()) {
			org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,
					new org.springframework.core.io.FileSystemResource("../docs/dataquery/investigation-schema.sql"));
		}
		f.metadata.investigations(
				new DataQueryQueryEvidence(f.source, f.json, f.clock, Base64.getEncoder().encodeToString(new byte[32])),
				null);
	}

	@AfterEach
	void close() {
		f.cleanup();
	}

	@Test
	void topTenKeepsOriginalFiltersAndMissingGroupsDespiteChangedModelCandidate() {
		f.jdbc.update("INSERT INTO dev_device VALUES(NULL),(NULL),(NULL),('blocked')");
		var source = f.service.begin(f.owner, "reader", f.message(1400, "各责任部门管理的设备数量分别是多少？"));
		var first = f.service.execute(source,
				"SELECT company_id AS responsible_dept_id, COUNT(*) AS device_count FROM dev_device WHERE company_id!='blocked' OR company_id IS NULL GROUP BY company_id ORDER BY device_count DESC");
		assertThat(first.getDataQuery()).containsEntry("status", "SUCCEEDED");
		var current = f.service.begin(f.owner, "reader", f.message(1401, "各责任部门管理的设备数量最多的前十"));
		var second = f.service.execute(current,
				"SELECT company_id AS responsible_dept_id,COUNT(*) AS device_count FROM dev_device WHERE company_id IS NOT NULL AND company_id!='A' GROUP BY company_id ORDER BY device_count DESC LIMIT 10");
		assertThat(second.getDataQuery()).containsEntry("status", "SUCCEEDED").containsEntry("rowCount", 3);
		assertThat(second.getDataQuery().get("rows")).isEqualTo(first.getDataQuery().get("rows"));
		assertThat((Map<String, Object>) second.getDataQuery().get("scopeReuse"))
			.containsEntry("sourceQueryId", source.queryId())
			.containsEntry("scopePreserved", true);
		var saved = f.metadata.exampleSource(f.owner, current.queryId());
		assertThat(saved.query().parameters()).contains("blocked").doesNotContain("A");
		assertThat(saved.query().sql()).doesNotContain("IS NOT NULL");
		assertThat(f.executions).hasValue(2);
	}

	@Test
	void shortLimitFollowupReusesFiltersButExplicitNewFilterIsANewQuery() {
		var first = f.service.begin(f.owner, "reader", f.message(1410, "列出A公司的设备"));
		f.service.execute(first, "SELECT company_id FROM dev_device WHERE company_id='A' ORDER BY company_id");
		var second = f.service.begin(f.owner, "reader", f.message(1411, "只看前一条"));
		var result = f.service.execute(second, "SELECT company_id FROM dev_device WHERE company_id='B' LIMIT 1");
		assertThat(result.getDataQuery()).containsEntry("status", "SUCCEEDED").containsEntry("rowCount", 1);
		assertThat(result.getDataQuery().get("rows")).isEqualTo(List.of(List.of("A")));
		var third = f.service.begin(f.owner, "reader", f.message(1412, "只看B公司，前十"));
		assertThat(f.service.execute(third, "SELECT company_id FROM dev_device WHERE company_id='B' LIMIT 10")
			.getDataQuery()).containsEntry("rows", List.of(List.of("B"))).doesNotContainKey("scopeReuse");
	}

	@Test
	void expiredOrDifferentConversationCannotSupplyFollowupScope() {
		var first = f.service.begin(f.owner, "reader", f.message(1420, "列出设备"));
		f.service.execute(first, "SELECT company_id FROM dev_device WHERE company_id='A'");
		f.jdbc.update("UPDATE ai_dataquery_query_evidence SET expires_at=?",
				java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
		var second = f.service.begin(f.owner, "reader", f.message(1421, "前十条"));
		assertThat(f.service.execute(second, "SELECT company_id FROM dev_device LIMIT 10").getDataQuery())
			.containsEntry("code", "FOLLOWUP_CONTEXT_UNAVAILABLE")
			.containsEntry("status", "NEEDS_CLARIFICATION")
			.doesNotContainKey("rows");
		var message = f.message(1422, "前十条");
		f.jdbc.update("UPDATE ai_chat_record SET conversation_id='different' WHERE record_id=1422");
		var third = f.service.begin(f.owner, "reader", message);
		assertThat(f.service.execute(third, "SELECT company_id FROM dev_device LIMIT 10").getDataQuery())
			.containsEntry("code", "FOLLOWUP_CONTEXT_UNAVAILABLE");
		assertThat(f.executions).hasValue(1);
	}

	@Test
	void groupedPreviewReturnsCompleteCountsAndOnlyTrimsTheFinalGroups() {
		f.jdbc.update("DELETE FROM dev_device");
		for (int i = 1; i <= 15; i++)
			for (int j = 0; j < i; j++)
				f.jdbc.update("INSERT INTO dev_device VALUES(?)", "C" + i);
		var c = f.service.begin(f.owner, "reader", f.message(1430, "按公司统计设备数量"));
		var result = f.service.execute(c,
				"SELECT company_id,COUNT(*) AS device_count FROM dev_device GROUP BY company_id ORDER BY device_count DESC");
		assertThat(result.getDataQuery()).containsEntry("rowCount", 10).containsEntry("status", "SUCCEEDED");
		assertThat(((List<List<Object>>) result.getDataQuery().get("rows")).get(0)).containsExactly("C15", "15");
		assertThat((Map<String, Object>) result.getDataQuery().get("resultPreview")).containsEntry("kind", "GROUPS")
			.containsEntry("hasMore", true)
			.containsEntry("aggregateInputLimited", false);
		assertThat(result.getMessage()).contains("按原筛选范围完整计算");
	}

	@Test
	void budgetsAreClassifiedNotGloballyUnlimitedAndTlsRemainsStrict() {
		var guard = new DataQuerySqlGuard();
		var tables = Set.of("dev_device", "dev_patrol_record");
		assertThat(DataQuerySqlExecutor
			.budget(guard.prepare("SELECT company_id,COUNT(*) FROM dev_device GROUP BY company_id", tables)))
			.isEqualTo(60);
		assertThat(DataQuerySqlExecutor.budget(guard.prepare("SELECT company_id FROM dev_device", tables)))
			.isEqualTo(30);
		assertThat(DataQuerySqlExecutor.budget(guard.prepare(
				"SELECT COUNT(*) FROM dev_device d JOIN dev_patrol_record p ON d.company_id=p.company_id", tables)))
			.isEqualTo(30);
		try (var pool = ApprovedDatasourceQueryExecutor
			.newReaderPool(new DataQueryMetadataRepository.ReaderCredentials(f.url, "reader", "synthetic-only"), 60)) {
			assertThat(pool.getQueryTimeout()).isEqualTo(60);
			assertThat(pool.getConnectionInitSqls()).containsExactly("SET SESSION max_execution_time = 60000");
			assertThat(pool.getConnectProperties()).containsEntry("socketTimeout", "65000")
				.containsEntry("sslMode", "VERIFY_IDENTITY")
				.containsEntry("allowMultiQueries", "false");
		}
	}

}
