package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Saves the rendered legacy answer, never the model's intermediate SQL proposal. */
@Component
public final class DataQueryLegacyAnswerStore {

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Map<String, Run> running = new ConcurrentHashMap<>();

	public record Run(String key, ControlledQueryService.Identity owner, String username, long messageId,
			String question, String conversation, String dataId) {
	}

	public DataQueryLegacyAnswerStore(DataSource source, ObjectMapper json) {
		this.jdbc = new JdbcTemplate(source);
		this.jdbc.setQueryTimeout(5);
		this.json = json;
	}

	public Run begin(ChatMessageDTO message) {
		var owner = DataQuerySecurity.identity();
		String username = DataQuerySecurity.username();
		if (message.getMessageKey() == null || message.getExtDetails() == null
				|| message.getExtDetails().getDataId() == null)
			throw new DataQueryException("INVALID_REQUEST");
		var row = owned(owner, username, message.getMessageKey());
		String dataId = message.getExtDetails().getDataId().toString();
		if (!Objects.equals(row.get("question_text"), message.getContent())
				|| !Objects.equals(row.get("conversation_id"), message.getConversationId())
				|| !dataId.equals(dataId((String) row.get("ext_details"))))
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		var run = new Run(key(owner, message.getMessageKey()), owner, username, message.getMessageKey(),
				message.getContent(), message.getConversationId(), dataId);
		if (running.putIfAbsent(run.key(), run) != null)
			throw new DataQueryException("QUERY_ALREADY_RUNNING");
		return run;
	}

	public void complete(Run run, AiMessageResultDTO result) {
		if (result.getMessage() == null || result.getMessage().isBlank())
			throw new DataQueryException("AI_RESPONSE_INVALID");
		if (!run.owner().equals(DataQuerySecurity.identity()) || !run.username().equals(DataQuerySecurity.username()))
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		var row = owned(run.owner(), run.username(), run.messageId());
		String ext = (String) row.get("ext_details");
		if (!Objects.equals(run.question(), row.get("question_text"))
				|| !Objects.equals(run.conversation(), row.get("conversation_id")) || !run.dataId().equals(dataId(ext)))
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		// The metadata-only path may already have saved its answer and provenance.
		// Leave that receipt and any chart metadata intact. No client-supplied answer.
		if (result.getMessage().equals(row.get("answer_text")))
			return;
		int changed = jdbc.update(
				"UPDATE ai_chat_record SET answer_text=? WHERE tenant_id=? AND record_id=? AND username=? AND dataset_id=-2 AND question_text=? AND conversation_id=? AND ext_details=? AND del_flag='0'",
				result.getMessage(), Long.parseLong(run.owner().tenantId()), run.messageId(), run.username(),
				run.question(), run.conversation(), ext);
		if (changed != 1)
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
	}

	public void end(Run run) {
		if (run != null)
			running.remove(run.key(), run);
	}

	/** Live observer only; after a process restart a missing answer stays incomplete. */
	public String state(AiChatRecordEntity record) {
		var owner = DataQuerySecurity.identity();
		var run = running.get(key(owner, record.getRecordId()));
		return run != null && run.username().equals(DataQuerySecurity.username())
				&& run.username().equals(record.getUsername()) && owner.tenantId().equals(record.getTenantId())
				&& Objects.equals(run.question(), record.getQuestionText())
				&& Objects.equals(run.conversation(), record.getConversationId())
				&& run.dataId().equals(dataId(record.getExtDetails())) ? "RUNNING" : null;
	}

	private Map<String, Object> owned(ControlledQueryService.Identity owner, String username, long id) {
		var rows = jdbc.queryForList(
				"SELECT question_text,conversation_id,ext_details,answer_text FROM ai_chat_record WHERE tenant_id=? AND record_id=? AND username=? AND dataset_id=-2 AND del_flag='0'",
				Long.parseLong(owner.tenantId()), id, username);
		if (rows.size() != 1)
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		return rows.get(0);
	}

	private String dataId(String ext) {
		try {
			return json.readTree(ext).path("dataId").asText();
		}
		catch (Exception invalid) {
			return "";
		}
	}

	private static String key(ControlledQueryService.Identity owner, long id) {
		return owner.tenantId() + ":" + owner.userId() + ":" + id;
	}

}
