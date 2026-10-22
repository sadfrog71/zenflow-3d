package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Owned H2 history plus synthetic source rows; not a live TOP correctness claim. */
class DataQueryDrilldownScopeTest {

	DataQueryAiChatTest f;

	String sourceId;

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		f.modelSql("SELECT COUNT(*) AS n FROM dev_device", 0);
		var first = f.rule.process(f.message(80, "2026年8月巡视记录数最多的前3个部门，按公司和部门标识分别统计")).blockFirst();
		sourceId = first.getDataQuery().get("queryId").toString();
		var r = new DataQueryDrilldownTest().ranking();
		f.metadata.recordEvidence(sourceId, f.owner, "department",
				new DataQueryDepartmentDimension.Ranking(r.captured(), r.first(), sourceId, r.status()));
		clearInvocations(f.model);
	}

	@AfterEach
	void cleanup() {
		f.cleanup();
	}

	@Test
	void productionPhraseHasLastTurnInMemoryAndReusesDatesWithoutASecondModelCall() throws Exception {
		var executed = new AtomicReference<DataQuerySqlGuard.Prepared>();
		var service = new DataQueryAiChatService(f.metadata, (identity, scope, sql) -> {
			executed.set(sql);
			var rows = new ArrayList<List<Object>>();
			for (int n = 1; n <= 6; n++)
				rows.add(List.of(Integer.toString(n), "synthetic", "2026-08-01 0" + n + ":00:00", "C", "D",
						"Same name"));
			return new DataQuerySqlExecutor.Rows(DataQueryDepartmentDimension.DETAIL_COLUMNS, rows);
		});
		var c = service.begin(f.owner, "reader", f.message(81, DataQueryDrilldownRequestTest.INCIDENT));
		assertThat(f.metadata.conversationContext(c)).contains("2026年8月", sourceId);
		var result = service.tryDrilldown(c).orElseThrow();
		assertThat(result.getDataQuery()).containsEntry("status", "SUCCEEDED");
		assertThat(executed.get().sql())
			.contains("start_date >= ?", "start_date < ?", "del_flag = ?", "BINARY company_id = BINARY ?",
					"BINARY patrol_dept_id = BINARY ?", "ORDER BY start_date ASC, id ASC LIMIT 6")
			.doesNotContain("JOIN");
		assertThat(executed.get().parameters()).containsSubsequence("2026-08-01 00:00:00", "2026-09-01 00:00:00", "0",
				"C", "D");
		assertThat((List<?>) result.getDataQuery().get("columns"))
			.isEqualTo(List.of("record_id", "start_date", "patrol_dept_id"));
		assertThat((List<?>) result.getDataQuery().get("rows")).hasSize(5);
		assertThat(f.metadata.findOwnedQuery(f.owner, c.queryId()).orElseThrow().candidateJson()).contains(sourceId,
				"\"page\"");
		var next = service.begin(f.owner, "reader", f.message(82, "下一页"));
		var nextResult = service.tryDrilldown(next).orElseThrow();
		assertThat(nextResult.getDataQuery()).containsEntry("status", "SUCCEEDED");
		assertThat(nextResult.getDataQuery().get("columns")).isEqualTo(result.getDataQuery().get("columns"));
		assertThat(executed.get().sql()).contains("id > ?", "LIMIT 6");
		assertThat(executed.get().parameters()).contains("2026-08-01 00:00:00", "2026-09-01 00:00:00",
				"2026-08-01 05:00:00", "5");
		verify(f.model, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void ruleRoutesOriginalPhraseAndNeverGuessesWhenEvidenceIsMissing() {
		f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json='{}' WHERE query_id=?", sourceId);
		int before = f.executions.get();
		var result = f.rule.process(f.message(81, DataQueryDrilldownRequestTest.INCIDENT)).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("code", "DETAIL_SCOPE_UNAVAILABLE");
		assertThat(f.executions.get()).isEqualTo(before);
		verify(f.model, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void unsupportedScopeChangesDoNotSilentlyKeepOrDropFilters() {
		int before = f.executions.get();
		var result = f.rule.process(f.message(81, "看刚才第一名的原始巡视记录，改成2026年7月，最早5条")).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("code", "DETAIL_REQUEST_NEEDS_CLARIFICATION");
		assertThat(f.executions.get()).isEqualTo(before);
		verify(f.model, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void failedNewerBusinessQueryCannotResurrectOlderRanking() {
		var failed = f.service.begin(f.owner, "reader", f.message(81, "换成2026年7月的部门排名"));
		f.service.fail(failed, "QUERY_TIMEOUT");
		var result = f.rule.process(f.message(82, DataQueryDrilldownRequestTest.INCIDENT)).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("code", "DETAIL_SCOPE_UNAVAILABLE");
		verify(f.model, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void originalPhraseCannotCrossSessionOrChangedAuthorization() {
		f.jdbc.update("UPDATE ai_chat_record SET conversation_id='another-session' WHERE record_id=80");
		var result = f.rule.process(f.message(81, DataQueryDrilldownRequestTest.INCIDENT)).blockFirst();
		assertThat(result.getDataQuery()).containsEntry("code", "DETAIL_SCOPE_UNAVAILABLE");
		f.jdbc.update("UPDATE ai_chat_record SET conversation_id='shared-conversation' WHERE record_id=80");
		var current = f.service.begin(f.owner, "reader", f.message(82, DataQueryDrilldownRequestTest.INCIDENT));
		f.jdbc.update("DELETE FROM ai_dataquery_access");
		assertThat(f.service.tryDrilldown(current).orElseThrow().getDataQuery()).containsEntry("code",
				"DATASET_NOT_AUTHORIZED");
		verify(f.model, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

}
