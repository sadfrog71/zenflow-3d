package com.deepdimen.matrix.knowledge.support.dataquery;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DataQuerySqlGuardTest {

	private final DataQuerySqlGuard guard = new DataQuerySqlGuard();

	private final Set<String> tables = Set.of("dev_patrol_record", "dev_device");

	@Test
	void supportsDifferentMetricsJoinsAndChartAggregatesWithoutQuestionTemplates() {
		for (String sql : new String[] { "SELECT COUNT(*) AS total FROM dev_device",
				"SELECT company_id, COUNT(DISTINCT patrol_dept_id) AS departments FROM dev_patrol_record GROUP BY company_id ORDER BY departments DESC",
				"SELECT DATE_FORMAT(start_date,'%Y-%m') AS month,COUNT(*) AS count FROM dev_patrol_record WHERE start_date >= '2026-01-01' GROUP BY DATE_FORMAT(start_date,'%Y-%m') ORDER BY month",
				"SELECT r.company_id,COUNT(*) FROM dev_patrol_record r JOIN dev_device d ON r.company_id=d.company_id GROUP BY r.company_id",
				"SELECT company_id, SUM(CASE WHEN del_flag='0' THEN 1 ELSE 0 END) AS total FROM dev_patrol_record GROUP BY company_id HAVING COUNT(*)>2 LIMIT 100",
				"SELECT company_id FROM dev_device UNION ALL SELECT company_id FROM dev_patrol_record",
				"WITH records AS (SELECT company_id FROM dev_patrol_record) SELECT company_id,COUNT(*) FROM records GROUP BY company_id" })
			assertFalse(guard.prepare(sql, tables).tables().isEmpty(), sql);
	}

	@Test
	void bindsValuesWithoutChangingOrderByOrdinalOrExposingThemInLogs() {
		var p = guard.prepare(
				"SELECT company_id,COUNT(*) FROM dev_patrol_record WHERE del_flag='0' AND company_id='secret-value' GROUP BY company_id ORDER BY 2 DESC LIMIT 12",
				tables);
		assertFalse(p.sql().contains("secret-value"));
		assertTrue(p.sql().contains("ORDER BY 2"));
		assertEquals("0", p.parameters().get(0));
		assertEquals("secret-value", p.parameters().get(1));
		assertFalse(p.toString().contains("secret-value"));
	}

	@Test
	void rejectsWritesSideEffectsAndUnselectedOrSystemTables() {
		for (String sql : new String[] { "DELETE FROM dev_patrol_record",
				"SELECT * FROM dev_device; DELETE FROM dev_device", "SELECT * FROM mysql.user",
				"SELECT * FROM unselected", "SELECT * FROM other.dev_device",
				"SELECT * FROM dev_device WHERE EXISTS (SELECT 1 FROM mysql.user)", "SELECT SLEEP(2) FROM dev_device",
				"SELECT custom_udf(company_id) FROM dev_device",
				"SELECT * INTO OUTFILE '/tmp/forbidden' FROM dev_device", "SELECT * FROM dev_device FOR UPDATE",
				"SELECT @@version FROM dev_device", "SELECT * FROM dev_device WHERE company_id=?",
				"SELECT load_file('/etc/passwd') FROM dev_device" })
			assertThrows(DataQueryException.class, () -> guard.prepare(sql, tables), sql);
	}

	@Test
	void ctesCannotHideUnselectedPhysicalTablesOutsideTheirScope() {
		for (String sql : new String[] {
				"SELECT * FROM unselected WHERE EXISTS (WITH unselected AS (SELECT company_id FROM dev_device) SELECT * FROM unselected)",
				"WITH visible AS (SELECT * FROM unselected),unselected AS (SELECT * FROM dev_device) SELECT * FROM visible",
				"WITH unselected AS (SELECT * FROM unselected) SELECT * FROM unselected" })
			assertThrows(DataQueryException.class, () -> guard.prepare(sql, tables), sql);
	}

	@Test
	void failsClosedWithMissingInventoryOrUnparseableInput() {
		assertThrows(DataQueryException.class, () -> guard.prepare("SELECT * FROM dev_device", Set.of()));
		assertThrows(DataQueryException.class, () -> guard.prepare("not sql", tables));
	}

	@Test
	void exactPhysicalIdentifiersCannotBeWidenedByCaseOrQuotedBackticks() {
		for (String sql : new String[] { "SELECT * FROM DEV_DEVICE", "SELECT * FROM TOP_DEVICE_BIZ.dev_device",
				"SELECT * FROM `dev_``device`", "SELECT * FROM `top_``device_biz`.dev_device" })
			assertThrows(DataQueryException.class, () -> guard.prepare(sql, tables), sql);
		assertTrue(
				guard.prepare("SELECT * FROM `top_device_biz`.`dev_device`", tables).tables().contains("dev_device"));
		assertTrue(guard.prepare("WITH Records AS (SELECT * FROM dev_device) SELECT * FROM records", tables)
			.tables()
			.contains("dev_device"));
	}

}
