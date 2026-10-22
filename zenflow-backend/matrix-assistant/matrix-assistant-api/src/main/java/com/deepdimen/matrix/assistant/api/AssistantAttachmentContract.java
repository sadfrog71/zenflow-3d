package com.deepdimen.matrix.assistant.api;

import java.time.Instant;
import java.util.Objects;

/** Contracts for user-owned files attached to unified assistant messages. */
public final class AssistantAttachmentContract {

	private AssistantAttachmentContract() {
	}

	public enum AttachmentKind {

		IMAGE, DOCUMENT

	}

	public record AssistantAttachment(String uploadId, String storageFileName, String originalName, String contentType,
			long size, AttachmentKind kind) {

		public AssistantAttachment {
			uploadId = requireText(uploadId, "uploadId");
			storageFileName = requireText(storageFileName, "storageFileName");
			originalName = requireText(originalName, "originalName");
			contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
			if (size < 0) {
				throw new IllegalArgumentException("size must not be negative");
			}
			kind = Objects.requireNonNull(kind, "kind");
		}
	}

	public record AssistantUploadResponse(String uploadId, String storageFileName, String originalName,
			String contentType, long size, AttachmentKind kind, Instant expiresAt) {

		public AssistantUploadResponse {
			expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
		}

		public AssistantAttachment toAttachment() {
			return new AssistantAttachment(uploadId, storageFileName, originalName, contentType, size, kind);
		}
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value;
	}

}
