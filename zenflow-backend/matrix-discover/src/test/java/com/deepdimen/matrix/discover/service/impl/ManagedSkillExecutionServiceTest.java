package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import com.deepdimen.matrix.discover.skill.*;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedSkillExecutionServiceTest {

	private final SkillResolutionTokenStore tokens = mock(SkillResolutionTokenStore.class);

	private final ManagedSkillBindingResolver resolver = mock(ManagedSkillBindingResolver.class);

	private final ManagedSkillRunIndex index = mock(ManagedSkillRunIndex.class);

	private final ManagedPromptSkillExecutor prompt = mock(ManagedPromptSkillExecutor.class);

	private final SkillResultStore results = mock(SkillResultStore.class);

	private final Identity identity = new Identity("42", "3", "3", "run");

	private final Consumer consumer = new Consumer("WORK_BRIEF", "7", "WORK_BRIEF", "default");

	private final ExecuteRequest request = new ExecuteRequest(identity, consumer, "token", "{}", null, "key", null);

	private final AiSkillRunEntity run = new AiSkillRunEntity();

	private ManagedSkillExecutionService service;

	@BeforeEach
	void setup() {
		var snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{\"type\":\"object\",\"properties\":{}}", "{}", "frozen",
				List.of(), "{}", List.of());
		when(tokens.require("token", identity, consumer)).thenReturn(new SkillResolutionTokenStore.StoredResolution(
				identity, consumer, "8", "{}", snapshot, Long.MAX_VALUE));
		when(results.consumerType()).thenReturn("WORK_BRIEF");
		when(results.supportsConsumer(any())).thenReturn(true);
		when(results.authorizeInput(identity, consumer, "8", "{}")).thenReturn("{}");
		when(prompt.available(any(), any())).thenReturn(true);
		run.setId(10L);
		run.setReleaseId(2L);
		run.setStatus("QUEUED");
		when(index.reserve(any(), any(), any())).thenReturn(run);
		service = new ManagedSkillExecutionService(tokens, resolver, index, prompt, List.of(results),
				new ObjectMapper());
	}

	@Test void successSavesBusinessResultAndRevalidatesBeforeReturning(){
        when(index.claim(run)).thenReturn(true);
        when(prompt.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> { call.getArgument(5, Runnable.class).run(); return new ManagedPromptSkillExecutor.Result("{}","77"); });
        when(results.save(identity,consumer,"8","10","{}")).thenReturn("ref");
        var result=service.execute(request);
        assertEquals(RunStatus.SUCCEEDED,result.status());assertEquals("ref",result.resultRef());
        assertNull(result.executorVersion());assertTrue(result.usageJson().contains("UNAVAILABLE"));
        verify(resolver,times(3)).revalidate(any());
        verify(index).finish(eq(run),eq(RunStatus.SUCCEEDED),eq("ref"),isNull(),isNull(),any());
    }

	@Test
	void replayNeverCallsModelAgain() {
		run.setStatus("SUCCEEDED");
		run.setResultRef("ref");
		when(results.read(identity, consumer, "8", "10", "ref")).thenReturn("{}");
		assertEquals("{}", service.execute(request).resultJson());
		verify(prompt, never()).execute(any(), any(), any(), any(), any(), any());
	}

	@Test void uncertainExternalOutcomeIsRecordedWithoutRetry(){
        when(index.claim(run)).thenReturn(true);
        when(prompt.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> { call.getArgument(5, Runnable.class).run(); throw new IllegalStateException("connection lost"); });
        assertThrows(IllegalStateException.class,()->service.execute(request));
        verify(index).finish(run,RunStatus.UNKNOWN,null,ErrorCode.RESULT_UNKNOWN,null,null);
        verify(prompt,times(1)).execute(any(), any(), any(), any(), any(), any());
    }

	@Test
	void failedAuthorizationDoesNotReserveOrInvoke() {
		doThrow(new IllegalArgumentException("RELEASE_REVOKED")).when(resolver).revalidate(any());
		assertThrows(IllegalArgumentException.class, () -> service.execute(request));
		verify(index, never()).reserve(any(), any(), any());
		verify(prompt, never()).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
    void fallbackRechecksCurrentBindingAndBusinessInputWithoutCallingModel() {
        when(resolver.revalidate(any())).thenReturn(new SkillConsumerPolicy.BindingConfiguration("9", "2", true,
                "{}", "EXPLICIT", "{}", "{\"mode\":\"DETERMINISTIC\"}"));
        assertTrue(service.fallbackAllowed(request, "DETERMINISTIC"));
        verify(results).authorizeInput(identity, consumer, "8", "{}");
        when(resolver.revalidate(any())).thenReturn(new SkillConsumerPolicy.BindingConfiguration("9", "2", true,
                "{}", "EXPLICIT", "{}", "{\"mode\":\"NONE\"}"));
        assertFalse(service.fallbackAllowed(request, "DETERMINISTIC"));
        assertFalse(service.fallbackAllowed(request, "LEGACY_SKILL"));
        when(resolver.revalidate(any())).thenThrow(new IllegalArgumentException("BINDING_REVISION_CONFLICT"));
        assertThrows(IllegalArgumentException.class, () -> service.fallbackAllowed(request, "DETERMINISTIC"));
        verify(prompt, never()).execute(any(), any(), any(), any(), any(), any());
        verify(index, never()).reserve(any(), any(), any());
    }

	@Test
    void fallbackCannotUseSubstitutedBusinessInput() {
        when(resolver.revalidate(any())).thenReturn(new SkillConsumerPolicy.BindingConfiguration("9", "2", true,
                "{}", "EXPLICIT", "{}", "{\"mode\":\"DETERMINISTIC\"}"));
        when(results.authorizeInput(identity, consumer, "8", "{}"))
                .thenThrow(new IllegalArgumentException("INPUT_INVALID"));
        assertThrows(IllegalArgumentException.class, () -> service.fallbackAllowed(request, "DETERMINISTIC"));
    }

	@Test
    void persistenceFailureDoesNotAuthorizeBusinessFallbackAsRecordedOutcome() {
        when(index.claim(run)).thenReturn(true);
        when(prompt.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> { call.getArgument(5, Runnable.class).run(); throw new IllegalStateException("timeout"); });
        doThrow(new IllegalStateException("db unavailable")).when(index)
                .finish(run, RunStatus.UNKNOWN, null, ErrorCode.RESULT_UNKNOWN, null, null);
        var failure = assertThrows(IllegalStateException.class, () -> service.execute(request));
        assertFalse(failure instanceof SkillExecutionFailure);
        assertEquals(1, failure.getSuppressed().length);
    }

	@Test
	void aPreInvocationPolicyFailureIsRecordedAsFailedWithItsRunAndRelease() {
		when(index.claim(run)).thenReturn(true);
		when(prompt.execute(any(), any(), any(), any(), any(), any()))
			.thenThrow(new IllegalArgumentException("TASK_MODEL_BINDING_CHANGED"));
		var failure = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
		assertEquals(RunStatus.FAILED, failure.result().status());
		assertEquals(ErrorCode.EXECUTOR_VERSION_DRIFT, failure.result().errorCode());
		assertEquals("10", failure.result().skillRunId());
		assertEquals("2", failure.result().actualReleaseId());
		verify(index).finish(run, RunStatus.FAILED, null, ErrorCode.EXECUTOR_VERSION_DRIFT, null, null);
		verify(results, never()).save(any(), any(), any(), any(), any());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void inputPreparationFailureIsNotAnInvalidModelOutput() {
		when(index.claim(run)).thenReturn(true);
		when(prompt.execute(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalArgumentException("INPUT_INVALID"));
		var failure = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
		assertEquals(RunStatus.FAILED, failure.result().status());
		assertEquals(ErrorCode.INPUT_INVALID, failure.result().errorCode());
	}

	@Test
	void outputValidationAfterInvocationRemainsAFailedOutputAndNeverRetries() {
		when(index.claim(run)).thenReturn(true);
		when(prompt.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
			call.getArgument(5, Runnable.class).run(); throw new IllegalArgumentException("OUTPUT_INVALID");
		});
		var failure = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
		assertEquals(RunStatus.FAILED, failure.result().status());
		assertEquals(ErrorCode.OUTPUT_INVALID, failure.result().errorCode());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void successWithoutAnInvocationBoundaryCannotSaveAFabricatedResult() {
		when(index.claim(run)).thenReturn(true);
		when(prompt.execute(any(), any(), any(), any(), any(), any())).thenReturn(new ManagedPromptSkillExecutor.Result("{}", "77"));
		var failure = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
		assertEquals(RunStatus.FAILED, failure.result().status());
		verify(results, never()).save(any(), any(), any(), any(), any());
	}

	@Test
	void preparationErrorsCannotMasqueradeAsModelOutputToEnableFallback() {
		when(index.claim(run)).thenReturn(true);
		when(prompt.execute(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalArgumentException("OUTPUT_INVALID"));
		var failure = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
		assertEquals(RunStatus.FAILED, failure.result().status());
		assertEquals(ErrorCode.EXECUTOR_UNAVAILABLE, failure.result().errorCode());
	}

	@Test
    void streamWithoutTerminalIsUnknownAndCannotSaveOrRepeatInvocation() {
        when(index.claim(run)).thenReturn(true, false);
        when(prompt.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            call.getArgument(5, Runnable.class).run();
            throw new IllegalArgumentException("OUTPUT_INCOMPLETE");
        });
        var failure = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
        assertEquals(RunStatus.UNKNOWN, failure.result().status());
        assertEquals(ErrorCode.RESULT_UNKNOWN, failure.result().errorCode());
        verify(index).finish(run, RunStatus.UNKNOWN, null, ErrorCode.RESULT_UNKNOWN, null, null);
        run.setStatus("UNKNOWN");run.setErrorCode("RESULT_UNKNOWN");
        var replay = service.execute(request);
        assertEquals(RunStatus.UNKNOWN, replay.status());
        verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
        verify(results, never()).save(any(), any(), any(), any(), any());
    }

	@Test void actualExecutorVersionIsPersistedAndReturnedWithoutInventingModelUsage() {
        when(index.claim(run)).thenReturn(true);
        when(prompt.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            call.getArgument(5, Runnable.class).run();
            return new ManagedPromptSkillExecutor.Result("{}", null, "configuration-digest");
        });
        when(results.save(identity,consumer,"8","10","{}")).thenReturn("ref");
        var result=service.execute(request);
        assertEquals("configuration-digest",result.executorVersion());
        assertTrue(result.usageJson().contains("\"modelId\":null"));
        verify(index).finish(eq(run),eq(RunStatus.SUCCEEDED),eq("ref"),isNull(),eq("configuration-digest"),any());
    }

}
