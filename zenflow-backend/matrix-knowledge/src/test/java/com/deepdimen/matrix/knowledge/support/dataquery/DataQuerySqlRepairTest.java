package com.deepdimen.matrix.knowledge.support.dataquery;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Set;

class DataQuerySqlRepairTest {

	final DataQuerySqlRepair repair = new DataQuerySqlRepair();

	final DataQueryMetadataRepository.DatasetInventory inventory = new DataQueryMetadataRepository.DatasetInventory(
			Set.of("dev_device"), "[{\"table_name\":\"dev_device\",\"fields\":[{\"field_name\":\"company_id\"}]}]",
			"schema-v1");

	@Test
	void onlyTableQualificationChangesAndLiteralsStayBound() {
		String sql = "SELECT dev_device.company_id,COUNT(*) AS total FROM dev_device d WHERE dev_device.company_id='secret-parameter' GROUP BY dev_device.company_id ORDER BY total DESC";
		var diagnosis = repair.diagnose(sql, inventory).orElseThrow();
		assertThat(diagnosis.references()).isEqualTo(3);
		var guard = new DataQuerySqlGuard();
		assertThat(guard.prepare(diagnosis.correctedSql(), inventory.tables()).parameters())
			.isEqualTo(guard.prepare(sql, inventory.tables()).parameters());
		assertThat(diagnosis.facts()).doesNotContain("secret-parameter", "SELECT");
		assertThat(diagnosis.toString()).doesNotContain("secret-parameter");
		assertThat(diagnosis
			.accepts("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\"}")).isTrue();
		assertThat(diagnosis.accepts(
				"{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\",\"sql\":\"SELECT 1\"}"))
			.isFalse();
		assertThat(
				diagnosis.accepts("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"sys_user\",\"alias\":\"d\"}"))
			.isFalse();
	}

	@Test
	void ambiguousBusinessChangesAndAlreadyValidQueriesAreNotRepairable() {
		for (String sql : new String[] { "SELECT d.company_id FROM dev_device d",
				"SELECT dev_device.missing_field FROM dev_device d", "SELECT invented.company_id FROM dev_device d",
				"SELECT dev_device.company_id FROM dev_device",
				"SELECT dev_device.company_id FROM dev_device d JOIN dev_device e ON d.company_id=e.company_id",
				"SELECT dev_device.company_id FROM dev_device d WHERE EXISTS(SELECT 1 FROM dev_device)",
				"WITH x AS (SELECT * FROM dev_device) SELECT dev_device.company_id FROM x d",
				"SELECT dev_device.company_id FROM dev_device d UNION SELECT company_id FROM dev_device",
				"SELECT dev_device.company_id FROM dev_device d; DELETE FROM dev_device",
				"SELECT Dev_device.company_id FROM Dev_device d", "SELECT company_id FROM sys_user" }) {
			assertThat(repair.diagnose(sql, inventory)).as(sql).isEmpty();
		}
	}

	@Test
	void unavailableOrSlowAdvisorCannotApproveAndNeverReceivesValues() {
		var diagnosis = repair.diagnose("SELECT dev_device.company_id FROM dev_device d", inventory).orElseThrow();
		assertThat(DataQuerySqlRepair.suggestedByAdvisor(null, diagnosis)).isFalse();
		assertThat(DataQuerySqlRepair.suggestedByAdvisor(facts -> {
			throw new IllegalStateException("private");
		}, diagnosis)).isFalse();
		assertThat(DataQuerySqlRepair.suggestedByAdvisor(facts -> {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return "{}";
		}, diagnosis, 10)).isFalse();
	}

}
