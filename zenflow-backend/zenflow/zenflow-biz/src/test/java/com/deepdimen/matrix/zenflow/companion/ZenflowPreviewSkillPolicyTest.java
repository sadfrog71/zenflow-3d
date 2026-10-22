package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.zenflow.admin.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class ZenflowPreviewSkillPolicyTest {

	final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	final ZenflowAdminAccess access = mock(ZenflowAdminAccess.class);

	final ZenflowModelService models = mock(ZenflowModelService.class);

	final MatrixUser administrator = mock(MatrixUser.class);

	final ObjectMapper json = new ObjectMapper();

	@SuppressWarnings("unchecked")
	final ObjectProvider<SkillPromptInvocationPort> ports = mock(ObjectProvider.class);

	@SuppressWarnings("unchecked")
	final ObjectProvider<ZenflowPreviewLease> leases = mock(ObjectProvider.class);

	final Map<String, Object> model = Map.of("modelName", "fixture", "provider", "OPENAI", "baseUrl",
			"https://example.org/v1", "temperature", 0.6, "maxOutputTokens", 2048, "allowPrivateContext", false,
			"version", 1);

	Map<String, Object> preview() {
		return Map.of("companion_revision_id", 123L, "skill_release_id", 888L, "release_digest", "a".repeat(64),
				"model_id", "456", "model_config_digest", new ZenflowCompanionPackage(json).modelDigest("456", model));
	}

	ZenflowPreviewSkillPolicy policy() {
		var product = new ZenflowProperties();
		product.setTenantId(1);
		when(access.require()).thenReturn(administrator);
		when(administrator.getTenantId()).thenReturn(1L);
		when(administrator.getId()).thenReturn(42L);
		when(models.publishedSnapshot("456")).thenReturn(model);
		when(jdbc.queryForList(anyString(), eq("1"), eq("42"), eq("900"))).thenReturn(List.of(preview()));
		when(jdbc.queryForList(anyString(), eq("1"), eq("123"))).thenReturn(List.of(preview()));
		when(ports.stream()).thenAnswer(call -> java.util.stream.Stream.empty());
		when(leases.stream()).thenAnswer(call -> java.util.stream.Stream.empty());
		return new ZenflowPreviewSkillPolicy(jdbc, product, access, models, json, ports, leases);
	}

	SkillConsumerPolicy.BindingConfiguration binding(String parameters) {
		return new SkillConsumerPolicy.BindingConfiguration("999", "888", true, "{}", "ON_DEMAND", parameters, "{}");
	}

	@Test
	void trialManagementRequiresExactAdministratorTenantAndPreviewConsumer() {
		var policy = policy();
		policy.authorizeEmptyBinding("1", "42", ZenflowPreviewSkillPolicy.consumer("900"));
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeEmptyBinding("1", "43", ZenflowPreviewSkillPolicy.consumer("900")));
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeEmptyBinding("2", "42", ZenflowPreviewSkillPolicy.consumer("900")));
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeEmptyBinding("1", "42", ZenflowCompanionSkillPolicy.consumer("900")));
	}

	@Test
	void deletedExpiredOrClosedTrialCannotCreateBinding() {
		var policy = policy();
		when(jdbc.queryForList(anyString(), eq("1"), eq("42"), eq("900"))).thenReturn(List.of());
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeEmptyBinding("1", "42", ZenflowPreviewSkillPolicy.consumer("900")));
	}

	@Test
	void immutableReleaseAndModelConfigurationCannotBeOverridden() {
		var policy = policy();
		var consumer = ZenflowPreviewSkillPolicy.consumer("900");
		policy.authorizeBinding("1", "42", consumer, binding("{\"companionRevisionId\":\"123\"}"));
		assertThrows(IllegalArgumentException.class, () -> policy.authorizeBinding("1", "42", consumer,
				binding("{\"companionRevisionId\":\"123\",\"systemPrompt\":\"override\"}")));
		var changed = new HashMap<>(model);
		changed.put("version", 2);
		when(models.publishedSnapshot("456")).thenReturn(changed);
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeBinding("1", "42", consumer, binding("{\"companionRevisionId\":\"123\"}")));
	}

	@Test
	void validTrialUsesItsIndependentCurrentBindingRatherThanConsumerHistoryPinning() {
		var policy = policy();
		var lease = mock(ZenflowPreviewLease.class);
		var identity = new Identity("1", "42", "42", "correlation");
		var consumer = ZenflowPreviewSkillPolicy.consumer("900");
		when(leases.stream()).thenAnswer(call -> java.util.stream.Stream.of(lease));
		when(lease.permits(identity, consumer, "trial")).thenReturn(true);
		assertTrue(policy.permitsBackgroundExecution(identity, consumer, "trial"));
		assertNull(policy.pinnedBindingRevision(identity, consumer, "trial"));
		verify(lease, never()).pinned(any(), any(), anyString());
	}

	@Test
	void fabricatedRunOrAppSubjectDoesNotAuthorizeBackgroundExecution() {
		var policy = policy();
		var consumer = ZenflowPreviewSkillPolicy.consumer("900");
		assertFalse(policy.permitsBackgroundExecution(new Identity("1", "42", "42", "correlation"), consumer,
				"fabricated"));
		assertNull(policy.pinnedBindingRevision(new Identity("1", "42", "42", "correlation"), consumer, "fabricated"));
		assertFalse(policy.permitsBackgroundExecution(new Identity("1", "42", "42", "correlation", "APP_USER"),
				consumer, "fabricated"));
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeExecution(new Identity("1", "42", "42", "correlation", "APP_USER"), consumer,
						binding("{}"), null));
		assertThrows(IllegalArgumentException.class, () -> policy
			.authorizeExecution(new Identity("1", "42", "42", "correlation"), consumer, binding("{}"), null));
	}

	@Test
	void activationCallbackDoesNotChangeOnlineConfiguration() {
		var policy = policy();
		clearInvocations(jdbc);
		policy.bindingActivated("1", "42", ZenflowPreviewSkillPolicy.consumer("900"),
				binding("{\"companionRevisionId\":\"123\"}"));
		verifyNoMoreInteractions(jdbc);
	}

	@Test
	void activationRequiresTheDedicatedPreviewInvocationPort() {
		var policy = policy();
		var consumer = ZenflowPreviewSkillPolicy.consumer("900");
		var binding = binding("{\"companionRevisionId\":\"123\"}");
		var release = new ResolvedSkillSnapshot("7", "888", "a".repeat(64), "999", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, "456", null), "{}", "{}", "instructions", List.of(), "{}",
				List.of("companionRevisionId"));
		assertThrows(IllegalArgumentException.class,
				() -> policy.validateActivation("1", "42", consumer, binding, release));
		var port = mock(SkillPromptInvocationPort.class);
		when(port.consumerType()).thenReturn(ZenflowCompanionSkillPolicy.TYPE);
		when(port.available(consumer, release)).thenReturn(true);
		when(ports.stream()).thenAnswer(call -> java.util.stream.Stream.of(port));
		assertThrows(IllegalArgumentException.class,
				() -> policy.validateActivation("1", "42", consumer, binding, release));
		when(port.consumerType()).thenReturn(ZenflowPreviewSkillPolicy.TYPE);
		policy.validateActivation("1", "42", consumer, binding, release);
	}

}
