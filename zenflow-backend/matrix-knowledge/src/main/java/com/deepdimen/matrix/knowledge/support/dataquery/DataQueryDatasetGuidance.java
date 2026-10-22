package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.Identity;

/** Generic authorized metadata explanation; independent of opt-in SQL control tables. */
@Component
public final class DataQueryDatasetGuidance {

	@FunctionalInterface
	public interface Generator {

		String generate(String question, DataQueryMetadataRepository.DatasetInventory inventory, Runnable recheck);

	}

	private static final String VERSION = "dataset-metadata-v1";

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	public DataQueryDatasetGuidance(DataSource source, ObjectMapper json) {
		this.jdbc = new JdbcTemplate(source);
		this.jdbc.setQueryTimeout(5);
		this.json = json;
	}

	public Optional<AiMessageResultDTO> respond(ChatMessageDTO message, Generator generator) {
		var identity = DataQuerySecurity.identity();
		String username = DataQuerySecurity.username();
		if (message.getMessageKey() == null || message.getExtDetails() == null
				|| message.getExtDetails().getDataId() == null)
			throw new DataQueryException("INVALID_REQUEST");
		var rows = jdbc.queryForList(
				"SELECT username,question_text,ext_details FROM ai_chat_record WHERE tenant_id=? AND record_id=? AND username=? AND dataset_id=-2 AND del_flag='0'",
				Long.parseLong(identity.tenantId()), message.getMessageKey(), username);
		if (rows.size() != 1 || !username.equals(rows.get(0).get("username")))
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		String question = (String) rows.get(0).get("question_text");
		if (!DataQueryDatasetOverview.matches(question))
			return Optional.empty();
		String originalExt = (String) rows.get(0).get("ext_details");
		var ext = parseExt(originalExt);
		long dataId = message.getExtDetails().getDataId();
		if (!Long.toString(dataId).equals(ext.path("dataId").asText()))
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		var inventory = inventory(identity, dataId);
		Runnable recheck = () -> {
			if (!identity.equals(DataQuerySecurity.identity()) || !username.equals(DataQuerySecurity.username())
					|| !inventory.equals(inventory(identity, dataId)))
				throw new DataQueryException("DATASET_METADATA_CHANGED");
			Integer owned = jdbc.queryForObject(
					"SELECT COUNT(*) FROM ai_chat_record WHERE tenant_id=? AND record_id=? AND username=? AND question_text=? AND ext_details=? AND del_flag='0'",
					Integer.class, Long.parseLong(identity.tenantId()), message.getMessageKey(), username, question,
					originalExt);
			if (!Integer.valueOf(1).equals(owned))
				throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		};
		String answer = generator.generate(question, inventory, recheck);
		if (!DataQueryMetadataAnalysis.validAnswerLength(question, inventory, answer))
			throw new DataQueryException("AI_RESPONSE_INVALID");
		var receipt = analysisReceipt(message.getMessageKey(), dataId, inventory.digest(), question, answer);
		// Persist only presentation provenance; no query, binding, rule or ACL is
		// created.
		ext.set("metadataGuidance", json.valueToTree(receipt));
		try {
			recheck.run();
			int updated = jdbc.update(
					"UPDATE ai_chat_record SET answer_text=?,ext_details=? WHERE tenant_id=? AND record_id=? AND username=? AND question_text=? AND ext_details=? AND del_flag='0'",
					answer, json.writeValueAsString(ext), Long.parseLong(identity.tenantId()), message.getMessageKey(),
					username, question, originalExt);
			if (updated != 1)
				throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
		var result = new AiMessageResultDTO(answer);
		result.setMetadataGuidance(receipt);
		result.setFunctionName("datasetMetadataAnalysis");
		return Optional.of(result);
	}

	private DataQueryMetadataRepository.DatasetInventory inventory(Identity identity, long dataId) {
		if (!DataQueryMetadataRepository.canReadDatasetMetadata(jdbc, identity))
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		return DataQueryMetadataRepository.readInventory(jdbc, json, identity, dataId);
	}

	/**
	 * Recheck ownership, current read permission and exact generated text, not a client
	 * flag.
	 */
	public Map<String, Object> restore(AiChatRecordEntity record) {
		try {
			var identity = DataQuerySecurity.identity();
			if (!identity.tenantId().equals(record.getTenantId())
					|| !DataQuerySecurity.username().equals(record.getUsername())
					|| !Long.valueOf(-2).equals(record.getDatasetId())
					|| !DataQueryDatasetOverview.matches(record.getQuestionText()))
				return null;
			var ext = parseExt(record.getExtDetails());
			var marker = ext.path("metadataGuidance");
			long dataId = Long.parseLong(ext.path("dataId").asText());
			var inventory = inventory(identity, dataId);
			if ("dataset-metadata-v2".equals(marker.path("version").asText())) {
				if (record.getAnswerText() == null || record.getAnswerText().isBlank())
					return null;
				var expected = analysisReceipt(record.getRecordId(), dataId, inventory.digest(),
						record.getQuestionText(), record.getAnswerText());
				return json.valueToTree(expected).equals(marker) ? expected : null;
			}
			var expected = receipt(record.getRecordId(), dataId);
			if (!json.valueToTree(expected).equals(marker))
				return null;
			var generated = DataQueryDatasetOverview.answer(record.getQuestionText(), inventory);
			return generated.isPresent() && generated.get().equals(record.getAnswerText()) ? expected : null;
		}
		catch (RuntimeException unavailable) {
			return null;
		}
	}

	private Map<String, Object> analysisReceipt(long messageId, long dataId, String metadataDigest, String question,
			String answer) {
		return Map.of("version", "dataset-metadata-v2", "messageId", Long.toString(messageId), "dataId",
				Long.toString(dataId), "responseType", "DATASET_GUIDANCE", "source", "AI_METADATA_ANALYSIS",
				"sqlExecuted", false, "metadataDigest", metadataDigest, "contentDigest",
				cn.hutool.crypto.digest.DigestUtil.sha256Hex(question + "\0" + answer));
	}

	private Map<String, Object> receipt(long messageId, long dataId) {
		return Map.of("version", VERSION, "messageId", Long.toString(messageId), "dataId", Long.toString(dataId),
				"responseType", "DATASET_GUIDANCE", "source", "SELECTED_TABLE_METADATA", "sqlExecuted", false);
	}

	private com.fasterxml.jackson.databind.node.ObjectNode parseExt(String value) {
		try {
			if (!(json.readTree(value) instanceof com.fasterxml.jackson.databind.node.ObjectNode object))
				throw new IllegalArgumentException();
			return object;
		}
		catch (Exception invalid) {
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		}
	}

}
