package com.deepdimen.matrix.knowledge.support.dataquery;

import org.junit.jupiter.api.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Synthetic SQL rows + actual H2 platform lifecycle. Real MySQL trials are separately
 * recorded in verification.md.
 */
class DataQueryDepartmentCorrectionTest {

	DataQueryAiChatTest f;

	DataQueryCorrectionService service;

	DataQueryAiChatService chat;

	AtomicInteger current;

	final String sql = "SELECT patrol_dept_name,COUNT(*) AS record_count FROM dev_patrol_record WHERE start_date >= '2026-08-01' AND start_date < '2026-09-01' AND del_flag='0' GROUP BY patrol_dept_name ORDER BY record_count DESC LIMIT 5";

	DataQuerySecurity.ManagementActor actor(String tenant, String user, boolean human) {
		var p = new HashSet<>(Set.of("knowledge_dataquery_edit", "knowledge_dataquery_verify",
				"knowledge_dataquery_review", "knowledge_dataquery_publish"));
		if (human)
			p.add("knowledge_dataquery_human_review");
		return new DataQuerySecurity.ManagementActor(new Identity(tenant, user), p);
	}

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		f.published("r1", "巡视");
		f.jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,'top.patrol','r1')");
		f.jdbc.update("UPDATE ai_dataquery_access SET targets_json=?",
				"[{\"type\":\"user\",\"targetId\":\"2\"},{\"type\":\"user\",\"targetId\":\"3\"}]");
		current = new AtomicInteger(7);
		service = new DataQueryCorrectionService(f.source, f.metadata, f.json, f.clock, (who, scope, original) -> {
			assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
				.isActualTransactionActive()).isFalse();
			var oracle = DataQueryDepartmentVerifier
				.mergeNames(new DataQuerySqlExecutor.Rows(List.of("c", "d", "n", "count"),
						List.of(List.of("C1", "D", "same", "7"), List.of("C2", "D", "same", "5"))));
			assertThat(DataQueryDepartmentVerifier.baselineMatches(wrong(), 2, oracle)).isFalse();
			DataQueryDepartmentVerifier.requireMatches(correct(), oracle);
			return new DataQueryDepartmentVerifier.Trial(true, true, 1, 2, List.of("SYNTHETIC_ROWS_ONLY"));
		});
		chat = new DataQueryAiChatService(f.metadata,
				(who, scope, p) -> p.sql().contains("AS name_variant_count") ? correct()
						: p.sql().contains("__dq_count") ? wrong() : new DataQuerySqlExecutor.Rows(List.of("total"),
								List.of(List.of(Integer.toString(current.get() + 5)))));
	}

	@AfterEach
	void cleanup() {
		f.cleanup();
	}

	DataQuerySqlExecutor.Rows wrong() {
		return new DataQuerySqlExecutor.Rows(List.of("name", "n", "c", "cc", "d", "dc", "n0", "gn"),
				List.of(List.of("same", Integer.toString(current.get() + 5), "C1", "2", "D", "1",
						Integer.toString(current.get() + 5), "same")));
	}

	DataQuerySqlExecutor.Rows correct() {
		return new DataQuerySqlExecutor.Rows(
				List.of("company_id", "patrol_dept_id", "record_count", "name_variant_count", "missing_name_count",
						"department_name"),
				List.of(List.of("C1", "D", Integer.toString(current.get()), "1", "0", "same"),
						List.of("C2", "D", "5", "1", "0", "same")));
	}

	com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO query(long id, String question) {
		return chat.execute(chat.begin(f.owner, "reader", f.message(id, question)), sql);
	}

	String draft() {
		var before = query(80, "2026年8月巡视最多的五个部门");
		String feedback = service.feedback(f.owner, before.getDataQuery().get("queryId").toString(),
				DataQueryCorrectionService.Category.BUSINESS_DEFINITION, "同名跨公司合并，请按真实公司和部门身份计数", null);
		return service.draft(actor("1", "2", true), feedback, List.of("巡视"), "2026-08",
				DataQueryDepartmentDimension.KIND);
	}

	@Test
	void failedGrainDraftTrialHumanPublishCurrentDataReuseAndRollback() {
		var version = draft();
		assertThat(f.metadata.current(f.owner).department()).isNull();
		assertThat(service.verify(actor("1", "2", true), version, 1)).contains("SYNTHETIC_ROWS_ONLY");
		assertThatThrownBy(() -> service.approve(actor("1", "2", true), version, 1)).hasMessage("REVIEWER_MUST_DIFFER");
		assertThatThrownBy(() -> service.publish(actor("1", "3", false), version, 1, "test"))
			.hasMessage("MANAGEMENT_FORBIDDEN");
		service.approve(actor("1", "3", true), version, 1);
		assertThat(f.metadata.current(f.owner).department()).isNull();
		service.publish(actor("1", "3", true), version, 1, "Synthetic human-reviewed grain regression");
		var after = query(81, "2026年8月各部门巡视前五");
		assertThat(after.getDataQuery().get("ruleApplication")).isEqualTo("DEPARTMENT_IDENTITY");
		assertThat((List<?>) after.getDataQuery().get("rows")).hasSize(2);
		current.set(9);
		assertThat(query(82, "2026年8月部门巡视Top5").getDataQuery().get("rows"))
			.isEqualTo(List.of(List.of("same", "9"), List.of("same", "5")));
		service.rollback(actor("1", "3", true), version, "r1", "Synthetic rollback");
		assertThat(f.metadata.current(f.owner).department()).isNull();
		assertThat((List<?>) query(83, "2026年8月各部门巡视前五").getDataQuery().get("rows")).hasSize(1);
	}

	@Test
	void editingInvalidatesTrialApprovalAndCrossTenantCannotReadDraft() {
		var version = draft();
		service.verify(actor("1", "2", true), version, 1);
		service.approve(actor("1", "3", true), version, 1);
		service.edit(actor("1", "2", true), version, 1, List.of("巡视"), "2026-08", DataQueryDepartmentDimension.KIND);
		var state = service.inspect(actor("1", "2", true), version);
		assertThat(state).containsEntry("state", "DRAFT")
			.containsEntry("validated_revision", null)
			.containsEntry("approved_revision", null);
		assertThatThrownBy(() -> service.publish(actor("1", "3", true), version, 1, "stale"))
			.hasMessage("REVISION_CONFLICT");
		assertThatThrownBy(() -> service.inspect(actor("2", "2", true), version)).hasMessage("DRAFT_NOT_FOUND");
	}

	@Test
	void staleBindingRevokedAuthorizationAndUnpublishedPayloadAreRejected() {
		var version = draft();
		service.verify(actor("1", "2", true), version, 1);
		f.jdbc.update("UPDATE ai_dataquery_binding SET binding_revision='changed'");
		assertThatThrownBy(() -> service.approve(actor("1", "3", true), version, 1))
			.hasMessage("DEPARTMENT_RULE_STALE");
		f.jdbc.update("UPDATE ai_dataquery_binding SET binding_revision='binding'");
		f.jdbc.update("UPDATE ai_dataquery_rule_active SET rule_version=?", version);
		assertThatThrownBy(() -> chat.begin(f.owner, "reader", f.message(82, "test")))
			.hasMessage("METRIC_DEFINITION_UNCONFIRMED");
		f.jdbc.update("UPDATE ai_dataquery_rule_active SET rule_version='r1'");
		f.jdbc.update("DELETE FROM ai_dataquery_access");
		assertThatThrownBy(() -> service.verify(actor("1", "2", true), version, 1))
			.hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void expiredTrialCannotBeApproved() {
		var version = draft();
		service.verify(actor("1", "2", true), version, 1);
		var late = new DataQueryCorrectionService(f.source, f.metadata, f.json,
				Clock.offset(f.clock, Duration.ofMinutes(31)));
		assertThatThrownBy(() -> late.approve(actor("1", "3", true), version, 1))
			.hasMessage("DEPARTMENT_VERIFICATION_STALE");
	}

	@Test
	void namesNeverMergeOrSplitIdentityAndConflictsRemainExplicit() {
		var raw = new DataQuerySqlExecutor.Rows(List.of("c", "d", "name", "n"),
				List.of(List.of("C1", "D", "same", "3"), List.of("C1", "D", "renamed", "4"),
						List.of("C2", "D", "same", "5"), Arrays.asList("C2", "D", null, "2")));
		var result = DataQueryDepartmentVerifier.mergeNames(raw);
		assertThat(result).hasSize(2);
		assertThat(result.get(0).names()).hasSize(2);
		assertThat(result.get(1).missing()).isEqualTo(2);
		var corrected = new DataQuerySqlExecutor.Rows(List.of("c", "d", "n", "v", "m", "name"),
				List.of(Arrays.asList("C1", "D", "7", "2", "0", null), List.of("C2", "D", "7", "1", "2", "same")));
		DataQueryDepartmentVerifier.requireMatches(corrected, result);
		var view = DataQueryDepartmentDimension.corrected("q",
				new DataQueryDepartmentDimension.Scope("2026-08-01", "2026-09-01", 5, List.of()), corrected);
		assertThat(view.quality().get(0)).containsEntry("nameStatus", "CONFLICT");
		assertThat(view.quality().get(1)).containsEntry("nameStatus", "SINGLE_NAME_WITH_GAPS");
		assertThat(view.ranked().rows().values().get(0).get(0)).isNull();
	}

	@Test
	void publishedCorrectionReusesWithObservedNonEmptyNameFilter() {
		var version = draft();
		service.verify(actor("1", "2", true), version, 1);
		service.approve(actor("1", "3", true), version, 1);
		service.publish(actor("1", "3", true), version, 1, "Synthetic bounded predicate test");
		String filtered = sql.replace("del_flag='0'",
				"del_flag='0' AND patrol_dept_name IS NOT NULL AND patrol_dept_name <> ''");
		var result = chat.execute(chat.begin(f.owner, "reader", f.message(84, "2026年8月巡视记录最多的五个部门")), filtered);
		assertThat(result.getDataQuery().get("ruleApplication")).isEqualTo("DEPARTMENT_IDENTITY");
		assertThat((List<?>) result.getDataQuery().get("rows")).hasSize(2);
	}

}
