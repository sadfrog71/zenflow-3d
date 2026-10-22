package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InformationSkillPromptPortTest {

	final InformationPolicyMapper policies = mock(InformationPolicyMapper.class);

	final InformationRunMapper runs = mock(InformationRunMapper.class);

	final ObjectMapper json = new ObjectMapper();

	final InformationSkillCollectionScope scope = new InformationSkillCollectionScope(policies, runs,
			mock(TenantUserDirectory.class), json);

	final InformationSkillResultStore results = mock(InformationSkillResultStore.class);

	final InformationReviewModel model = mock(InformationReviewModel.class);

	final ObjectProvider<InformationReviewModel> models = mock(ObjectProvider.class);

	final InformationSkillPromptPort port = new InformationSkillPromptPort(models, scope, results, json);

	final Identity identity = new Identity("42", "information-collector", "information-collector",
			"work-information:9");

	final InformationSkillInput input = new InformationSkillInput("t", "u", "e", LocalDate.of(2026, 9, 26));

	final String analysis = "{\"eligible\":false,\"reason\":\"insufficient\"}";

	final Runnable callback = mock(Runnable.class);

	InformationSkillCollectionScope.Lease lease;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		var policy = new InformationPolicy();
		policy.setId(7L);
		policy.setEnabled(1);
		policy.setModelName("fixed-model");
		var run = new InformationRun();
		run.setId(8L);
		run.setPolicyId(7L);
		run.setStatus("RUNNING");
		when(policies.selectOne(any())).thenReturn(policy);
		when(runs.selectOne(any())).thenReturn(run);
		when(models.getIfAvailable()).thenReturn(model);
		when(model.supportsManagedModel("fixed-model")).thenReturn(true);
		when(results.authorizeInput(any(), any(), eq("9"), any())).thenAnswer(call -> call.getArgument(3));
		when(model.chatManaged(any(), any(), any(), any()))
			.thenReturn(new InformationReviewModel.ManagedResponse(analysis, "fixed-model"));
		lease = scope.open(7L, 8L);
	}

	@AfterEach
	void cleanup() {
		lease.close();
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	Consumer consumer(String slot) {
		return new Consumer("INFORMATION", "7", slot, "default");
	}

	ResolvedSkillSnapshot snapshot(String slot, String modelId) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, modelId, null), "{}", "{}",
				InformationSkillInstructions.source(slot).instruction(), List.of(), "{}", List.of());
	}

	@Test
	void analysisUsesOriginalInstructionAndFrozenPromptWithoutLegacyCall() {
		var output = port.execute("10", identity, consumer("ANALYZE"), snapshot("ANALYZE", "fixed-model"),
				input.snapshot(json), callback);
		assertEquals(analysis, output.outputJson());
		verify(callback).run();
		verify(model).chatManaged("work-info-analyze-10", "fixed-model", InformationSkillInstructions.ANALYZER_SYSTEM,
				input.analysisPrompt());
		verify(model, never()).chat(any(), any(), any());
	}

	@Test
	void legacyAdapterMustExplicitlySupportFixedModels() {
		InformationReviewModel legacy = (id, system, user) -> "{}";
		when(models.getIfAvailable()).thenReturn(legacy);
		assertFalse(port.available(consumer("ANALYZE"), snapshot("ANALYZE", "fixed-model")));
		assertThrows(IllegalArgumentException.class, () -> port.execute("10", identity, consumer("ANALYZE"),
				snapshot("ANALYZE", "fixed-model"), input.snapshot(json), callback));
		verifyZeroInteractions(callback);
	}

	@Test void modelOrInputAuthorizationDriftStopsBeforeInvocation() {
        when(model.supportsManagedModel("different")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->port.execute("10",identity,consumer("ANALYZE"),snapshot("ANALYZE","different"),input.snapshot(json),callback));
        when(results.authorizeInput(any(),any(),any(),any())).thenThrow(new IllegalArgumentException("INPUT_INVALID"));
        assertThrows(IllegalArgumentException.class,()->port.execute("10",identity,consumer("ANALYZE"),snapshot("ANALYZE","fixed-model"),input.snapshot(json),callback));
        verifyZeroInteractions(callback);verify(model,never()).chatManaged(any(),any(),any(),any());
    }

	@Test void actualModelDriftCannotReturnAResult() {
        when(model.chatManaged(any(),any(),any(),any())).thenReturn(new InformationReviewModel.ManagedResponse(analysis,"other"));
        assertThrows(IllegalArgumentException.class,()->port.execute("10",identity,consumer("ANALYZE"),snapshot("ANALYZE","fixed-model"),input.snapshot(json),callback));
        verify(callback).run();verify(model,times(1)).chatManaged(any(),any(),any(),any());
    }

	@Test
	void reviewRequiresEligibleCandidateAndUsesTheOriginalReviewInstruction() throws Exception {
		var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(input.snapshot(json));
		node.set("analysis", json.readTree(analysis));
		assertThrows(IllegalArgumentException.class, () -> port.execute("11", identity, consumer("REVIEW"),
				snapshot("REVIEW", "fixed-model"), node.toString(), callback));
		String accepted = "{\"eligible\":true,\"contentType\":\"CASE\",\"title\":\"t\",\"summary\":\"s\",\"tags\":\"AI\",\"confidence\":0.9,\"reason\":\"ok\"}";
		node.set("analysis", json.readTree(accepted));
		when(model.chatManaged(any(), any(), any(), any())).thenReturn(new InformationReviewModel.ManagedResponse(
				"{\"approved\":true,\"riskLevel\":\"LOW\",\"reason\":\"ok\"}", "fixed-model"));
		port.execute("11", identity, consumer("REVIEW"), snapshot("REVIEW", "fixed-model"), node.toString(), callback);
		verify(model).chatManaged("work-info-review-11", "fixed-model", InformationSkillInstructions.REVIEWER_SYSTEM,
				input.reviewPrompt(accepted));
		verify(callback, times(1)).run();
	}

}
