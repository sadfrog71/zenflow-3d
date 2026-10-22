package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.entity.*;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import com.deepdimen.matrix.work.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InformationSkillResultStoreTest {

	final InformationPolicyMapper policies = mock(InformationPolicyMapper.class);

	final InformationRunMapper runs = mock(InformationRunMapper.class);

	final InformationSkillInvocationMapper mapper = mock(InformationSkillInvocationMapper.class);

	final ObjectMapper json = new ObjectMapper();

	final InformationSkillCollectionScope scope = new InformationSkillCollectionScope(policies, runs,
			mock(TenantUserDirectory.class), json);

	final InformationSkillResultStore store = new InformationSkillResultStore(scope, mapper, json);

	final Map<Long, InformationSkillInvocationEntity> rows = new HashMap<>();

	final InformationPolicy policy = new InformationPolicy();

	final InformationRun run = new InformationRun();

	final InformationSkillInput input = new InformationSkillInput("Title", "https://example.test/item", "Evidence",
			LocalDate.of(2026, 9, 26));

	final String analysis = "{\"eligible\":true,\"contentType\":\"CASE\",\"title\":\"Title\",\"summary\":\"Summary\",\"tags\":\"water,AI\",\"confidence\":0.9,\"reason\":\"evidence\"}";

	InformationSkillCollectionScope.Lease lease;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		policy.setId(7L);
		policy.setEnabled(1);
		policy.setOrgId(5L);
		run.setId(8L);
		run.setPolicyId(7L);
		run.setOrgId(5L);
		run.setStatus("RUNNING");
		when(policies.selectOne(any())).thenReturn(policy);
		when(runs.selectOne(any())).thenReturn(run);
		when(mapper.insert(any(InformationSkillInvocationEntity.class))).thenAnswer(call -> {
			var row = call.getArgument(0, InformationSkillInvocationEntity.class);
			rows.put(row.getId(), row);
			return 1;
		});
		when(mapper.findOwned(any(), any(), any())).thenAnswer(call -> {
			var row = rows.get(call.getArgument(2));
			return row != null && row.getTenantId().equals(call.getArgument(0))
					&& row.getCollectionRunId().equals(call.getArgument(1)) ? row : null;
		});
		when(mapper.findStage(any(), any(), any(), any())).thenAnswer(call -> rows.values()
			.stream()
			.filter(row -> row.getTenantId().equals(call.getArgument(0))
					&& row.getCollectionRunId().equals(call.getArgument(1))
					&& row.getDocumentDigest().equals(call.getArgument(2))
					&& row.getSlotCode().equals(call.getArgument(3)))
			.findFirst()
			.orElse(null));
		when(mapper.saveOnce(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
			var row = rows.get(call.getArgument(2));
			if (!"PREPARED".equals(row.getStatus()))
				return 0;
			row.setSkillRunId(call.getArgument(4));
			row.setSkillOutputJson(call.getArgument(5));
			row.setStatus("RAW_RESULT");
			return 1;
		});
		lease = scope.open(7L, 8L);
	}

	@AfterEach
	void cleanup() {
		lease.close();
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void analysisAndReviewPersistSeparateOutputsWithExactParentInput() throws Exception {
		var parent = store.prepareAnalysis(input);
		assertEquals(parent.getId(), store.prepareAnalysis(input).getId());
		var result = save(parent, analysis, "10");
		var review = store.prepareReview(parent.getId(), result);
		assertEquals(parent.getId(), review.getParentInvocationId());
		assertEquals(json.readTree(analysis), json.readTree(review.getInputSnapshot()).get("analysis"));
		assertEquals(input.snapshot(json), review.getEvidenceSnapshot());
		String output = "{\"approved\":true,\"riskLevel\":\"LOW\",\"reason\":\"verified\"}";
		var reviewed = save(review, output, "11");
		assertEquals(json.readTree(output), json.readTree(
				store.read(identity(review), consumer(review), review.getId().toString(), "11", reviewed.resultRef())));
		assertEquals(2, rows.size());
	}

	@Test
	void unsuccessfulOrIneligibleAnalysisCannotPrepareReview() {
		var parent = store.prepareAnalysis(input);
		assertThrows(IllegalArgumentException.class, () -> store.prepareReview(parent.getId(), null));
		var rejected = save(parent, "{\"eligible\":false,\"reason\":\"insufficient\"}", "10");
		assertThrows(IllegalArgumentException.class, () -> store.prepareReview(parent.getId(), rejected));
		assertEquals(1, rows.size());
	}

	@Test
	void unknownExecutionWithSavedOutputStillCannotStartReview() {
		var parent = store.prepareAnalysis(input);
		var success = save(parent, analysis, "10");
		var unknown = new ExecuteResult("10", RunStatus.UNKNOWN, analysis, success.resultRef(), List.of(), "2", null,
				null, ErrorCode.RESULT_UNKNOWN);
		assertThrows(IllegalArgumentException.class, () -> store.prepareReview(parent.getId(), unknown));
	}

	@Test
	void changedEvidenceForSameDocumentConflictsAndCallerCannotSubstituteInput() {
		var parent = store.prepareAnalysis(input);
		assertThrows(IllegalArgumentException.class, () -> store
			.prepareAnalysis(new InformationSkillInput(input.title(), input.url(), "changed", input.reviewDate())));
		assertThrows(IllegalArgumentException.class,
				() -> store.authorizeInput(identity(parent), consumer(parent), parent.getId().toString(), "{}"));
	}

	@Test
	void differentActorTenantOrResultRunCannotReadStoredOutput() {
		var parent = store.prepareAnalysis(input);
		var success = save(parent, analysis, "10");
		assertThrows(IllegalArgumentException.class,
				() -> store.read(new Identity("42", "other", "other", "work-information:" + parent.getId()),
						consumer(parent), parent.getId().toString(), "10", success.resultRef()));
		assertThrows(IllegalArgumentException.class, () -> store.read(identity(parent), consumer(parent),
				parent.getId().toString(), "11", success.resultRef()));
		TenantContextHolder.setTenantId(43L);
		assertThrows(IllegalArgumentException.class, () -> store.read(identity(parent), consumer(parent),
				parent.getId().toString(), "10", success.resultRef()));
		TenantContextHolder.setTenantId(42L);
	}

	@Test
	void repeatedIdenticalSaveIsAllowedButConflictingResultIsRejected() {
		var parent = store.prepareAnalysis(input);
		var success = save(parent, analysis, "10");
		assertEquals(success.resultRef(), save(parent, analysis, "10").resultRef());
		assertThrows(IllegalArgumentException.class,
				() -> save(parent, analysis.replace("Summary", "different"), "10"));
		assertThrows(IllegalArgumentException.class, () -> save(parent, analysis, "11"));
		policy.setEnabled(0);
		assertThrows(IllegalArgumentException.class, () -> save(parent, analysis, "10"));
	}

	@Test
	void stringApprovalsExtraActionsAndTrailingJsonCannotBeSaved() {
		var parent = store.prepareAnalysis(input);
		for (String bad : List.of(analysis.replace("true", "\"true\""), analysis + "{}", analysis.replace("0.9", "2"),
				analysis.replace("\"eligible\":true", "\"eligible\":true,\"publish\":true")))
			assertThrows(IllegalArgumentException.class, () -> save(parent, bad, "10"));
		verify(mapper, never()).saveOnce(any(), any(), any(), any(), any(), any());
	}

	ExecuteResult save(InformationSkillInvocationEntity row, String output, String skillRun) {
		var ref = store.save(identity(row), consumer(row), row.getId().toString(), skillRun, output);
		return new ExecuteResult(skillRun, RunStatus.SUCCEEDED, output, ref, List.of(), "2", null, null, null);
	}

	Identity identity(InformationSkillInvocationEntity row) {
		return new Identity("42", "information-collector", "information-collector", "work-information:" + row.getId());
	}

	Consumer consumer(InformationSkillInvocationEntity row) {
		return new Consumer("INFORMATION", "7", row.getSlotCode(), "default");
	}

}
