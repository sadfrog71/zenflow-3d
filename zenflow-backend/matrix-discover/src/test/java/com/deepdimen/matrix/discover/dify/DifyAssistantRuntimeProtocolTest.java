package com.deepdimen.matrix.discover.dify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class DifyAssistantRuntimeProtocolTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void routesChatflowToChatMessagesAndResumesConversation() {
		var request = DifyAssistantRuntimeProtocol.build("advanced-chat", Map.of("scope", "work"), "question", "user-1",
				"conversation-1");

		assertEquals("/chat-messages", request.path());
		assertTrue(request.conversational());
		assertEquals("conversation-1", request.body().get("conversation_id"));
		assertEquals("question", request.body().get("query"));
	}

	@Test
	void routesWorkflowToWorkflowRunWithoutConversationFields() {
		var request = DifyAssistantRuntimeProtocol.build("workflow", Map.of("query", "question"), "ignored", "user-1",
				"conversation-1");

		assertEquals("/workflows/run", request.path());
		assertFalse(request.conversational());
		assertFalse(request.body().containsKey("query"));
		assertFalse(request.body().containsKey("conversation_id"));
	}

	@Test
	void parsesWorkflowAndChatMetadata() throws Exception {
		var workflow = DifyAssistantRuntimeProtocol.parse("workflow", objectMapper.readTree("""
				{
				  "task_id": "task-1",
				  "workflow_run_id": "run-1",
				  "data": {"outputs": {"answer": "workflow answer"}}
				}
				"""));
		var chat = DifyAssistantRuntimeProtocol.parse("chat", objectMapper.readTree("""
				{"task_id":"task-2","conversation_id":"conversation-2","answer":"chat answer"}
				"""));

		assertEquals("workflow answer", workflow.answer());
		assertEquals("run-1", workflow.workflowRunId());
		assertEquals("chat answer", chat.answer());
		assertEquals("conversation-2", chat.conversationId());
	}

	@Test
	void parsesTokenUsageForUnifiedReporting() throws Exception {
		var chat = DifyAssistantRuntimeProtocol.parse("chat", objectMapper.readTree("""
				{
				  "id": "message-usage-1",
				  "answer": "done",
				  "metadata": {
				    "usage": {
				      "prompt_tokens": 120,
				      "completion_tokens": 30,
				      "total_tokens": 150,
				      "model": "model-a"
				    }
				  }
				}
				"""));

		assertEquals(120L, chat.usage().promptTokens());
		assertEquals(30L, chat.usage().completionTokens());
		assertEquals(150L, chat.usage().totalTokens());
		assertEquals("model-a", chat.usage().model());
		assertEquals("message-usage-1", chat.usage().requestId());
	}

	@Test
	void rejectsUnsupportedRuntimeMode() {
		assertThrows(IllegalArgumentException.class,
				() -> DifyAssistantRuntimeProtocol.build("completion", Map.of(), "question", "user", null));
	}

}
