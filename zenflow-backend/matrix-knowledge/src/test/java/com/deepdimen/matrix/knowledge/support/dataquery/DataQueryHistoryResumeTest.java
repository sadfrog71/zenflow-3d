package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Owned history is an observer, not a query retry endpoint. All source rows are
 * synthetic.
 */
class DataQueryHistoryResumeTest {

	final DataQueryAiChatTest f = new DataQueryAiChatTest();

	@BeforeEach
	void setup() throws Exception {
		f.setup();
	}

	@AfterEach
	void cleanup() {
		f.cleanup();
	}

	@Test
	void closingBrowserStreamDoesNotInterruptAcceptedQueryOrExecuteAgain() throws Exception {
		var entered = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
		var reply = dev.langchain4j.model.chat.response.ChatResponse.builder()
			.aiMessage(dev.langchain4j.data.message.AiMessage.from("{\"sql\":\"SELECT COUNT(*) AS n FROM dev_device\",\"chartOutput\":false}"))
			.build();
		when(f.model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenAnswer(call -> {
			entered.countDown();
			try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test wait exceeded"); }
			catch (InterruptedException e) { interrupted.set(true); throw e; }
			return reply;
		});
		var subscription = f.rule.processStreaming(f.message(71, "设备数")).subscribe();
		try {
			assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			var before = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(71L)).get(71L);
			assertThat(before.dataQuery()).containsEntry("status", "RUNNING");
			subscription.dispose();
			Thread.sleep(100);
			release.countDown();
			Map<String, Object> result = null;
			for (int n=0; n<100; n++) {
				result = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(71L)).get(71L).dataQuery();
				if (!"RUNNING".equals(result.get("status"))) break;
				Thread.sleep(50);
			}
			assertThat(interrupted).isFalse();
			assertThat(result).containsEntry("status", "SUCCEEDED").containsEntry("queryId", before.dataQuery().get("queryId"));
			assertThat(f.executions).hasValue(1);
			assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query", Integer.class)).isEqualTo(1);
			assertThat(f.jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=71", String.class)).isNotBlank();
			verify(f.model, times(1)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
		} finally { release.countDown(); subscription.dispose(); }
	}

	@Test
	void pendingThenCompletedHistoryRestoresSameQueryWithoutExecutingAgain() {
		var context = f.service.begin(f.owner, "reader", f.message(70, "查询设备数量"));
		for (int i = 0; i < 3; i++) {
			var pending = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(70L)).get(70L);
			assertThat(pending.state()).isEqualTo("AVAILABLE");
			assertThat(pending.dataQuery()).containsEntry("status", "RUNNING")
				.containsEntry("queryId", context.queryId())
				.doesNotContainKey("explanation");
		}
		assertThat(f.executions).hasValue(0);
		var result = f.service.execute(context, "SELECT COUNT(*) AS total FROM dev_device");
		assertThat(result.getDataQuery()).containsEntry("status", "SUCCEEDED");
		for (int i = 0; i < 3; i++) {
			var done = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(70L)).get(70L);
			assertThat(done.dataQuery()).containsEntry("status", "SUCCEEDED")
				.containsEntry("queryId", context.queryId())
				.containsKey("explanation");
			assertThat(f.jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=70", String.class))
				.isNotBlank();
		}
		assertThat(f.executions).hasValue(1);
		assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query", Integer.class)).isEqualTo(1);
		verify(f.model, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void pendingHistoryRemainsOwnedAndAmbiguityIsNeverAutoResumed() {
		f.service.begin(f.owner, "reader", f.message(70, "查询设备数量"));
		assertThat(f.metadata.findOwnedChatReferences(new Identity("2", "2"), "reader", List.of(70L))).isEmpty();
		assertThat(f.metadata.findOwnedChatReferences(new Identity("1", "3"), "reader", List.of(70L))).isEmpty();
		assertThat(f.metadata.findOwnedChatReferences(f.owner, "other", List.of(70L))).isEmpty();
		var duplicate = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO();
		duplicate.setMessageKey(70L);
		duplicate.getExtDetails().setDataId(10L);
		f.service.begin(f.owner, "reader", duplicate);
		assertThat(f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(70L)).get(70L).state())
			.isEqualTo("AMBIGUOUS");
		assertThat(f.executions).hasValue(0);
	}

	@Test
	void terminalFailureIsNotShownAsStillRunning() {
		var context = f.service.begin(f.owner, "reader", f.message(70, "查询设备数量"));
		f.service.fail(context, "QUERY_TIMEOUT");
		var failure = f.metadata.findOwnedChatReferences(f.owner, "reader", List.of(70L)).get(70L);
		assertThat(failure.dataQuery()).containsEntry("status", "ERROR").containsEntry("code", "QUERY_TIMEOUT");
		assertThat(f.executions).hasValue(0);
	}

}
