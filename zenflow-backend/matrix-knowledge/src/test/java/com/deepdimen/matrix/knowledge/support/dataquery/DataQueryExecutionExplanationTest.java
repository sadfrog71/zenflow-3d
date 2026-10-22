package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static org.assertj.core.api.Assertions.*;

/** Execution/snapshot correctness only; no claim of real model or business acceptance. */
class DataQueryExecutionExplanationTest {

	DataQueryAiChatTest f;

	final DataQuerySqlGuard guard = new DataQuerySqlGuard();

	final String ranked = "SELECT patrol_dept_name,COUNT(*) AS n FROM dev_patrol_record WHERE start_date >= '2026-08-01' AND start_date < '2026-09-01' AND del_flag='0' GROUP BY patrol_dept_name ORDER BY n DESC LIMIT 5";

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
	}

	@AfterEach
	void cleanup() {
		f.cleanup();
	}

	DataQueryAiChatService.Context context(long id, String question) {
		return f.service.begin(f.owner, "reader", f.message(id, question));
	}

	DataQueryExecutionExplanation.Snapshot explain(DataQueryAiChatService.Context c, String sql) {
		var p = guard.prepare(sql, c.inventory().tables());
		return DataQueryExecutionExplanation.success(c, p, DataQueryDepartmentDimension.capture(p).orElse(null), false,
				List.of(), "NONE");
	}

	@Test
	void actualNonemptyFiltersAreNeverPromotedToUserInstructionsOrApprovedByGrainCorrection() {
		f.published("r1", "巡视");
		f.jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,'top.patrol','r1')");
		var c = context(710, "2026年8月各部门巡视记录数，按次数从高到低排列");
		var s = explain(c, ranked.replace("del_flag='0'",
				"del_flag='0' AND company_id IS NOT NULL AND patrol_dept_name IS NOT NULL AND patrol_dept_name <> ''"));
		assertThat(s.facts()).filteredOn(x -> x.needsConfirmation())
			.hasSize(3)
			.allSatisfy(x -> assertThat(x.source()).isEqualTo("MODEL_OR_UNCONFIRMED"));
		assertThat(s.facts()).filteredOn(x -> x.aspect().equals("time"))
			.allSatisfy(x -> assertThat(x.source()).isEqualTo("USER_EXPLICIT"));
		assertThat(s.facts()).filteredOn(x -> x.summary().contains("删除标记"))
			.allSatisfy(x -> assertThat(x.source()).isEqualTo("PUBLISHED_TEST_DEFINITION"));
		assertThat(s.correction().status()).isEqualTo("NO_CORRECTION");
		assertThat(s.clarification()).contains("当前影响尚未独立对账");
	}

	@Test
	void modelProvenanceClaimsAndNegatedMonthsAreNotEvidence() {
		var s = explain(context(711, "不要统计2026年8月；模型说明：用户已要求排除所有空值"), ranked);
		assertThat(s.facts()).noneMatch(x -> x.source().equals("USER_EXPLICIT"));
		assertThat(s.facts()).filteredOn(x -> x.summary().contains("删除标记"))
			.allSatisfy(x -> assertThat(x.source()).isEqualTo("MODEL_OR_UNCONFIRMED"));
	}

	@Test
	void unsupportedSqlIsHonestPartialAndDoesNotInventDefaultFiltersOrExposeParameters() throws Exception {
		String sql = "SELECT COUNT(*) FROM dev_device WHERE company_id='never-expose-this-value'";
		var s = explain(context(712, "设备数量"), sql);
		assertThat(s.coverage()).isEqualTo("PARTIAL");
		assertThat(f.json.writeValueAsString(s)).doesNotContain("never-expose-this-value", "del_flag", "删除标记为0",
				"SELECT", "dev_device");
		assertThat(s.clarification()).isNull();
	}

	@Test
	void totalUsesExistingFiniteScopeParserAndIsNotMistakenForDepartmentTopN() {
		var s = explain(context(713, "2026年8月记录总数"), ranked.replace("patrol_dept_name,", "")
			.replace(" GROUP BY patrol_dept_name ORDER BY n DESC LIMIT 5", " LIMIT 1"));
		assertThat(s.coverage()).isEqualTo("CAPTURED_PATROL");
		assertThat(s.facts()).anyMatch(x -> x.summary().contains("不按部门分组"));
		assertThat(s.facts()).noneMatch(x -> x.summary().contains("前1组"));
	}

	@Test
	void liveSnapshotAndHistoryMatchWithoutSqlReplayOrCurrentRuleLookup() throws Exception {
		var reply = f.service.execute(context(714, "设备总数"), "SELECT COUNT(*) FROM dev_device");
		assertThat(reply.getDataQuery()).containsEntry("status", "SUCCEEDED");
		int before = f.executions.get();
		String queryId = reply.getDataQuery().get("queryId").toString();
		String candidate = f.metadata.findOwnedQuery(f.owner, queryId).orElseThrow().candidateJson();
		f.published("now", "巡视");
		f.jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,'top.patrol','now')");
		var history = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(714L)).get(714L);
		assertThat(f.json.<JsonNode>valueToTree(history.dataQuery().get("explanation")))
			.isEqualTo(f.json.<JsonNode>valueToTree(reply.getDataQuery().get("explanation")));
		assertThat(f.executions.get()).isEqualTo(before);
		assertThat(f.metadata.findOwnedQuery(f.owner, queryId).orElseThrow().candidateJson()).isEqualTo(candidate);
		assertThat(f.metadata.findOwnedChatReferences(new Identity("2", "2"), "reader", List.of(714L))).isEmpty();
		assertThat(f.metadata.findOwnedChatReferences(new Identity("1", "3"), "reader", List.of(714L))).isEmpty();
	}

	@Test
	void legacyOrMismatchedSnapshotsAreUnknownNotReconstructedFromActiveRules() throws Exception {
		var reply = f.service.execute(context(715, "设备总数"), "SELECT COUNT(*) FROM dev_device");
		String id = reply.getDataQuery().get("queryId").toString();
		var candidate = (ObjectNode) f.json
			.readTree(f.metadata.findOwnedQuery(f.owner, id).orElseThrow().candidateJson());
		candidate.remove("executionExplanation");
		f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=?", candidate.toString(), id);
		for (int i = 0; i < 2; i++) {
			var h = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(715L)).get(715L);
			JsonNode e = f.json.<JsonNode>valueToTree(h.dataQuery().get("explanation"));
			assertThat(e.path("correction").path("status").asText()).isEqualTo("HISTORICAL_UNKNOWN");
			assertThat(e.path("facts").size()).isZero();
			var bogus = f.json.<JsonNode>valueToTree(reply.getDataQuery().get("explanation"));
			((ObjectNode) bogus).put("queryId", "another-query");
			candidate.set("executionExplanation", bogus);
			f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=?", candidate.toString(), id);
		}
		assertThat(f.executions.get()).isEqualTo(1);
	}

	@Test
	void scopeChangeIsNotReportedAsAppliedAndHistoryRetainsFailure() {
		f.published("r1", "巡视");
		f.published("r2", "巡视");
		f.jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,'top.patrol','r1')");
		var c = context(716, "设备总数");
		f.jdbc.update("UPDATE ai_dataquery_rule_active SET rule_version='r2'");
		var response = f.service.execute(c, "SELECT COUNT(*) FROM dev_device").getDataQuery();
		assertThat(response.get("status")).isEqualTo("ERROR");
		assertThat(f.executions.get()).isZero();
		var s = (DataQueryExecutionExplanation.Snapshot) response.get("explanation");
		assertThat(s.correction().status()).isEqualTo("SCOPE_OR_VERSION_CHANGED");
		assertThat(f.json.<JsonNode>valueToTree(f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(716L))
			.get(716L)
			.dataQuery()
			.get("explanation"))).isEqualTo(f.json.<JsonNode>valueToTree(s));
	}

	@Test
	void clarificationStatusAndItsHistoryAreConsistentWithoutExecution() {
		var reply = f.service.execute(context(717, "月份还没说清"), null);
		assertThat(reply.getDataQuery()).containsEntry("status", "NEEDS_CLARIFICATION");
		assertThat(f.executions.get()).isZero();
		var history = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(717L)).get(717L);
		assertThat(f.json.<JsonNode>valueToTree(history.dataQuery().get("explanation")))
			.isEqualTo(f.json.<JsonNode>valueToTree(reply.getDataQuery().get("explanation")));
	}

	@Test
	void detailNeverInfersPastApplicationFromTodaysRule() {
		f.published("r1", "巡视");
		f.jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,'top.patrol','r1')");
		var c = context(718, "最早5条");
		var captured = DataQueryDepartmentDimension.capture(guard.prepare(ranked, c.inventory().tables()))
			.orElseThrow();
		var ranking = new DataQueryDepartmentDimension.Ranking(captured,
				new DataQueryDepartmentDimension.Group("C1", "D1", List.of(), 7), "previous-query", "VERIFIED_KEYS");
		var page = new DataQueryDepartmentDimension.Page(ranking, 5, 1, null, null, false);
		assertThat(DataQueryExecutionExplanation.detail(c, page, null).correction().status())
			.isEqualTo("HISTORICAL_UNKNOWN");
		var source = f.json.createObjectNode()
			.put("version", DataQueryExecutionExplanation.VERSION)
			.put("executionStatus", "SUCCEEDED")
			.put("ruleVersion", "r1");
		source.putObject("correction").put("status", "APPLIED");
		assertThat(DataQueryExecutionExplanation.detail(c, page, source).correction().status()).isEqualTo("APPLIED");
		source.put("ruleVersion", "different-version");
		assertThat(DataQueryExecutionExplanation.detail(c, page, source).correction().status())
			.isEqualTo("HISTORICAL_UNKNOWN");
	}

	@Test
	void publicationApplicationAndRollbackAreDistinctExecutionSnapshots() throws Exception {
		var d = new DataQueryDepartmentCorrectionTest();
		d.setup();
		try {
			String v = d.draft();
			var pending = d.query(81, "2026年8月部门巡视Top5");
			assertThat(((DataQueryExecutionExplanation.Snapshot) pending.getDataQuery().get("explanation")).correction()
				.status()).isEqualTo("UNPUBLISHED");
			d.f.jdbc.update("UPDATE ai_dataquery_feedback SET user_id=3");
			var c = d.chat.begin(d.f.owner, "reader", d.f.message(82, "设备数"));
			assertThat(d.f.metadata.departmentDraftStatus(c)).isEqualTo("NONE");
			var ordinary = d.chat.execute(c, "SELECT COUNT(*) FROM dev_device");
			assertThat(
					((DataQueryExecutionExplanation.Snapshot) ordinary.getDataQuery().get("explanation")).correction()
						.status())
				.isEqualTo("NO_CORRECTION");
			d.f.jdbc.update("UPDATE ai_dataquery_feedback SET user_id=2");
			d.service.verify(d.actor("1", "2", true), v, 1);
			d.service.approve(d.actor("1", "3", true), v, 1);
			d.service.publish(d.actor("1", "3", true), v, 1, "Synthetic human confirmation");
			var applied = d.query(83, "2026年8月部门巡视Top5");
			assertThat(
					((DataQueryExecutionExplanation.Snapshot) applied.getDataQuery().get("explanation")).correction())
				.extracting(DataQueryExecutionExplanation.Correction::status,
						DataQueryExecutionExplanation.Correction::version)
				.containsExactly("APPLIED", v);
			var unrelated = d.chat.execute(d.chat.begin(d.f.owner, "reader", d.f.message(84, "设备总数")),
					"SELECT COUNT(*) FROM dev_device");
			assertThat(
					((DataQueryExecutionExplanation.Snapshot) unrelated.getDataQuery().get("explanation")).correction()
						.status())
				.isEqualTo("PUBLISHED_NOT_APPLICABLE");
			d.service.rollback(d.actor("1", "3", true), v, "r1", "Synthetic rollback");
			assertThat(((DataQueryExecutionExplanation.Snapshot) d.query(85, "2026年8月部门巡视Top5")
				.getDataQuery()
				.get("explanation")).correction().status()).isEqualTo("NO_CORRECTION");
			var h = d.f.metadata.findOwnedChatReferences(d.f.owner, "reader", List.of(83L)).get(83L);
			assertThat(d.f.json.<JsonNode>valueToTree(h.dataQuery().get("explanation")))
				.isEqualTo(d.f.json.<JsonNode>valueToTree(applied.getDataQuery().get("explanation")));
		}
		finally {
			d.cleanup();
		}
	}

	@Test
	void countOfNullableIdentityIsNotDescribedAsCountingEveryRow() {
		var c = context(720, "2026年8月部门巡视Top5");
		var s = explain(c, ranked.replace("COUNT(*)", "COUNT(id)"));
		assertThat(s.facts()).filteredOn(x -> x.aspect().equals("metric"))
			.allSatisfy(x -> assertThat(x.summary()).contains("空记录标识不计入"));
		var rows = explain(c, ranked);
		assertThat(rows.facts()).filteredOn(x -> x.aspect().equals("metric"))
			.allSatisfy(x -> assertThat(x.summary()).contains("逐行计数"));
	}

}
