package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedSkillBindingServiceTest {

	private final AiSkillBindingMapper bindings = mock(AiSkillBindingMapper.class);

	private final AiSkillBindingRevisionMapper revisions = mock(AiSkillBindingRevisionMapper.class);

	private final AiSkillReleaseMapper releases = mock(AiSkillReleaseMapper.class);

	private final SkillConsumerPolicy policy = mock(SkillConsumerPolicy.class);

	private final AiSkillBindingEntity binding = new AiSkillBindingEntity();

	private final AiSkillReleaseEntity release = new AiSkillReleaseEntity();

	private ManagedSkillBindingService service;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		when(user.getUsername()).thenReturn("operator");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
		binding.setId(7L);
		binding.setTenantId("42");
		binding.setRevision(2L);
		binding.setConsumerType("TEST");
		binding.setConsumerId("consumer");
		binding.setSlotCode("SUMMARY");
		binding.setBindingKey("fixed");
		when(bindings.lockOwned("42", 7L)).thenReturn(binding);
		var revision = new AiSkillBindingRevisionEntity();
		revision.setId(9L);
		revision.setReleaseId(11L);
		revision.setEnabled(1);
		revision.setParameterJson("{}");
		when(revisions.find("42", 7L, 9L)).thenReturn(revision);
		release.setStatus("PUBLISHED");
		release.setId(11L);
		release.setSkillId(12L);
		release.setReleaseDigest("digest");
		release.setRuntimeKind("PROMPT");
		release.setExecutorRef("{\"executorKey\":\"PROMPT\"}");
		release.setResourceRefs("[]");
		release.setLimitsJson("{}");
		release.setAllowedParameters("[\"count\"]");
		release.setInputSchema(
				"{\"type\":\"object\",\"properties\":{\"count\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":5}}}");
		when(releases.findOwned("42", 11L)).thenReturn(release);
		when(policy.consumerType()).thenReturn("TEST");
		service = new ManagedSkillBindingService(bindings, revisions, releases, List.of(policy), new ObjectMapper());
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void staleVersionAndFrozenConsumerNeverChangePointer() {
		assertThrows(IllegalStateException.class, () -> service.activate(7L, 9L, 1));
		binding.setConsumerType("UNIFIED_ASSISTANT");
		assertThrows(IllegalArgumentException.class, () -> service.activate(7L, 9L, 2));
		verify(bindings, never()).activateRevision(any(), any(), any(), any(), any());
	}

	@Test
	void revokedReleaseAndMissingAdapterCannotActivate() {
		release.setStatus("REVOKED");
		assertThrows(IllegalArgumentException.class, () -> service.activate(7L, 9L, 2));
		binding.setConsumerType("UNKNOWN");
		assertThrows(IllegalArgumentException.class, () -> service.activate(7L, 9L, 2));
		verify(bindings, never()).activateRevision(any(), any(), any(), any(), any());
	}

	@Test
    void authorizedSwitchUsesExpectedRevisionAndReportsLostCompareAndSet() {
        when(bindings.activateRevision("42",7L,9L,2L,"operator")).thenReturn(1).thenReturn(0);
        service.activate(7L,9L,2);
        verify(policy).validateActivation(eq("42"),eq("3"),any(),any(),any());
        assertThrows(IllegalStateException.class, () -> service.activate(7L,9L,2));
    }

	@Test
    void draftCreationDoesNotEnableOrRequireExecutableAdapterButStillAuthorizes() {
        when(bindings.insert(any(AiSkillBindingEntity.class))).thenReturn(1);
        when(revisions.nextRevision(any(),any())).thenReturn(1L);
        when(revisions.insertRevision(any())).thenReturn(1);
        var consumer = new com.deepdimen.matrix.skill.api.SkillContract.Consumer("TEST","consumer","SUMMARY","fixed");
        var input = new ManagedSkillBindingService.RevisionInput(11L,true,"{}","EXPLICIT","{\"count\":2}","{}");
        assertNotNull(service.createDraft(consumer,input));
        verify(policy).authorizeBinding(eq("42"),eq("3"),eq(consumer),any());
        verify(policy,never()).validateActivation(any(),any(),any(),any(),any());
        verify(bindings,never()).activateRevision(any(),any(),any(),any(),any());
        var captured = org.mockito.ArgumentCaptor.forClass(AiSkillBindingEntity.class);
        verify(bindings).insert(captured.capture());
        assertNull(captured.getValue().getActiveRevisionId());
    }

	@Test
    void revisionRejectsUndeclaredAndInvalidParameterBeforeSaving() {
        when(revisions.nextRevision(any(),any())).thenReturn(1L);
        for (String parameters : List.of("{\"executorRef\":{}}","{\"count\":9}","{\"count\":\"2\"}")) {
            var input = new ManagedSkillBindingService.RevisionInput(11L,true,"{}","EXPLICIT",parameters,"{}");
            assertThrows(IllegalArgumentException.class, () -> service.revise(7L,2,input));
        }
        verify(revisions,never()).insertRevision(any());
    }

	@Test
    void historyReadRequiresConsumerManagementAuthorizationEvenForEmptyPage() {
        when(bindings.selectOne(any())).thenReturn(binding);
        var revision = new AiSkillBindingRevisionEntity();revision.setId(9L);revision.setReleaseId(11L);revision.setEnabled(1);
        when(revisions.listOwned("42",7L,null)).thenReturn(List.of(revision));
        when(revisions.listOwned("42",7L,8L)).thenReturn(List.of());
        assertTrue(service.detail(7L,8L).revisions().isEmpty());
        verify(policy).authorizeBinding(eq("42"),eq("3"),any(),any());
        doThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED")).when(policy).authorizeBinding(any(),any(),any(),any());
        assertThrows(IllegalArgumentException.class,()->service.detail(7L,8L));
        binding.setTenantId("43");
        assertThrows(IllegalArgumentException.class,()->service.detail(7L,null));
    }

	@Test
	void preflightChecksActivationWithoutSavingAnyDraftOrChangingPointer() {
		var consumer = new com.deepdimen.matrix.skill.api.SkillContract.Consumer("TEST", "consumer", "SUMMARY",
				"fixed");
		var input = new ManagedSkillBindingService.RevisionInput(11L, true, "{}", "EXPLICIT", "{\"count\":2}", "{}");
		service.validateDraft(consumer, input);
		verify(policy).validateActivation(eq("42"), eq("3"), eq(consumer), any(), any());
		verifyZeroInteractions(bindings, revisions);
		release.setStatus("REVOKED");
		assertThrows(IllegalArgumentException.class, () -> service.validateDraft(consumer, input));
		release.setStatus("PUBLISHED");
		assertThrows(IllegalArgumentException.class, () -> service.validateDraft(consumer,
				new ManagedSkillBindingService.RevisionInput(11L, true, "{} {}", "EXPLICIT", "{}", "{}")));
		verifyZeroInteractions(bindings, revisions);
	}

	@Test void activationCallbackReceivesTheSameValidatedSnapshotAndItsFailurePropagates() {
        when(bindings.activateRevision("42",7L,9L,2L,"operator")).thenReturn(1);
        service.activate(7L,9L,2);
        var checked=org.mockito.ArgumentCaptor.forClass(com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot.class);
        var activated=org.mockito.ArgumentCaptor.forClass(com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot.class);
        verify(policy).validateActivation(eq("42"),eq("3"),any(),any(),checked.capture());
        verify(policy).bindingActivated(eq("42"),eq("3"),any(),any(),activated.capture());
        assertSame(checked.getValue(),activated.getValue());
        assertEquals("11",activated.getValue().releaseId()); assertEquals("9",activated.getValue().bindingRevisionId());
        doThrow(new IllegalArgumentException("TASK_POLICY_VERSION_CONFLICT")).when(policy).bindingActivated(any(),any(),any(),any(),any());
        assertThrows(IllegalArgumentException.class,()->service.activate(7L,9L,2));
    }

	@Test
	void snapshotCallbackKeepsExistingConsumerMigrationHookCompatible() {
		var existing = mock(SkillConsumerPolicy.class, CALLS_REAL_METHODS);
		var consumer = new com.deepdimen.matrix.skill.api.SkillContract.Consumer("WORK_BRIEF", "7", "WORK_BRIEF",
				"default");
		var configuration = new SkillConsumerPolicy.BindingConfiguration("9", "11", true, "{}", "EXPLICIT", "{}", "{}");
		existing.bindingActivated("42", "3", consumer, configuration, null);
		verify(existing).bindingActivated("42", "3", consumer, configuration);
	}

    @Test void emptyIdentityDraftRequiresConsumerAuthorizationAndCannotBecomeExecutable() {
        var consumer=new com.deepdimen.matrix.skill.api.SkillContract.Consumer("TEST","consumer","COMPANION","default");
        when(bindings.insert(any(AiSkillBindingEntity.class))).thenReturn(1);
        assertNotNull(service.createIdentityDraft(consumer));
        verify(policy).authorizeEmptyBinding("42","3",consumer);
        var capture=org.mockito.ArgumentCaptor.forClass(AiSkillBindingEntity.class);
        verify(bindings).insert(capture.capture());
        assertNull(capture.getValue().getActiveRevisionId());
        assertEquals(0L,capture.getValue().getRevision());
        verifyNoMoreInteractions(revisions,releases);
    }

    @Test void refusedEmptyConsumerDoesNotPersistAnIdentity() {
        var consumer=new com.deepdimen.matrix.skill.api.SkillContract.Consumer("TEST","consumer","COMPANION","default");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED"))
            .when(policy).authorizeEmptyBinding("42","3",consumer);
        assertThrows(IllegalArgumentException.class,()->service.createIdentityDraft(consumer));
        verify(bindings,never()).insert(any(AiSkillBindingEntity.class));
        verifyNoMoreInteractions(revisions,releases);
    }

}
