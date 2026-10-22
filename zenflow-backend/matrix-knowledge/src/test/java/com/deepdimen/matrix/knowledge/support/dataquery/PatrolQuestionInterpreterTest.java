package com.deepdimen.matrix.knowledge.support.dataquery;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PatrolQuestionInterpreterTest {

	@Test
	void interpretsOnlyExplicitMonthDepartmentCountsAndPublishedTerms() {
		var parser = new PatrolQuestionInterpreter();
		var plan = parser.interpret("2026年8月各部门巡视记录有多少？按次数从高到低排列。", List.of("巡视"));
		assertThat(plan.from()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(plan.to()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(plan.metric()).isEqualTo("valid_patrol_count");
		assertThatThrownBy(() -> parser.interpret("2026年8月各部门巡查记录有多少？按次数从高到低排列。", List.of("巡视")))
			.hasMessage("INTENT_NOT_SUPPORTED");
		assertThat(parser.interpret("2026年8月各部门巡查记录有多少？按次数从高到低排列。", List.of("巡视", "巡查"))).isEqualTo(plan);
	}

	@Test
	void refusesOtherMetricsAmbiguousDatesSqlAndUnsafeAliases() {
		var parser = new PatrolQuestionInterpreter();
		for (String question : List.of("2026年8月各部门巡视完成率是多少", "2026年8月巡视记录数", "各部门巡视记录数", "2026年13月各部门巡视记录数",
				"2026年8月各部门巡视记录数; SELECT * FROM x", "2026年8月和9月各部门巡视记录数", "2026年8月各部门巡视记录数及设备数")) {
			assertThatThrownBy(() -> parser.interpret(question, List.of("巡视"))).isInstanceOf(DataQueryException.class);
		}
		assertThatThrownBy(() -> PatrolQuestionInterpreter.normalizedTerms(List.of("巡视", "完成率")))
			.hasMessage("UNSUPPORTED_CORRECTION");
	}

	@Test
	void acceptsEquivalentSpokenCountQuestionsWithoutChangingThePublishedMetric() {
		var parser = new PatrolQuestionInterpreter();
		var expected = parser.interpret("2026年8月各部门巡视记录数", List.of("巡视"));
		for (String question : List.of("2026年8月各部门巡视了多少次？", "2026年8月份各部门巡视了多少次?", "请问2026年8月份各个部门巡视次数，按次数从高到低排序。",
				"2026年8月各部门有效巡视记录有多少条？"))
			assertThat(parser.interpret(question, List.of("巡视"))).as(question).isEqualTo(expected);
		assertThatThrownBy(() -> parser.interpret("2026年8月各部门巡查了多少次？", List.of("巡视")))
			.hasMessage("INTENT_NOT_SUPPORTED");
	}

	@Test
	void asksForYearWithoutGuessingAndDistinguishesInvalidDates() {
		var parser = new PatrolQuestionInterpreter();
		assertThatThrownBy(() -> parser.interpret("8月份各部门巡视了多少次？", List.of("巡视"))).hasMessage("QUERY_YEAR_REQUIRED");
		assertThatThrownBy(() -> parser.interpret("2026年13月各部门巡视记录数", List.of("巡视"))).hasMessage("QUERY_DATE_INVALID");
		for (String question : List.of("8月份各部门巡视了多少次并查询设备明细", "2026年8月各部门巡视了多少次;SELECT 1", "去年8月各部门巡视了多少次",
				"2026年8月各部门巡视了多少次，只看A部门", "2026年8月和9月各部门巡视了多少次"))
			assertThatThrownBy(() -> parser.interpret(question, List.of("巡视"))).hasMessage("INTENT_NOT_SUPPORTED");
	}

}
