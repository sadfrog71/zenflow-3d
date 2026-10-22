package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import org.junit.jupiter.api.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real owned platform history; the model and source rows are explicitly synthetic. */
class DataQueryConversationTest {

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
	void followUpReceivesOwnedSavedContextButExecutesAgainWithCurrentData() throws Exception {
		f.modelSql("SELECT COUNT(*) AS total FROM dev_device", 0);
		var first = f.rule.process(f.message(70, "CONTEXT_BASE_2026年9月各部门设备数")).blockFirst();
		assertThat(first.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		f.jdbc.update("INSERT INTO dev_device VALUES('C')");
		when(f.model.chat(any(ChatRequest.class))).thenAnswer(call -> {
			var prompt = ((ChatRequest) call.getArgument(0)).messages().toString();
			assertThat(prompt).contains("CONTEXT_BASE_2026年9月各部门设备数", "换上个月", "TOTAL", "3");
			assertThat(prompt).doesNotContain("shared-conversation");
			return ChatResponse.builder()
				.aiMessage(AiMessage.from(
						"{\"sql\":\"SELECT COUNT(*) AS total FROM dev_device\",\"chartOutput\":false,\"chartType\":0}"))
				.build();
		});
		var second = f.rule.process(f.message(71, "换上个月")).blockFirst();
		assertThat(second.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		assertThat(second.getDataQuery().get("queryId")).isNotEqualTo(first.getDataQuery().get("queryId"));
		assertThat(second.getDataQuery().get("rows")).isEqualTo(List.of(List.of("4")));
		assertThat(f.executions).hasValue(2);
	}

	@Test
	void savedContextExcludesOtherSessionUserTenantAndChangedScope() throws Exception {
		f.modelSql("SELECT COUNT(*) AS total FROM dev_device", 0);
		var prior = f.rule.process(f.message(70, "CONTEXT_PRIVATE_BASE")).blockFirst();
		String query = prior.getDataQuery().get("queryId").toString();
		var next = f.service.begin(f.owner, "reader", f.message(71, "看刚才第一名的明细"));
		assertThat(f.service.prompt(next)).contains("CONTEXT_PRIVATE_BASE");
		for (String change : List.of("UPDATE ai_chat_record SET conversation_id='another-session' WHERE record_id=70",
				"UPDATE ai_chat_record SET username='other-user' WHERE record_id=70",
				"UPDATE ai_chat_record SET username='Reader' WHERE record_id=70",
				"UPDATE ai_chat_record SET conversation_id='SHARED-conversation' WHERE record_id=70",
				"UPDATE ai_chat_record SET tenant_id=2 WHERE record_id=70",
				"UPDATE ai_dataquery_query SET user_id=3 WHERE query_id='" + query + "'",
				"UPDATE ai_dataquery_query SET tenant_id=2 WHERE query_id='" + query + "'",
				"UPDATE ai_dataquery_query SET scope_revision='obsolete' WHERE query_id='" + query + "'")) {
			f.jdbc.update(change);
			assertThat(f.service.prompt(next)).as(change).doesNotContain("CONTEXT_PRIVATE_BASE");
			f.jdbc.update(
					"UPDATE ai_chat_record SET conversation_id='shared-conversation',username='reader',tenant_id=1 WHERE record_id=70");
			f.jdbc.update("UPDATE ai_dataquery_query SET tenant_id=1,user_id=2,scope_revision=? WHERE query_id=?",
					next.authorization().revision(), query);
		}
	}

	@Test
	void excludesOtherDatasetAndOldInventoryAndDoesNotTrustRequestConversationId() throws Exception {
		f.modelSql("SELECT COUNT(*) FROM dev_device", 0);
		var prior = f.rule.process(f.message(70, "CONTEXT_PRIVATE_BASE")).blockFirst();
		String query = prior.getDataQuery().get("queryId").toString();
		var message = f.message(71, "换上个月");
		message.setConversationId("forged-other-session");
		var next = f.service.begin(f.owner, "reader", message);
		assertThat(f.service.prompt(next)).contains("CONTEXT_PRIVATE_BASE");
		String candidate = f.jdbc.queryForObject("SELECT candidate_json FROM ai_dataquery_query WHERE query_id=?",
				String.class, query);
		for (var mutation : List.of(Map.of("dataId", "999"), Map.of("inventoryDigest", "obsolete"),
				Map.of("kind", "NOT_AI_SQL"))) {
			var changed = (com.fasterxml.jackson.databind.node.ObjectNode) f.json.readTree(candidate);
			mutation.forEach(changed::put);
			f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=?",
					f.json.writeValueAsString(changed), query);
			assertThat(f.service.prompt(next)).doesNotContain("CONTEXT_PRIVATE_BASE");
		}
	}

	@Test
	void reauthorizesBeforeSendingAnyHistoryToTheModel() throws Exception {
		f.modelSql("SELECT COUNT(*) FROM dev_device", 0);
		f.rule.process(f.message(70, "CONTEXT_PRIVATE_BASE")).blockFirst();
		var next = f.service.begin(f.owner, "reader", f.message(71, "换上个月"));
		f.jdbc.update("DELETE FROM ai_dataquery_access");
		assertThatThrownBy(() -> f.service.prompt(next)).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void contextIsBoundedAndMissingOrAmbiguousProvenanceIsNotReused() throws Exception {
		f.modelSql("SELECT COUNT(*) FROM dev_device", 0);
		for (int i = 70; i < 78; i++)
			f.rule.process(f.message(i, "HISTORY_SENTINEL_" + i)).blockFirst();
		f.jdbc.update("UPDATE ai_chat_record SET answer_text=? WHERE record_id=77",
				"bounded-prefix\n" + "x".repeat(10000));
		var current = f.service.begin(f.owner, "reader", f.message(78, "下一轮"));
		var history = f.json.readTree(f.metadata.conversationContext(current));
		assertThat(history.size()).isEqualTo(6);
		assertThat(history.toString()).doesNotContain("HISTORY_SENTINEL_70", "HISTORY_SENTINEL_71", "x".repeat(6001));
		assertThat(history.get(5).path("answerIsExcerpt").asBoolean()).isTrue();
		f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json='{}' WHERE chat_record_id=77");
		assertThat(f.metadata.conversationContext(current)).doesNotContain("HISTORY_SENTINEL_77");
		var duplicate = f.service.begin(f.owner, "reader", messageWithoutInsert(76));
		f.metadata.finished(duplicate.queryId(), f.owner, "OK", 1);
		assertThat(f.metadata.conversationContext(current)).doesNotContain("HISTORY_SENTINEL_76");
	}

	private com.deepdimen.matrix.knowledge.dto.ChatMessageDTO messageWithoutInsert(long id) {
		var message = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO();
		message.setMessageKey(id);
		message.getExtDetails().setDataId(10L);
		return message;
	}

}
