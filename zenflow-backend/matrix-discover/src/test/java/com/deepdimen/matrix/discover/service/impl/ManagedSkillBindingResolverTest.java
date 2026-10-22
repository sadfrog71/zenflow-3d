package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.discover.skill.*;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedSkillBindingResolverTest {

	private final AiSkillBindingMapper bindings = mock(AiSkillBindingMapper.class);

	private final AiSkillBindingRevisionMapper revisions = mock(AiSkillBindingRevisionMapper.class);

	private final AiSkillReleaseMapper releases = mock(AiSkillReleaseMapper.class);

	private final SkillConsumerPolicy policy = mock(SkillConsumerPolicy.class);

	private final SkillResolutionTokenStore tokens = mock(SkillResolutionTokenStore.class);

	private final Identity identity = new Identity("42", "3", "3", "run");

	private final Consumer consumer = new Consumer("TEST", "7", "SUMMARY", "default");

	private AiSkillReleaseEntity release;

	private ManagedSkillBindingResolver resolver;

	@BeforeEach
	void setup() throws Exception {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
		var binding = new AiSkillBindingEntity();
		binding.setId(7L);
		binding.setActiveRevisionId(9L);
		when(bindings.selectOne(any())).thenReturn(binding);
		var revision = new AiSkillBindingRevisionEntity();
		revision.setId(9L);
		revision.setReleaseId(11L);
		revision.setEnabled(1);
		revision.setParameterJson("{\"count\":2}");
		when(revisions.find("42", 7L, 9L)).thenReturn(revision);
		var json = new ObjectMapper();
		ObjectNode manifest = (ObjectNode) json.readTree("""
				{"runtimeKind":"PROMPT_INJECTION","declared":{"managedRelease":{
				  "inputSchema":{"type":"object","properties":{"count":{"type":"integer","maximum":5}}},
				  "outputSchema":{"type":"object","properties":{}},
				  "executorRef":{"executorKey":"PROMPT"},"allowedParameters":["count"]}}}
				""");
		var parsed = new AgentSkillPackageParser.ParsedPackage("skill", "test", Map.of(), "frozen", manifest,
				json.createObjectNode(), List.of(), "hash", 1, "skill.zip", List.of());
		release = new ManagedSkillReleaseSnapshot(json).prompt(parsed, "hash");
		release.setId(11L);
		release.setSkillId(12L);
		release.setStatus("PUBLISHED");
		when(releases.findOwned("42", 11L)).thenReturn(release);
		when(policy.consumerType()).thenReturn("TEST");
		resolver = new ManagedSkillBindingResolver(bindings, revisions, releases, List.of(policy), tokens, json);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void resolvesPinnedReleaseAndMergesOnlyPermittedParameters() {
		resolver.resolve(new ResolveRequest(identity, consumer, "7", "business", "{\"count\":3}"));
		var capture = org.mockito.ArgumentCaptor.forClass(ResolvedSkillSnapshot.class);
		verify(tokens).issue(eq(identity), eq(consumer), eq("business"), eq("{\"count\":3}"), capture.capture());
		assertEquals("11", capture.getValue().releaseId());
		assertEquals("9", capture.getValue().bindingRevisionId());
		assertEquals("frozen", capture.getValue().instructionSnapshot());
		verify(policy).authorizeExecution(eq(identity), eq(consumer), any(), any());
	}

	@Test void registeredProductRunKeepsPinnedRevisionWhenCurrentPointerChanges() {
		var product=new Consumer("ZENFLOW_COMPANION","7","CHAT","default");
		when(policy.consumerType()).thenReturn("ZENFLOW_COMPANION");
		when(policy.pinnedBindingRevision(identity,product,"registered")).thenReturn("8");
		var old=new AiSkillBindingRevisionEntity();old.setId(8L);old.setReleaseId(11L);old.setEnabled(1);old.setParameterJson("{\"count\":2}");
		when(revisions.find("42",7L,8L)).thenReturn(old);
		resolver.resolve(new ResolveRequest(identity,product,"7","registered","{}"));
		var capture=org.mockito.ArgumentCaptor.forClass(ResolvedSkillSnapshot.class);
		verify(tokens).issue(eq(identity),eq(product),eq("registered"),eq("{\"count\":2}"),capture.capture());
		assertEquals("8",capture.getValue().bindingRevisionId());
	}
	@Test void pinningCannotBeEnabledForOtherConsumersOrByMismatchedSubjectType() {
		when(policy.pinnedBindingRevision(identity,consumer,"business")).thenReturn("8");
		assertThrows(IllegalArgumentException.class,()->resolver.resolve(new ResolveRequest(identity,consumer,"7","business","{}")));
		var forged=new Identity("42","3","3","run","APP_USER");
		assertThrows(IllegalArgumentException.class,()->resolver.resolve(new ResolveRequest(forged,consumer,"7","business","{}")));
	}

	@Test
	void rejectsRevokedOrTamperedReleaseBeforeIssuingToken() {
		release.setStatus("REVOKED");
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(new ResolveRequest(identity, consumer, "7", "business", null)));
		release.setStatus("PUBLISHED");
		release.setContentSnapshot("tampered");
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(new ResolveRequest(identity, consumer, "7", "business", null)));
		verifyZeroInteractions(tokens);
	}

	@Test
	void rejectsSpoofedActorAndUnauthorizedOverrides() {
		assertThrows(IllegalArgumentException.class, () -> resolver
			.resolve(new ResolveRequest(new Identity("42", "4", "3", "run"), consumer, "7", "business", null)));
		for (String params : List.of("{\"executorRef\":{}}", "{\"count\":6}")) {
			assertThrows(IllegalArgumentException.class,
					() -> resolver.resolve(new ResolveRequest(identity, consumer, "7", "business", params)));
		}
		verifyZeroInteractions(tokens);
	}

	@Test
	void principalWithoutAuthenticatedSessionCannotResolve() {
		var current = SecurityContextHolder.getContext().getAuthentication();
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(current.getPrincipal(), "n/a"));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(new ResolveRequest(identity, consumer, "7", "business", null)));
		verifyZeroInteractions(tokens);
	}

	@Test
	void capabilityInspectionReusesAuthorizationWithoutIssuingAnyToken() {
		var snapshot = resolver.inspect(identity, consumer);
		assertEquals("11", snapshot.releaseId());
		assertEquals("9", snapshot.bindingRevisionId());
		verify(policy).authorizeInspection(eq(identity), eq(consumer), any(), any());
		verify(policy, never()).authorizeExecution(any(), any(), any(), any());
		verifyZeroInteractions(tokens);
		release.setStatus("REVOKED");
		assertThrows(IllegalArgumentException.class, () -> resolver.inspect(identity, consumer));
		verify(policy, never()).authorizeExecution(any(), any(), any(), any());
		verifyZeroInteractions(tokens);
	}

	@Test
	void missingSessionRequiresExplicitBusinessWorkerApprovalAndStillChecksBindingAuthorization() {
		SecurityContextHolder.clearContext();
		var request = new ResolveRequest(identity, consumer, "7", "business", null);
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));
		verifyZeroInteractions(bindings, tokens);
		when(policy.permitsBackgroundExecution(identity, consumer, "business")).thenReturn(true);
		resolver.resolve(request);
		verify(policy).authorizeExecution(eq(identity), eq(consumer), any(), any());
		verify(tokens).issue(eq(identity), eq(consumer), eq("business"), any(), any());
		doThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED")).when(policy)
			.authorizeExecution(any(), any(), any(), any());
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));
		verify(tokens, times(1)).issue(any(), any(), any(), any(), any());
	}

	@Test
	void backgroundApprovalCannotOverrideTenantOrFrozenConsumer() {
		SecurityContextHolder.clearContext();
		when(policy.permitsBackgroundExecution(any(), any(), any())).thenReturn(true);
		TenantContextHolder.setTenantId(43L);
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(new ResolveRequest(identity, consumer, null, "business", null)));
		TenantContextHolder.setTenantId(42L);
		var frozen = new Consumer("UNIFIED_ASSISTANT", "7", "SUMMARY", "default");
		assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(new ResolveRequest(identity, frozen, null, "business", null)));
		verify(policy, never()).permitsBackgroundExecution(any(), any(), any());
		verifyZeroInteractions(tokens, bindings);
	}

	@Test void existingSessionCannotUseWorkerApprovalToSpoofAnotherActor() {
        when(policy.permitsBackgroundExecution(any(),any(),any())).thenReturn(true);
        var spoofed=new Identity("42","4","4","run");
        assertThrows(IllegalArgumentException.class,()->resolver.resolve(new ResolveRequest(spoofed,consumer,null,"business",null)));
        var auth=SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(auth.getPrincipal(),"n/a"));
        assertThrows(IllegalArgumentException.class,()->resolver.resolve(new ResolveRequest(identity,consumer,null,"business",null)));
        verify(policy,never()).permitsBackgroundExecution(any(),any(),any());verifyZeroInteractions(tokens);
    }

	@Test
	void backgroundLeaseMustStillBeApprovedOnEveryRevalidation() {
		SecurityContextHolder.clearContext();
		when(policy.permitsBackgroundExecution(identity, consumer, "business")).thenReturn(true);
		resolver.resolve(new ResolveRequest(identity, consumer, null, "business", null));
		var snapshot = org.mockito.ArgumentCaptor.forClass(ResolvedSkillSnapshot.class);
		verify(tokens).issue(eq(identity), eq(consumer), eq("business"), any(), snapshot.capture());
		when(policy.permitsBackgroundExecution(identity, consumer, "business")).thenReturn(false);
		var prior = new SkillResolutionTokenStore.StoredResolution(identity, consumer, "business", "{\"count\":2}",
				snapshot.getValue(), 0);
		assertThrows(IllegalArgumentException.class, () -> resolver.revalidate(prior));
	}

}
