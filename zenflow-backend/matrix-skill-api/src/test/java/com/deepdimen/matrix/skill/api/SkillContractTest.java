package com.deepdimen.matrix.skill.api;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillContractTest {

	@Test
	void snapshotUsesFixedReleaseAndImmutableResourceList() {
		var refs = new ArrayList<>(List.of(new SkillContract.ResourceRef("template", "1", "digest")));
		var snapshot = new SkillContract.ResolvedSkillSnapshot("skill", "release", "digest", "revision",
				SkillContract.RuntimeKind.PROMPT, new SkillContract.ExecutorRef("knowledge-answer", null, null, null),
				"{}", "{}", "instructions", refs, "{}", List.of("period"));
		refs.clear();
		assertEquals(1, snapshot.resourceRefs().size());
		assertNull(snapshot.executorRef().provider());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.allowedParameters().add("systemPrompt"));
	}

	@Test
	void callerCannotPassBothRawInputAndInputReference() {
		var identity = new SkillContract.Identity("tenant", "actor", "subject", "correlation");
		var consumer = new SkillContract.Consumer("WORK_BRIEF", "policy", "SUMMARY", "default");
		assertThrows(IllegalArgumentException.class,
				() -> new SkillContract.ExecuteRequest(identity, consumer, "token", "{}", "reference", "key", null));
		assertThrows(IllegalArgumentException.class,
				() -> new SkillContract.ExecuteRequest(identity, consumer, "token", null, null, "key", null));
	}

	@Test
	void unknownCompatibilityCannotClaimAnExecutableRuntime() {
		assertThrows(IllegalArgumentException.class,
				() -> new SkillContract.AgentCapabilities(SkillContract.CompatibilityStatus.UNKNOWN,
						List.of(SkillContract.RuntimeKind.SCRIPT), false, false, false, false, false, null, null,
						SkillContract.ErrorCode.AGENT_ADAPTER_UNAVAILABLE));
	}

}
