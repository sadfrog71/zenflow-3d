package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InformationSkillReviewRunnerTest {

	final SkillBindingResolver resolver = mock(SkillBindingResolver.class);

	final SkillExecutionService executor = mock(SkillExecutionService.class);

	final InformationSkillResultStore store = mock(InformationSkillResultStore.class);

	final ObjectMapper json = new ObjectMapper();

	final InformationSkillReviewRunner runner = new InformationSkillReviewRunner(provider(resolver), provider(executor),
			store, json);

	final InformationSkillInput input = new InformationSkillInput("t", "u", "e", LocalDate.of(2026, 9, 26));

	final String analysis = "{\"eligible\":true,\"contentType\":\"CASE\",\"title\":\"t\",\"summary\":\"s\",\"tags\":\"AI\",\"confidence\":0.9,\"reason\":\"ok\"}";

	final String review = "{\"approved\":true,\"riskLevel\":\"LOW\",\"reason\":\"ok\"}";

	@BeforeEach void setup() {
        when(store.prepareAnalysis(input)).thenReturn(row(9L,"ANALYZE"));when(store.prepareReview(eq(9L),any())).thenReturn(row(10L,"REVIEW"));
        when(store.authorizeInput(any(),any(),any(),any())).thenReturn("{}");
        when(resolver.resolve(any())).thenAnswer(call->{var r=call.getArgument(0,ResolveRequest.class);var slot=r.consumer().slotCode();return new Resolution(new ResolvedSkillSnapshot("1", "ANALYZE".equals(slot)?"2":"3","digest","4",RuntimeKind.PROMPT,new ExecutorRef("PROMPT",null,"model",null),InformationSkillContract.inputSchema(json,slot),InformationSkillContract.outputSchema(slot),InformationSkillInstructions.source(slot).instruction(),List.of(),"{}",List.of()),"token-"+slot,Instant.now().plusSeconds(60));});
        when(executor.execute(any())).thenAnswer(call->{var r=call.getArgument(0,ExecuteRequest.class);boolean a="ANALYZE".equals(r.consumer().slotCode());return new ExecuteResult(a?"20":"21",RunStatus.SUCCEEDED,a?analysis:review,a?"ref-a":"ref-r",List.of(),a?"2":"3",null,null,null);});
        when(store.read(any(),any(),eq("9"),any(),any())).thenReturn(analysis);when(store.read(any(),any(),eq("10"),any(),any())).thenReturn(review);
    }

	@Test
	void reviewFollowsSuccessfulAnalysisAndUsesDistinctStableKeysAndParentRun() {
		assertTrue(runner.review(input).approved());
		var requests = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
		verify(executor, times(2)).execute(requests.capture());
		assertEquals("work-information:9", requests.getAllValues().get(0).idempotencyKey());
		assertNull(requests.getAllValues().get(0).parentRunId());
		assertEquals("work-information:10", requests.getAllValues().get(1).idempotencyKey());
		assertEquals("20", requests.getAllValues().get(1).parentRunId());
		var order = inOrder(store, executor);
		order.verify(executor).execute(any());
		order.verify(store).read(any(), any(), eq("9"), any(), any());
		order.verify(store).prepareReview(eq(9L), any());
		order.verify(executor).execute(any());
		verify(executor, never()).fallbackAllowed(any(), any());
	}

	@Test
	void unknownAnalysisPreservesActualExecutionAndStopsBeforeReview() {
		var result = new ExecuteResult("20", RunStatus.UNKNOWN, null, null, List.of(), "2", null, null,
				ErrorCode.RESULT_UNKNOWN);
		doThrow(new SkillExecutionFailure(result, new IllegalStateException("timeout"))).when(executor).execute(any());
		var failure = assertThrows(InformationSkillReviewRunner.StageFailure.class, () -> runner.review(input));
		assertSame(result, failure.result());
		assertEquals("ANALYZE", failure.slot());
		assertEquals("9", failure.businessRunId());
		verify(store, never()).prepareReview(any(), any());
		verify(executor, times(1)).execute(any());
	}

	@Test
	void rejectedAnalysisIsQuarantinedWithoutReview() {
		String rejected = "{\"eligible\":false,\"reason\":\"no evidence\"}";
		doReturn(new ExecuteResult("20", RunStatus.SUCCEEDED, rejected, "ref-a", List.of(), "2", null, null, null))
			.when(executor)
			.execute(any());
		when(store.read(any(), any(), eq("9"), any(), any())).thenReturn(rejected);
		assertFalse(runner.review(input).approved());
		verify(store, never()).prepareReview(any(), any());
	}

	@Test void conflictingPersistedResultCannotProduceApproval() {
        when(store.read(any(),any(),eq("10"),any(),any())).thenReturn(review.replace("true","false"));
        assertThrows(InformationSkillReviewRunner.StageFailure.class,()->runner.review(input));
    }

	@Test
	void oldEntryCannotBypassManagedModeWhenNoCollectorIsPresent() {
		var model = mock(InformationReviewModel.class);
		var service = new InformationReviewService(provider(model), json);
		var policy = new InformationPolicy();
		policy.setId(7L);
		policy.setSkillManagementMode("MANAGED");
		assertFalse(service.review(policy, "t", "u", "e").approved());
		assertFalse(service.review(policy, 8L, "t", "u", "e").approved());
		verifyZeroInteractions(model);
	}

	InformationSkillInvocationEntity row(Long id, String slot) {
		var row = new InformationSkillInvocationEntity();
		row.setId(id);
		row.setTenantId(42L);
		row.setActorId("information-collector");
		row.setPolicyId(7L);
		row.setSlotCode(slot);
		row.setInputSnapshot("{}");
		return row;
	}

	static <T> ObjectProvider<T> provider(T value) {
		ObjectProvider<T> p = mock(ObjectProvider.class);
		when(p.getIfAvailable()).thenReturn(value);
		return p;
	}

}
