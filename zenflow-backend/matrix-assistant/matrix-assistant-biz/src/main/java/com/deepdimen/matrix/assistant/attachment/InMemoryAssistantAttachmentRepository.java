package com.deepdimen.matrix.assistant.attachment;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(AssistantAttachmentRepository.class)
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAssistantAttachmentRepository implements AssistantAttachmentRepository {

	private final Map<String, UploadRecord> uploads = new ConcurrentHashMap<>();

	@Override
	public UploadRecord save(UploadRecord upload) {
		uploads.entrySet().removeIf(entry -> expired(entry.getValue(), Instant.now()));
		uploads.put(upload.uploadId(), upload);
		return upload;
	}

	@Override
	public Optional<UploadRecord> findOwned(String uploadId, String tenantId, String ownerUserId) {
		UploadRecord upload = uploads.get(uploadId);
		if (expired(upload, Instant.now())) {
			uploads.remove(uploadId, upload);
			return Optional.empty();
		}
		return Optional.ofNullable(upload)
			.filter(record -> tenantId.equals(record.tenantId()) && ownerUserId.equals(record.ownerUserId()));
	}

	@Override
	public boolean markConsumed(String uploadId, String tenantId, String ownerUserId, Instant consumedAt) {
		UploadRecord upload = uploads.get(uploadId);
		if (upload == null || !tenantId.equals(upload.tenantId()) || !ownerUserId.equals(upload.ownerUserId())) {
			return false;
		}
		uploads.computeIfPresent(uploadId,
				(ignored, current) -> new UploadRecord(current.uploadId(), current.tenantId(), current.ownerUserId(),
						current.storageFileName(), current.originalName(), current.contentType(), current.size(),
						current.kind(), current.createdAt(), current.expiresAt(), consumedAt));
		return true;
	}

	private boolean expired(UploadRecord upload, Instant now) {
		return upload != null && upload.consumedAt() == null
				&& (upload.expiresAt() == null || !upload.expiresAt().isAfter(now));
	}

}
