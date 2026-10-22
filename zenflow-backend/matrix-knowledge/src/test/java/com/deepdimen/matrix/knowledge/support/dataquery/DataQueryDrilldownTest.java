package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class DataQueryDrilldownTest {

	final DataQuerySqlGuard guard = new DataQuerySqlGuard();

	String sql = "SELECT patrol_dept_id AS dept,patrol_dept_name AS name,COUNT(*) AS n FROM dev_patrol_record WHERE start_date >= '2026-08-01' AND start_date < '2026-09-01' AND del_flag='0' GROUP BY patrol_dept_id,patrol_dept_name ORDER BY n DESC LIMIT 10";

	DataQuerySqlGuard.Prepared prepared() {
		return guard.prepare(sql, Set.of("dev_patrol_record"));
	}

	DataQueryDepartmentDimension.Ranking ranking() {
		var c = DataQueryDepartmentDimension.capture(prepared()).orElseThrow();
		var result = new DataQuerySqlExecutor.Rows(
				List.of("dept", "name", "n", "c", "cc", "d", "dc", "count", "gd", "gn"),
				List.of(List.of("D", "Same name", "12", "C", "1", "D", "1", "12", "D", "Same name")));
		var ranked = DataQueryDepartmentDimension.materialize("source-query", c, result);
		assertThat(ranked.rows().columns()).containsExactly("dept", "name", "n");
		return ranked.ranking();
	}

	@Test
	void retainsActualScopeAndAddsKeysWithoutChangingGroupWhereOrLimit() {
		var p = prepared();
		var c = DataQueryDepartmentDimension.capture(p).orElseThrow();
		var instrumented = DataQueryDepartmentDimension.instrument(p, c);
		assertThat(c.scope().from()).isEqualTo("2026-08-01");
		assertThat(c.scope().topN()).isEqualTo(10);
		assertThat(instrumented.parameters()).isEqualTo(p.parameters());
		assertThat(instrumented.sql()).contains("COUNT(DISTINCT BINARY company_id)",
				"GROUP BY patrol_dept_id, patrol_dept_name", "ORDER BY n DESC");
		var r = ranking();
		assertThat(r.first().companyId()).isEqualTo("C");
		assertThat(r.first().departmentId()).isEqualTo("D");
		assertThat(r.first().count()).isEqualTo(12);
	}

	@Test
	void refusesSilentLossOfBusinessFiltersJoinMetricAndSorting() {
		for (String changed : List.of(sql.replace("del_flag='0'", "del_flag='0' AND status=1"),
				sql.replace("ORDER BY n DESC", "ORDER BY name DESC"),
				sql.replace("COUNT(*)", "COUNT(DISTINCT patrol_dept_id)"),
				sql.replace("FROM dev_patrol_record", "FROM dev_patrol_record JOIN dev_device ON 1=1"),
				sql.replace("GROUP BY patrol_dept_id,patrol_dept_name",
						"GROUP BY patrol_dept_id,patrol_dept_name HAVING COUNT(*)>2"),
				sql.replace("'2026-08-01'", "'2026-08-02'"))) {
			assertThat(DataQueryDepartmentDimension
				.capture(guard.prepare(changed, Set.of("dev_patrol_record", "dev_device")))).as(changed).isEmpty();
		}
	}

	@Test
	void preservesOriginalNonNullPredicatesAndHasStableKeysetPagination() throws Exception {
		var p = guard.prepare(sql.replace("del_flag='0'", "del_flag='0' AND patrol_dept_name IS NOT NULL"),
				Set.of("dev_patrol_record"));
		assertThat(DataQueryDepartmentDimension.capture(p).orElseThrow().scope().nonNullFields())
			.containsExactly("patrol_dept_name");
		var page = new DataQueryDepartmentDimension.Page(ranking(), 5, 2, "2026-08-01 10:00:00", "9007199254740993",
				false);
		var q = DataQueryDepartmentDimension.details(page);
		assertThat(q.sql())
			.contains("ORDER BY start_date ASC, id ASC LIMIT 6", "id > ?", "BINARY company_id = BINARY ?")
			.doesNotContain("JOIN", "OFFSET", "Same name");
		assertThat(q.parameters()).contains("C", "D", "Same name", "9007199254740993");
		var json = new ObjectMapper();
		assertThat(json.readValue(json.writeValueAsString(page), DataQueryDepartmentDimension.Page.class))
			.isEqualTo(page);
	}

	@Test
	void ambiguousPhysicalKeysCannotBecomeAFirstDepartment() {
		var c = DataQueryDepartmentDimension.capture(prepared()).orElseThrow();
		var result = new DataQuerySqlExecutor.Rows(
				List.of("dept", "name", "n", "c", "cc", "d", "dc", "count", "gd", "gn"),
				List.of(List.of("D", "same", "12", "C", "2", "D", "1", "12", "D", "same")));
		var r = DataQueryDepartmentDimension.materialize("q", c, result).ranking();
		assertThat(r.status()).isEqualTo("AMBIGUOUS_GRAIN");
		assertThatThrownBy(() -> DataQueryDepartmentDimension
			.details(new DataQueryDepartmentDimension.Page(r, 50, 1, null, null, false)))
			.hasMessage("DETAIL_SCOPE_UNAVAILABLE");
	}

	@Test
	void recognizedReferenceDoesNotCallModelWithoutOwnedEvidenceAndNormalQueryStillWorks() throws Exception {
		var f = new DataQueryAiChatTest();
		f.setup();
		try {
			var result = f.rule.process(f.message(80, "看刚才第一名的明细")).blockFirst();
			assertThat(result.getDataQuery().get("code")).isEqualTo("DETAIL_SCOPE_UNAVAILABLE");
			org.mockito.Mockito.verify(f.model, org.mockito.Mockito.never())
				.chat(org.mockito.Mockito.any(dev.langchain4j.model.chat.request.ChatRequest.class));
			assertThat(f.executions).hasValue(0);
			f.modelSql("SELECT COUNT(*) AS n FROM dev_device", 0);
			assertThat(f.rule.process(f.message(81, "设备多少个")).blockFirst().getDataQuery().get("status"))
				.isEqualTo("SUCCEEDED");
		}
		finally {
			f.cleanup();
		}
	}

	@Test
	void onlyOwnedUnchangedNearestSuccessfulTurnCanSupplyKeys() throws Exception {
		var f = new DataQueryAiChatTest();
		f.setup();
		try {
			f.modelSql("SELECT COUNT(*) AS n FROM dev_device", 0);
			var first = f.rule.process(f.message(80, "部门统计")).blockFirst();
			String id = first.getDataQuery().get("queryId").toString();
			f.metadata.recordEvidence(id, f.owner, "department", ranking());
			var c = f.service.begin(f.owner, "reader", f.message(81, "看刚才第一名的明细"));
			assertThat(f.metadata.drilldownReference(c)).isNotNull();
			for (String mutation : List.of("UPDATE ai_chat_record SET conversation_id='other' WHERE record_id=80",
					"UPDATE ai_dataquery_query SET user_id=3 WHERE query_id='" + id + "'",
					"UPDATE ai_dataquery_query SET tenant_id=2 WHERE query_id='" + id + "'",
					"UPDATE ai_dataquery_query SET scope_revision='old' WHERE query_id='" + id + "'")) {
				f.jdbc.update(mutation);
				assertThat(f.metadata.drilldownReference(c)).as(mutation).isNull();
				f.jdbc.update("UPDATE ai_chat_record SET conversation_id='shared-conversation' WHERE record_id=80");
				f.jdbc.update("UPDATE ai_dataquery_query SET user_id=2,tenant_id=1,scope_revision=? WHERE query_id=?",
						c.authorization().revision(), id);
			}
			f.jdbc.update("DELETE FROM ai_dataquery_access");
			assertThat(f.service.tryDrilldown(c).orElseThrow().getDataQuery().get("code"))
				.isEqualTo("DATASET_NOT_AUTHORIZED");
		}
		finally {
			f.cleanup();
		}
	}

	@Test
	void preservesObservedEmptyNameInequalityAndLegacyScope() throws Exception {
		var p = guard.prepare(
				sql.replace("del_flag='0'", "del_flag='0' AND patrol_dept_name IS NOT NULL AND patrol_dept_name <> ''"),
				Set.of("dev_patrol_record"));
		var capture = DataQueryDepartmentDimension.capture(p);
		assertThat(capture).isPresent();
		var scope = capture.orElseThrow().scope();
		assertThat(DataQueryDepartmentDimension.capture(guard.prepare(
				sql.replace("del_flag='0'", "del_flag='0' AND patrol_dept_name != ''"), Set.of("dev_patrol_record"))))
			.isPresent();
		var mapper = new ObjectMapper();
		assertThat(mapper.valueToTree(scope).path("nonEmptyNameFilter").asBoolean()).isTrue();
		var compiled = new PatrolQueryCompiler().compileDepartment(scope);
		assertThat(compiled.sql()).contains("patrol_dept_name IS NOT NULL", "patrol_dept_name <> ?");
		assertThat(compiled.parameters()).containsExactly("2026-08-01 00:00:00", "2026-09-01 00:00:00", "0", "");
		var ranked = new DataQueryDepartmentDimension.Ranking(capture.get(), ranking().first(), "q", "VERIFIED_KEYS");
		var detail = DataQueryDepartmentDimension
			.details(new DataQueryDepartmentDimension.Page(ranked, 5, 1, null, null, false));
		assertThat(detail.sql()).contains("patrol_dept_name <> ?");
		assertThat(detail.parameters().get(3)).isEqualTo("");
		assertThat(mapper.readValue(mapper.writeValueAsString(scope), DataQueryDepartmentDimension.Scope.class))
			.isEqualTo(scope);
		var node = mapper.createObjectNode().put("from", "2026-08-01").put("to", "2026-09-01").put("topN", 5);
		node.putArray("nonNullFields");
		var legacy = mapper.treeToValue(node, DataQueryDepartmentDimension.Scope.class);
		assertThat(new PatrolQueryCompiler().compileDepartment(legacy).parameters()).hasSize(3);
	}

	@Test
	void rejectsOtherInequalitiesFunctionsAndBooleanOr() {
		for (String condition : List.of("patrol_dept_name <> 'some name'", "company_id <> ''",
				"TRIM(patrol_dept_name) <> ''", "patrol_dept_name <> '' OR status=1"))
			assertThat(DataQueryDepartmentDimension.capture(guard.prepare(
					sql.replace("del_flag='0'", "del_flag='0' AND (" + condition + ")"), Set.of("dev_patrol_record"))))
				.isEmpty();
	}

}
