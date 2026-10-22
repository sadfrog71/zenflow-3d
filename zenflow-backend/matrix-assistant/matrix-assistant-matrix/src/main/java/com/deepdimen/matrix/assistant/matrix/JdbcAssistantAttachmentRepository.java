package com.deepdimen.matrix.assistant.matrix;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.attachment.AssistantAttachmentRepository;

@Primary
@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantAttachmentRepository implements AssistantAttachmentRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcAssistantAttachmentRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public UploadRecord save(UploadRecord upload) {
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_upload
				(id, tenant_id, owner_user_id, storage_file_name, original_name, content_type, file_size, kind,
				 created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", upload.uploadId(), upload.tenantId(), upload.ownerUserId(), upload.storageFileName(),
				upload.originalName(), upload.contentType(), upload.size(), upload.kind().name(),
				Timestamp.from(upload.createdAt()), Timestamp.from(upload.expiresAt()));
		return upload;
	}

	@Override
	public Optional<UploadRecord> findOwned(String uploadId, String tenantId, String ownerUserId) {
		return jdbcTemplate
			.query("""
					SELECT * FROM ai_assistant_upload
					WHERE id = ? AND tenant_id = ? AND owner_user_id = ?
					""",
					(resultSet, rowNum) -> new UploadRecord(resultSet.getString("id"), resultSet.getString("tenant_id"),
							resultSet.getString("owner_user_id"), resultSet.getString("storage_file_name"),
							resultSet.getString("original_name"), resultSet.getString("content_type"),
							resultSet.getLong("file_size"), AttachmentKind.valueOf(resultSet.getString("kind")),
							resultSet.getTimestamp("created_at").toInstant(),
							resultSet.getTimestamp("expires_at").toInstant(),
							resultSet.getTimestamp("consumed_at") == null ? null
									: resultSet.getTimestamp("consumed_at").toInstant()),
					uploadId, tenantId, ownerUserId)
			.stream()
			.findFirst();
	}

	@Override
	public boolean markConsumed(String uploadId, String tenantId, String ownerUserId, java.time.Instant consumedAt) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_upload
				SET consumed_at = COALESCE(consumed_at, ?)
				WHERE id = ? AND tenant_id = ? AND owner_user_id = ?
				""", Timestamp.from(consumedAt), uploadId, tenantId, ownerUserId) == 1;
	}

}
