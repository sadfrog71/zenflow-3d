package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantUploadResponse;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.attachment.AssistantAttachmentService;
import com.deepdimen.matrix.common.core.util.R;

class AssistantAttachmentUploadServiceTest {

	private final RemoteFileService remoteFileService = mock(RemoteFileService.class);

	private final AssistantAttachmentService attachmentService = mock(AssistantAttachmentService.class);

	private final AssistantAttachmentUploadService service = new AssistantAttachmentUploadService(remoteFileService,
			attachmentService);

	@Test
	void uploadsMarkdownAndRegistersOwnerScopedMetadata() {
		MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown", "# Notes".getBytes());
		when(remoteFileService.upload(file)).thenReturn(R.ok(Map.of("fileName", "abc.md")));
		when(attachmentService.register(any(), eq("abc.md"), eq("notes.md"), eq("text/markdown"), eq(7L),
				eq(AttachmentKind.DOCUMENT)))
			.thenReturn(new AssistantUploadResponse("upload-1", "abc.md", "notes.md", "text/markdown", 7,
					AttachmentKind.DOCUMENT, Instant.now().plusSeconds(600)));

		AssistantUploadResponse uploaded = service.upload(identity(), file);

		assertEquals("upload-1", uploaded.uploadId());
	}

	@Test
	void rejectsUnsupportedFilesBeforeStorage() {
		MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream",
				new byte[] { 1 });

		assertThrows(ResponseStatusException.class, () -> service.upload(identity(), file));
		verify(remoteFileService, never()).upload(any());
	}

	@Test
	void rejectsInvalidFileNamesBeforeStorage() {
		MockMultipartFile file = new MockMultipartFile("file", "bad\0name.md", "text/markdown", new byte[] { 1 });

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.upload(identity(), file));

		assertEquals("ATTACHMENT_NAME_INVALID", exception.getReason());
		verify(remoteFileService, never()).upload(any());
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(), List.of(), "corr-1",
				"assistant");
	}

}
