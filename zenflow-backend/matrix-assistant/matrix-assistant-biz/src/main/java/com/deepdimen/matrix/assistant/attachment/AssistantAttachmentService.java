package com.deepdimen.matrix.assistant.attachment;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantUploadResponse;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.attachment.AssistantAttachmentRepository.UploadRecord;

@Service
public class AssistantAttachmentService {

	public static final int MAX_ATTACHMENTS = 5;

	public static final long MAX_TOTAL_SIZE = 40L * 1024 * 1024;

	public static final long MAX_IMAGE_TOTAL_SIZE = 10L * 1024 * 1024;

	private final AssistantAttachmentRepository repository;

	private final Duration uploadTtl;

	@Autowired
	public AssistantAttachmentService(AssistantAttachmentRepository repository) {
		this(repository, Duration.ofHours(2));
	}

	AssistantAttachmentService(AssistantAttachmentRepository repository, Duration uploadTtl) {
		this.repository = repository;
		this.uploadTtl = uploadTtl;
	}

	public AssistantUploadResponse register(IdentityEnvelope identity, String storageFileName, String originalName,
			String contentType, long size, AttachmentKind kind) {
		requireIdentity(identity);
		if (size < 0 || size > 20L * 1024 * 1024 || kind == AttachmentKind.IMAGE && size > MAX_IMAGE_TOTAL_SIZE) {
			throw invalidUpload();
		}
		Instant now = Instant.now();
		UploadRecord saved = repository.save(new UploadRecord(UUID.randomUUID().toString(), identity.tenantId(),
				identity.userId(), storageFileName, originalName, contentType, size, kind, now, now.plus(uploadTtl)));
		return response(saved);
	}

	public List<AssistantAttachment> resolve(IdentityEnvelope identity, List<String> uploadIds) {
		requireIdentity(identity);
		List<String> normalized = uploadIds == null ? List.of() : uploadIds.stream().map(id -> {
			if (id == null || id.isBlank()) {
				throw invalidUpload();
			}
			return id.trim();
		}).toList();
		if (normalized.size() > MAX_ATTACHMENTS || new LinkedHashSet<>(normalized).size() != normalized.size()) {
			throw invalidUpload();
		}
		Instant now = Instant.now();
		List<AssistantAttachment> attachments = normalized.stream()
			.map(uploadId -> repository.findOwned(uploadId, identity.tenantId(), identity.userId())
				.filter(upload -> upload.consumedAt() != null
						|| upload.expiresAt() != null && upload.expiresAt().isAfter(now))
				.orElseThrow(this::invalidUpload))
			.map(upload -> new AssistantAttachment(upload.uploadId(), upload.storageFileName(), upload.originalName(),
					upload.contentType(), upload.size(), upload.kind()))
			.toList();
		if (attachments.stream().mapToLong(AssistantAttachment::size).sum() > MAX_TOTAL_SIZE) {
			throw invalidUpload();
		}
		if (attachments.stream()
			.filter(attachment -> attachment.kind() == AttachmentKind.IMAGE)
			.mapToLong(AssistantAttachment::size)
			.sum() > MAX_IMAGE_TOTAL_SIZE) {
			throw invalidUpload();
		}
		return attachments;
	}

	public void markConsumed(IdentityEnvelope identity, List<AssistantAttachment> attachments) {
		requireIdentity(identity);
		Instant now = Instant.now();
		for (AssistantAttachment attachment : attachments == null ? List.<AssistantAttachment>of() : attachments) {
			if (!repository.markConsumed(attachment.uploadId(), identity.tenantId(), identity.userId(), now)) {
				throw invalidUpload();
			}
		}
	}

	private AssistantUploadResponse response(UploadRecord upload) {
		return new AssistantUploadResponse(upload.uploadId(), upload.storageFileName(), upload.originalName(),
				upload.contentType(), upload.size(), upload.kind(), upload.expiresAt());
	}

	private ResponseStatusException invalidUpload() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_UPLOAD_INVALID");
	}

	private void requireIdentity(IdentityEnvelope identity) {
		if (identity == null || identity.tenantId() == null || identity.tenantId().isBlank()
				|| identity.userId() == null || identity.userId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未获取到有效登录身份");
		}
	}

}
