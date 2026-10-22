package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagedPromptInvocationPortTest {

	final SkillPromptInvocationPort port = mock(SkillPromptInvocationPort.class);

	final ObjectProvider<AssistantModelGateway> gateways = mock(ObjectProvider.class);

	final TenantUserDirectory users = mock(TenantUserDirectory.class);

	final Consumer consumer = new Consumer("INFORMATION", "7", "ANALYZE", "default");

	final Identity actor = new Identity("42", "information-collector", "information-collector", "run");

	final ResolvedSkillSnapshot snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", null, "fixed-model", null), "{\"type\":\"object\"}", "{\"type\":\"object\"}",
			"original", List.of(), "{}", List.of());

	ManagedPromptSkillExecutor executor(List<SkillPromptInvocationPort> ports) {
        when(port.consumerType()).thenReturn("INFORMATION");
        return new ManagedPromptSkillExecutor(gateways,users,new ObjectMapper(),List.of(),ports);
    }

	@Test
	void absentUnavailableOrAmbiguousPortCannotBorrowDefaultGateway() {
		assertFalse(executor(List.of()).available(consumer, snapshot));
		assertFalse(executor(List.of(port)).available(consumer, snapshot));
		when(port.available(consumer, snapshot)).thenReturn(true);
		assertFalse(executor(List.of(port, port)).available(consumer, snapshot));
		verifyZeroInteractions(gateways, users);
	}

	@Test
	void selectedPortOwnsServiceIdentityAndReportsInvocation() {
		var executor = executor(List.of(port));
		when(port.available(consumer, snapshot)).thenReturn(true);
		var callback = mock(Runnable.class);
		when(port.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
			call.getArgument(5, Runnable.class).run();
			return new SkillPromptInvocationPort.Output("{}", "fixed-model");
		});
		assertEquals("fixed-model", executor.execute("10", actor, consumer, snapshot, "{}", callback).modelId());
		verify(callback).run();
		verifyZeroInteractions(gateways, users);
	}

	@Test
	void wrongModelOrInvalidOutputCannotBecomeSuccess() {
		var executor = executor(List.of(port));
		when(port.available(consumer, snapshot)).thenReturn(true);
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new SkillPromptInvocationPort.Output("{}", "other"));
		assertThrows(IllegalArgumentException.class, () -> executor.execute("10", actor, consumer, snapshot, "{}"));
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new SkillPromptInvocationPort.Output("{}{}", "fixed-model"));
		assertThrows(IllegalArgumentException.class, () -> executor.execute("10", actor, consumer, snapshot, "{}"));
	}

	@Test
	void difyPortReportsConfigurationWithoutFabricatingAModelId() {
		var target = executor(List.of(port));
		when(port.consumerType()).thenReturn("MARKET_AGENT");
		var market = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");
		var release = difyRelease();
		when(port.available(market, release)).thenReturn(true);
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new SkillPromptInvocationPort.Output("{}", null, "a".repeat(64)));
		var result = target.execute("10", actor, market, release, "{}");
		assertNull(result.modelId());
		assertEquals("a".repeat(64), result.executorVersion());
		verifyZeroInteractions(gateways, users);
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new SkillPromptInvocationPort.Output("{}", null, "b".repeat(64)));
		assertThrows(IllegalArgumentException.class, () -> target.execute("10", actor, market, release, "{}"));
	}

	@Test
	void externalReferenceCannotInvokeAnUnrelatedBusinessPort() {
		var target = executor(List.of(port));
		when(port.available(any(), any())).thenReturn(true);
		assertFalse(target.available(consumer, difyRelease()));
		assertThrows(IllegalArgumentException.class, () -> target.execute("10", actor, consumer, difyRelease(), "{}"));
		verify(port, never()).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void knowledgeFixedReplyDoesNotFabricateActualModel() {
		var target = executor(List.of(port));
		when(port.consumerType()).thenReturn("MARKET_AGENT");
		var market = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");
		var refs = List.of(new ResourceRef("agent:88", "a".repeat(64), "a".repeat(64)),
				new ResourceRef("model:77", "b".repeat(64), "b".repeat(64)),
				new ResourceRef("dataset:31", "c".repeat(64), "c".repeat(64)));
		String digest = KnowledgeSkillRelease.configurationDigest(refs);
		var release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", "KNOWLEDGE", "77", digest), "{\"type\":\"object\"}", "{\"type\":\"object\"}",
				"rules", refs, "{}", List.of());
		when(port.available(market, release)).thenReturn(true);
		when(port.execute(any(), any(), any(), any(), any(), any())).thenReturn(new SkillPromptInvocationPort.Output(
				"{}", null, digest, SkillPromptInvocationPort.InvocationMode.FIXED_REPLY));
		assertNull(target.execute("10", actor, market, release, "{}").modelId());
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new SkillPromptInvocationPort.Output("{}", null, digest));
		assertThrows(IllegalArgumentException.class, () -> target.execute("10", actor, market, release, "{}"));
		assertThrows(IllegalArgumentException.class, () -> new SkillPromptInvocationPort.Output("{}", "77", digest,
				SkillPromptInvocationPort.InvocationMode.FIXED_REPLY));
		verifyZeroInteractions(gateways, users);
	}

	private ResolvedSkillSnapshot difyRelease() {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", "DIFY", "app-1", "a".repeat(64)), "{\"type\":\"object\"}",
				"{\"type\":\"object\"}", "original", List.of(new ResourceRef("app-1", "config-1", "a".repeat(64))),
				"{}", List.of());
	}

}
