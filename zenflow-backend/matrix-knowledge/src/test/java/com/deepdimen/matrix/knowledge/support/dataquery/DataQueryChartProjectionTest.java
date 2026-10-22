package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DataQueryChartProjectionTest {

	private Map<String, Object> result(List<List<Object>> rows) {
		return Map.of("status", "SUCCEEDED", "columns", List.of("部门", "次数"), "rows", rows);
	}

	@Test
	void allThreeTypesPreserveEveryValueAndRowEvenForDuplicateAndMissingDepartmentNames() throws Exception {
		var rows = result(List.of(List.of("同名部门", "1240"), List.of("同名部门", "830"), Arrays.asList(null, "3")));
		for (String type : List.of("1", "2", "3")) {
			var chart = new ObjectMapper().readTree(DataQueryChartProjection.fromResult(rows, type).orElseThrow());
			var series = chart.path("series").get(0);
			assertThat(series.path("data").size()).isEqualTo(3);
			if ("2".equals(type)) {
				assertThat(series.path("data").get(0).path("value").asInt()).isEqualTo(1240);
				assertThat(series.path("data").get(1).path("value").asInt()).isEqualTo(830);
				assertThat(series.path("data").get(2).path("value").asInt()).isEqualTo(3);
				assertThat(series.path("data").get(0).path("name").asText()).contains("第1行");
				assertThat(series.path("data").get(1).path("name").asText()).contains("第2行");
				assertThat(series.path("stillShowZeroSum").asBoolean()).isFalse();
			}
			else {
				assertThat(series.path("data").toString()).isEqualTo("[1240,830,3]");
				assertThat(chart.path("xAxis").path("data").get(2).asText()).contains("空值（源数据）", "第3行");
			}
		}
	}

	@Test
	void keepsZeroNullAndNegativeSeparateAndDoesNotInventAPieShare() throws Exception {
		var rows = result(List.of(List.of("A", "0"), Arrays.asList("B", null), List.of("C", "-2.5")));
		var chart = new ObjectMapper().readTree(DataQueryChartProjection.fromResult(rows, "3").orElseThrow());
		assertThat(chart.path("series").get(0).path("data").toString()).isEqualTo("[0,null,-2.5]");
		assertThatThrownBy(() -> DataQueryChartProjection.fromResult(rows, "2")).hasMessage("CHART_DATA_NOT_SUITABLE");
	}

	@Test
	void doesNotRoundBigintsOrHighPrecisionDecimalsToMakeAChart() {
		for (String value : List.of("9007199254740993", "1.234567890123456789"))
			assertThatThrownBy(() -> DataQueryChartProjection.fromResult(result(List.of(List.of("A", value))), "3"))
				.hasMessage("CHART_NUMBER_NOT_REPRESENTABLE");
	}

	@Test
	void unsupportedShapesRemainOnTheOriginalChartPathAndEmptyOrFailedQueriesAreNotProjected() {
		assertThat(DataQueryChartProjection.fromResult(null, "3")).isEmpty();
		assertThat(DataQueryChartProjection.fromResult(result(List.of()), "3")).isEmpty();
		assertThat(DataQueryChartProjection.fromResult(result(List.of(List.of("A", "not numeric"))), "3")).isEmpty();
		assertThat(DataQueryChartProjection.fromResult(Map.of("status", "ERROR"), "3")).isEmpty();
		assertThat(DataQueryChartProjection.fromResult(result(List.of(List.of("A", "1"))), null)).isEmpty();
		assertThat(DataQueryChartProjection.fromResult(Map.of("status", "SUCCEEDED", "columns",
				List.of("a", "b", "count"), "rows", List.of(List.of("A", "B", "1"))), "3"))
			.isEmpty();
	}

}
