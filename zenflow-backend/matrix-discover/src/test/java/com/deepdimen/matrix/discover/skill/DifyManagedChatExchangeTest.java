package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DifyManagedChatExchangeTest {

	final ObjectMapper json = new ObjectMapper();

	DifyManagedChatExchange exchange() {
		var e = new DifyManagedChatExchange(json, "reviewed", () -> {
		});
		e.start();
		return e;
	}

	@Test
	void completeStreamUsesActualIdsAndReplacementText() throws Exception {
		var e = exchange();
		e.event(null,
				"{\"event\":\"message\",\"answer\":\"draft\",\"conversation_id\":\"c\",\"message_id\":\"m\",\"task_id\":\"t\"}");
		e.event(null, "{\"event\":\"message_replace\",\"answer\":\"reviewed answer\",\"message_id\":\"m\"}");
		e.event(null, "{\"event\":\"message_end\",\"conversation_id\":\"c\",\"id\":\"m\"}");
		var output = json.readTree(e.result());
		assertEquals("reviewed answer", output.path("answer").asText());
		assertEquals("c", output.path("conversationId").asText());
		assertEquals("m", output.path("messageId").asText());
		assertEquals("t", output.path("taskId").asText());
	}

	@Test
	void httpCompletionWithoutTerminalCannotBecomeSuccess() {
		var e = exchange();
		e.event("message", "{\"answer\":\"partial\"}");
		assertThrows(IllegalArgumentException.class, e::result);
	}

	@Test
	void mismatchedIdsAndLateErrorInvalidateCollectedResult() {
		var e = exchange();
		e.event("message", "{\"answer\":\"one\",\"message_id\":\"m\"}");
		assertThrows(IllegalStateException.class,
				() -> e.event("message_end", "{\"message_id\":\"other\",\"conversation_id\":\"c\"}"));
		assertThrows(IllegalStateException.class, e::result);
		var complete = exchange();
		complete.event("message_end", "{\"message_id\":\"m\",\"conversation_id\":\"c\"}");
		complete.fail(new RuntimeException("transport lost"));
		assertThrows(IllegalStateException.class, complete::result);
	}

	@Test
	void workflowOrMalformedEventCannotMasqueradeAsClassicChat() {
		for (String data : new String[] { "{\"event\":\"workflow_finished\"}", "{\"event\":\"message_end\"}",
				"{\"event\":\"message\",\"answer\":\"a\",\"answer\":\"b\"}" }) {
			var e = exchange();
			assertThrows(IllegalStateException.class, () -> e.event(null, data));
			assertThrows(IllegalStateException.class, e::result);
		}
	}

	@Test
	void firstRemoteConversationIsRecordedBeforeAnIncompleteStreamCanExposeIt() {
		var observed = new java.util.ArrayList<String>();
		var e = new DifyManagedChatExchange(json, "reviewed", () -> {
		}, observed::add);
		e.start();
		e.event("message", "{\"answer\":\"partial\",\"conversation_id\":\"remote\"}");
		e.event("message", "{\"answer\":\"more\",\"conversation_id\":\"remote\"}");
		assertEquals(java.util.List.of("remote"), observed);
		assertThrows(IllegalArgumentException.class, e::result);
	}

	@Test
	void failedConversationRecordStopsEventBeforeCallerCanForwardIt() {
		var e = new DifyManagedChatExchange(json, "reviewed", () -> {
		}, id -> {
			throw new IllegalStateException("storage lost");
		});
		e.start();
		assertThrows(IllegalStateException.class,
				() -> e.event("message", "{\"answer\":\"partial\",\"conversation_id\":\"remote\"}"));
		assertThrows(IllegalStateException.class, e::result);
	}

}
