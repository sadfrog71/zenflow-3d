package com.deepdimen.matrix.assistant.matrix;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantUploadResponse;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.attachment.AssistantAttachmentService;
import com.deepdimen.matrix.common.core.util.R;

@Service
public class AssistantAttachmentUploadService {

	public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

	private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

	private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf", "csv", "json", "yaml",
			"yml", "doc", "docx", "xls", "xlsx", "ppt", "pptx");

	private final RemoteFileService remoteFileService;

	private final AssistantAttachmentService attachmentService;

	public AssistantAttachmentUploadService(RemoteFileService remoteFileService,
			AssistantAttachmentService attachmentService) {
		this.remoteFileService = remoteFileService;
		this.attachmentService = attachmentService;
	}

	public AssistantUploadResponse upload(IdentityEnvelope identity, MultipartFile file) {
		ValidatedFile validated = validate(file);
		R<?> result = remoteFileService.upload(file);
		if (result == null || !result.isOk() || !(result.getData() instanceof Map<?, ?> data)) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ATTACHMENT_STORAGE_FAILED");
		}
		String storageFileName = value(data.get("fileName"));
		if (storageFileName == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ATTACHMENT_STORAGE_FAILED");
		}
		return attachmentService.register(identity, storageFileName, validated.originalName(), validated.contentType(),
				file.getSize(), validated.kind());
	}

	private ValidatedFile validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_FILE_EMPTY");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_FILE_TOO_LARGE");
		}
		String rawName = file.getOriginalFilename();
		String originalName;
		try {
			originalName = rawName == null ? "attachment" : Paths.get(rawName).getFileName().toString();
		}
		catch (InvalidPathException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_NAME_INVALID");
		}
		if (originalName.isBlank() || originalName.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_NAME_INVALID");
		}
		String extension = extension(originalName);
		AttachmentKind kind = IMAGE_EXTENSIONS.contains(extension) ? AttachmentKind.IMAGE
				: DOCUMENT_EXTENSIONS.contains(extension) ? AttachmentKind.DOCUMENT : null;
		if (kind == null) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ATTACHMENT_TYPE_UNSUPPORTED");
		}
		String contentType = file.getContentType();
		if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
			contentType = contentType(extension);
		}
		if (kind == AttachmentKind.IMAGE && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ATTACHMENT_TYPE_UNSUPPORTED");
		}
		if (kind == AttachmentKind.IMAGE && file.getSize() > AssistantAttachmentService.MAX_IMAGE_TOTAL_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_IMAGE_PAYLOAD_TOO_LARGE");
		}
		return new ValidatedFile(originalName, contentType, kind);
	}

	private String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String contentType(String extension) {
		return switch (extension) {
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			case "md", "markdown" -> "text/markdown";
			case "txt" -> "text/plain";
			case "csv" -> "text/csv";
			case "json" -> "application/json";
			case "yaml", "yml" -> "application/yaml";
			case "pdf" -> "application/pdf";
			default -> "application/octet-stream";
		};
	}

	private String value(Object value) {
		return value instanceof String text && !text.isBlank() ? text : null;
	}

	private record ValidatedFile(String originalName, String contentType, AttachmentKind kind) {
	}

}
