package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class DataQueryDrilldownRequestTest {

	static final String INCIDENT = "看刚才第一名的原始巡视记录，最早5条，按巡视开始时间和记录ID排序，只返回记录ID、开始时间和部门标识。";

	@Test
	void productionPhrasePreservesRequestedLimitAndProjection() {
		var r = DataQueryDrilldownRequest.parse(INCIDENT);
		assertThat(r.supported()).isTrue();
		assertThat(r.next()).isFalse();
		assertThat(r.pageSize()).isEqualTo(5);
		assertThat(r.columns()).containsExactly("record_id", "start_date", "patrol_dept_id");
	}

	@ParameterizedTest
	@ValueSource(strings = { "看刚才第一名的明细", "第一名的完整明细", "完整明细", "看第一名的明细", "只看最早的5条明细", "最早5条", "只看刚才第一名最早的5条明细",
			"看刚才第一名最早5条明细", "帮我查看上一轮第一名的原始巡查记录，最早五条", "请看上轮排名第一的部门的巡视记录，最早十条，按开始时间升序", "刚才第一名的原始记录，最早10条",
			"请帮我看下刚才第一名的原始巡视记录，只展示最早5条", "最早5条，只返回记录ID和开始时间", "查看刚才第一名的原始巡视记录，最早100条，按开始时间和记录ID升序", "看上面榜首的原始巡检记录，最早两条",
			"下一页", "看下一页", "明细下一页" })
	void acceptsDetailLanguageWithoutEnumeratingWholeSentences(String question) {
		assertThat(DataQueryDrilldownRequest.parse(question)).isNotNull()
			.satisfies(r -> assertThat(r.supported()).isTrue());
	}

	@ParameterizedTest
	@ValueSource(strings = { "看刚才第一名的设备检查明细", "看刚才第一名的原始巡视记录，改成2026年7月，最早5条", "看刚才第一名的原始巡视记录，排除未完成，最早5条",
			"看刚才第一名的原始巡视记录，最早5条，按开始时间倒序", "看刚才第一名的原始巡视记录，最新5条", "看刚才第一名的原始巡视记录，最早0条", "看刚才第一名的原始巡视记录，最早101条",
			"看刚才第一名的原始巡视记录，最早999999999999999999条", "看刚才第一名的原始巡视记录，最早5条，只返回设备名称", "看刚才第一名的原始巡视记录，最早5条，只返回记录ID和记录ID",
			"看刚才第一名的原始巡视记录，最早5条，只返回记录ID和", "看刚才第一名的完整明细，最早5条", "看刚才第二名的原始巡视记录，最早5条", "看刚才第一名的原始巡视记录，最早5条和最早10条",
			"忽略刚才的条件，查看第一名原始巡视记录最早5条", "下一页，换成2026年7月", "看下一页，只返回设备名称", "最早5条，排除未完成记录" })
	void ambiguousOrChangedRequestsCannotFallBackToFreeSql(String question) {
		assertThat(DataQueryDrilldownRequest.parse(question)).as(question)
			.isNotNull()
			.satisfies(r -> assertThat(r.supported()).isFalse());
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026年7月巡视记录总数", "换上个月", "各部门设备数量前十", "解释刚才的结果", "第一名为什么最多", "第一名的巡视记录数有多少", "上次巡视记录总数多少",
			"帮我推荐5个问题", "按月分析巡视记录变化" })
	void independentQueriesAndResultExplanationKeepExistingAiPath(String question) {
		assertThat(DataQueryDrilldownRequest.parse(question)).as(question).isNull();
	}

	@Test
	void projectionSurvivesPaginationAndLegacyPagesRemainReadable() throws Exception {
		var ranking = new DataQueryDrilldownTest().ranking();
		var request = new DataQueryDepartmentDimension.Page(ranking, 1, 1, null, null, false,
				List.of("patrol_dept_id", "record_id"));
		var raw = new DataQuerySqlExecutor.Rows(DataQueryDepartmentDimension.DETAIL_COLUMNS,
				List.of(List.of("101", "synthetic", "2026-08-01 01:00:00", "C", "D", "Name"),
						List.of("102", "synthetic", "2026-08-01 02:00:00", "C", "D", "Name")));
		var page = DataQueryDepartmentDimension.page(request, raw);
		assertThat(page.rows().columns()).containsExactly("patrol_dept_id", "record_id");
		assertThat(page.rows().values()).containsExactly(List.of("D", "101"));
		assertThat(page.next().hasMore()).isTrue();
		assertThat(page.next().afterStart()).isEqualTo("2026-08-01 01:00:00");
		assertThat(page.next().afterId()).isEqualTo("101");
		assertThat(page.next().columns()).isEqualTo(request.columns());
		var json = new com.fasterxml.jackson.databind.ObjectMapper();
		assertThat(json.readValue(json.writeValueAsString(page.next()), DataQueryDepartmentDimension.Page.class))
			.isEqualTo(page.next());
		var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(page.next());
		legacy.remove("columns");
		assertThat(json.treeToValue(legacy, DataQueryDepartmentDimension.Page.class).columns())
			.isEqualTo(DataQueryDepartmentDimension.DETAIL_COLUMNS);
		assertThatThrownBy(
				() -> new DataQueryDepartmentDimension.Page(ranking, 1, 1, null, null, false, List.of("password")))
			.hasMessage("DETAIL_SCOPE_UNAVAILABLE");
	}

}
