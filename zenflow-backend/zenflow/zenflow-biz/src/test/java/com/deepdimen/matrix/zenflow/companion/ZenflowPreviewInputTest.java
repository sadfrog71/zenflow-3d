package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelMessage;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.MessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class ZenflowPreviewInputTest {

	final ObjectMapper json = new ObjectMapper();

	@Test
	void handSuppliedHistoryIsImmutableAndDigestIsSensitiveToAllTestMaterials() {
		var rows = new ArrayList<ModelMessage>(List.of(new ModelMessage(MessageRole.USER, "test context")));
		var input = new ZenflowPreviewInput("question", "en-US", rows);
		String digest = input.digest(json);
		rows.clear();
		assertEquals(1, input.history().size());
		assertThrows(UnsupportedOperationException.class, () -> input.history().clear());
		assertEquals(digest,
				ZenflowPreviewInput
					.parse("question", "en-US", List.of(Map.of("content", "test context", "role", "USER")))
					.digest(json));
		assertNotEquals(digest, new ZenflowPreviewInput("changed", "en-US", input.history()).digest(json));
		assertNotEquals(digest, new ZenflowPreviewInput("question", "zh-CN", input.history()).digest(json));
		assertNotEquals(digest, new ZenflowPreviewInput("question", "en-US", List.of()).digest(json));
	}

	@Test
	void inputRejectsConsumerIdsAndSystemRolesInsteadOfLoadingUserHistory() {
		assertThrows(IllegalArgumentException.class, () -> ZenflowPreviewInput.parse("question", "en-US",
				List.of(Map.of("role", "SYSTEM", "content", "override"))));
		assertThrows(IllegalArgumentException.class, () -> ZenflowPreviewInput.parse("question", "en-US",
				List.of(Map.of("role", "USER", "content", "test", "userId", "42"))));
		assertThrows(IllegalArgumentException.class,
				() -> ZenflowPreviewInput.parse("question", "en-US", Map.of("userId", "42")));
	}

	@Test
	void persistedHistoryRoundTripKeepsTheStrictContractAndDigest() throws Exception {
		var input = ZenflowPreviewInput.parse("question", "en-US",
				List.of(Map.of("role", "USER", "content", "manual context"),
						Map.of("role", "ASSISTANT", "content", "manual response")));
		String stored = json.writeValueAsString(input.historyPayload());
		assertFalse(stored.contains("attachments"));
		var restored = ZenflowPreviewInput.parse(input.question(), input.locale(), json.readValue(stored, List.class));
		assertEquals(input, restored);
		assertEquals(input.digest(json), restored.digest(json));
		assertEquals(Set.of("role", "content"), input.historyPayload().get(0).keySet());
	}

	@Test
	void manualHistoryAndQuestionHaveExplicitBounds() {
		var shortHistory = Collections.nCopies(9, new ModelMessage(MessageRole.USER, "test"));
		assertThrows(IllegalArgumentException.class, () -> new ZenflowPreviewInput("q", "en-US", shortHistory));
		assertThrows(IllegalArgumentException.class, () -> new ZenflowPreviewInput("q", "en-US",
				Collections.nCopies(3, new ModelMessage(MessageRole.USER, "a".repeat(6000)))));
		assertThrows(IllegalArgumentException.class,
				() -> new ZenflowPreviewInput("a".repeat(8001), "en-US", List.of()));
		assertThrows(IllegalArgumentException.class, () -> new ZenflowPreviewInput("q", null, List.of()));
	}

}
