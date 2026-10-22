package com.deepdimen.matrix.assistant.orchestrator;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import static org.junit.jupiter.api.Assertions.*;

class AssistantSemanticRouterTest {

	private final AssistantSemanticRouter router = new AssistantSemanticRouter();

	@Test
	void greetingDoesNotAbsorbBusinessQuestion() {
		assertEquals(ConversationIntent.CASUAL_CHAT,
				router.fastRoute("好的，谢谢", catalog()).orElseThrow().conversationIntent());
		assertTrue(router.fastRoute("你好，今天水质怎么样", catalog()).isEmpty());
		assertTrue(router.fastRoute("那昨天呢", catalog()).isEmpty());
	}

	@Test
	void descriptionAndExecutionAreDifferentIntents() {
		assertEquals(ExecutionMode.CAPABILITY_HELP,
				router.fastRoute("水质智能体能做什么？", catalog()).orElseThrow().executionMode());
		assertEquals(ExecutionMode.WORKFLOW_EXECUTION,
				router.fastRoute("调用水质智能体查询今天水质", catalog()).orElseThrow().executionMode());
		assertTrue(router.fastRoute("不要调用水质智能体", catalog()).isEmpty());
		assertTrue(router.fastRoute("介绍一下水质智能体并查询今天水质", catalog()).isEmpty());
	}

	@Test
	void absentAuthorizedNameCannotReturnCapabilityDetails() {
		assertTrue(router.fastRoute("秘密智能体能做什么", catalog()).isEmpty());
	}

	private FrozenCapabilityCatalog catalog() {
		return new FrozenCapabilityCatalog(List.of(), List.of(new WorkflowCapabilitySnapshot("water", 1, "v1", "水质智能体",
				"监测水质", List.of(), List.of(), "in", "out", "app", "digest")), "catalog", Instant.EPOCH);
	}

}
