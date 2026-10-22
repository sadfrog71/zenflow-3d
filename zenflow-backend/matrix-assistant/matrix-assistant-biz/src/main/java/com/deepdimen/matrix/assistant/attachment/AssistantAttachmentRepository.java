package com.deepdimen.matrix.assistant.attachment;

import java.time.Instant;
import java.util.Optional;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;

public interface AssistantAttachmentRepository {

	UploadRecord save(UploadRecord upload);

	Optional<UploadRecord> findOwned(String uploadId, String tenantId, String ownerUserId);

	boolean markConsumed(String uploadId, String tenantId, String ownerUserId, Instant consumedAt);

	public record UploadRecord(String uploadId, String tenantId, String ownerUserId, String storageFileName,
			String originalName, String contentType, long size, AttachmentKind kind, Instant createdAt,
			Instant expiresAt, Instant consumedAt) {

		public UploadRecord(String uploadId, String tenantId, String ownerUserId, String storageFileName,
				String originalName, String contentType, long size, AttachmentKind kind, Instant createdAt,
				Instant expiresAt) {
			this(uploadId, tenantId, ownerUserId, storageFileName, originalName, contentType, size, kind, createdAt,
					expiresAt, null);
		}
	}

}
