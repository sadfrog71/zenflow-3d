package com.deepdimen.matrix.knowledge.support.dataquery;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DataQueryRelationTest {

	@Test
	void changesOnlyTheCapturedRelationAndPreservesCurrentParameters() {
		var d = DataQueryRelation.Definition.patrol(10, "i", "b");
		var p = new DataQuerySqlGuard().prepare(
				"SELECT COUNT(*) AS n FROM dev_patrol_record p WHERE p.start_date >= '2026-08-01' AND p.start_date < '2026-09-01' AND p.del_flag='0' AND NOT EXISTS (SELECT 1 FROM dev_patrol_record_device c WHERE c.record_id=p.record_code)",
				Set.of("dev_patrol_record", "dev_patrol_record_device"));
		var r = DataQueryRelation.apply(p, d);
		assertThat(r.status()).isEqualTo("APPLIED");
		assertThat(r.query().parameters()).isEqualTo(p.parameters());
		assertThat(r.query().sql()).contains("p.id").doesNotContain("p.record_code");
	}

	@Test
	void unrelatedAndUnsupportedShapesAreNotSilentlyRewritten() {
		var d = DataQueryRelation.Definition.patrol(10, "i", "b");
		var p = new DataQuerySqlGuard().prepare("SELECT COUNT(*) FROM dev_device_card_info",
				Set.of("dev_device_card_info"));
		assertThat(DataQueryRelation.apply(p, d).status()).isEqualTo("NOT_APPLICABLE");
		String base = "SELECT COUNT(*) AS n FROM dev_patrol_record p WHERE p.start_date>='2026-08-01' AND p.start_date<'2026-09-01' AND EXISTS (SELECT 1 FROM dev_patrol_record_device c WHERE c.record_id=p.record_code)";
		for (String unsupported : List.of(base.replace("COUNT(*) AS n", "COUNT(*) OVER () AS n"),
				base.replace("SELECT 1 FROM", "SELECT COUNT(*) FROM"))) {
			var candidate = new DataQuerySqlGuard().prepare(unsupported,
					Set.of("dev_patrol_record", "dev_patrol_record_device"));
			var unchanged = DataQueryRelation.apply(candidate, d);
			assertThat(unchanged.status()).isEqualTo("UNSUPPORTED_SHAPE");
			assertThat(unchanged.query()).isEqualTo(candidate);
		}

	}

	static DataQuerySqlGuard.Prepared observedMonthlyQuery(String from, String to) {
		return new DataQuerySqlGuard().prepare(
				"SELECT COUNT(DISTINCT r.id) AS record_count FROM dev_patrol_record r LEFT JOIN dev_patrol_record_device d ON r.record_code=d.record_id AND r.company_id=d.company_id WHERE r.del_flag='0' AND r.start_date >= '"
						+ from + "' AND r.start_date < '" + to + "' AND d.id IS NULL",
				Set.of("dev_patrol_record", "dev_patrol_record_device"));
	}

	@Test
	void capturedMidnightTimestampsAreEquivalentMonthlyBoundsWithoutChangingFilters() {
		var definition = DataQueryRelation.Definition.patrol(10, "i", "b");
		for (var suffixes : List.of(List.of("", ""), List.of(" 00:00:00", " 00:00:00"),
				List.of("T00:00:00", "T00:00:00"), List.of(" 00:00:00.000000", " 00:00:00.000000"),
				List.of("T00:00:00.000", ""))) {
			var query = observedMonthlyQuery("2026-01-01" + suffixes.get(0), "2026-02-01" + suffixes.get(1));
			var window = DataQueryRelationProbe.window(query, definition);
			assertThat(window).isEqualTo(new DataQueryRelationProbe.Window("2026-01-01", "2026-02-01"));
			assertThat(DataQueryRelation.apply(query, definition).query().parameters()).isEqualTo(query.parameters());
			var previous = DataQueryRelationProbe.previousMonth(query, definition);
			assertThat(previous.parameters()).containsExactly("0", "2025-12-01" + suffixes.get(0),
					"2026-01-01" + suffixes.get(1));
			assertThat(previous.sql()).contains("r.company_id = d.company_id", "r.del_flag = ?");
			assertThat(DataQueryRelationProbe.window(previous, definition))
				.isEqualTo(new DataQueryRelationProbe.Window("2025-12-01", "2026-01-01"));
			assertThat(query.parameters()).containsExactly("0", "2026-01-01" + suffixes.get(0),
					"2026-02-01" + suffixes.get(1));
		}
	}

	@Test
	void unsupportedTimesCannotBeTruncatedOrExpandedIntoAnEntireMonth() {
		var definition = DataQueryRelation.Definition.patrol(10, "i", "b");
		for (var bounds : List.of(List.of("2026-08-01 00:00:00.000001", "2026-09-01 00:00:00"),
				List.of("2026-08-01", "2026-09-01 00:00:01"), List.of("2026-08-02", "2026-09-01"),
				List.of("2026-08-01", "2026-10-01"), List.of("2026-02-30", "2026-03-01"),
				List.of("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"))) {
			var query = observedMonthlyQuery(bounds.get(0), bounds.get(1));
			assertThatThrownBy(() -> DataQueryRelationProbe.window(query, definition))
				.hasMessage("INVESTIGATION_SCOPE_UNSUPPORTED");
			assertThat(query.parameters()).containsExactly("0", bounds.get(0), bounds.get(1));
		}
	}

}