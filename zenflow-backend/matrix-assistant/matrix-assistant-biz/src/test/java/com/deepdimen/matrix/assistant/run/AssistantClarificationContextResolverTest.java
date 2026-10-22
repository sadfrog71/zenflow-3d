package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.*;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.*;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantClarificationContextResolverTest {

	private final Instant now = Instant.parse("2026-09-26T00:00:00Z");

	private final AssistantClarificationContextResolver resolver = new AssistantClarificationContextResolver();

	private final FrozenCapabilityCatalog catalog = new FrozenCapabilityCatalog(List.of(), List.of(), "catalog", now);

	private AssistantRunRecord run(String id, String tenant, String user, String org, List<AssistantMessage> history,
			boolean completed) {
		var identity = new IdentityEnvelope(tenant, user, org, List.of(), List.of(), List.of(), "corr", "assistant");
		var input = new AssistantRunRecord.RunInputSnapshot(identity, "查询", List.of(), false, null, null, null, history,
				null, null, null, List.of(), SourceMode.AUTO);
		var state = new ClarificationState(id, 1, false, "补充对象", List.of(), now.plusSeconds(600).toEpochMilli());
		var plan = new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan(null, "p", id,
				SourceMode.AUTO, PlanningDisposition.AMBIGUOUS, List.of(), null, List.of(), "catalog", null, now, null,
				state);
		return new AssistantRunRecord(id, "session", tenant, user, id, "hash", "u-" + id, "a-" + id,
				completed ? RunStatus.COMPLETED : RunStatus.ACCEPTED, DispatchStatus.PENDING, input, null, "", 0, null,
				null, null, null, null, now.plusSeconds(completed ? 0 : 1), now, completed ? now : null, null,
				"catalog", completed ? plan : null, null);
	}

	private AssistantMessage message(String id, long seq, MessageRole role) {
		return new AssistantMessage(id, "session", role, "text", MessageStatus.COMPLETED, seq, null, now);
	}

	@Test
	void onlyImmediateFrozenAssistantMessageMaySupplyContext() {
		var prior = run("prior", "t", "u", "o", List.of(), true);
		assertTrue(resolver.continues("改为今天", prior.executionPlan(), catalog));
		assertFalse(resolver.continues("另外查询今天的资料", prior.executionPlan(), catalog));
		var current = run("current", "t", "u", "o", List.of(message("a-prior", 1, MessageRole.ASSISTANT)), false);
		assertTrue(resolver.resolve(current, List.of(prior), catalog, now.plusSeconds(1)).isPresent());
		var intervening = run("current", "t", "u", "o",
				List.of(message("a-prior", 1, MessageRole.ASSISTANT), message("other", 2, MessageRole.ASSISTANT)),
				false);
		assertTrue(resolver.resolve(intervening, List.of(prior), catalog, now.plusSeconds(1)).isEmpty());
		assertTrue(resolver.resolve(current, List.of(prior, prior), catalog, now.plusSeconds(1)).isEmpty());
		assertTrue(resolver.resolve(current, List.of(prior), catalog, now.plusSeconds(600)).isEmpty());
	}

	@Test
	void tenantUserOrganizationAndCatalogChangesDiscardContext() {
		var current = run("current", "t", "u", "o", List.of(message("a-prior", 1, MessageRole.ASSISTANT)), false);
		for (var prior : List.of(run("prior", "other", "u", "o", List.of(), true),
				run("prior", "t", "other", "o", List.of(), true), run("prior", "t", "u", "other", List.of(), true)))
			assertTrue(resolver.resolve(current, List.of(prior), catalog, now.plusSeconds(1)).isEmpty());
		assertTrue(resolver
			.resolve(current, List.of(run("prior", "t", "u", "o", List.of(), true)),
					new FrozenCapabilityCatalog(List.of(), List.of(), "changed", now), now.plusSeconds(1))
			.isEmpty());
	}

}
