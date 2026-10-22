package com.deepdimen.matrix.assistant.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

class AssistantAttachmentServiceTest {

	private final InMemoryAssistantAttachmentRepository repository = new InMemoryAssistantAttachmentRepository();

	private final AssistantAttachmentService service = new AssistantAttachmentService(repository,
			Duration.ofMinutes(30));

	@Test
	void createsTheProductionServiceThroughConstructorInjection() {
		try (var context = new AnnotationConfigApplicationContext()) {
			context.registerBean(AssistantAttachmentRepository.class, InMemoryAssistantAttachmentRepository::new);
			context.register(AssistantAttachmentService.class);
			context.refresh();

			assertNotNull(context.getBean(AssistantAttachmentService.class));
		}
	}

	@Test
	void resolvesOnlyTheCurrentOwnersUnexpiredUploads() {
		IdentityEnvelope owner = identity("user-1");
		var uploaded = service.register(owner, "tenant/report.md", "report.md", "text/markdown", 256,
				AttachmentKind.DOCUMENT);

		assertEquals(uploaded.uploadId(), service.resolve(owner, List.of(uploaded.uploadId())).get(0).uploadId());
		assertThrows(ResponseStatusException.class,
				() -> service.resolve(identity("user-2"), List.of(uploaded.uploadId())));
	}

	@Test
	void rejectsExpiredUploads() {
		IdentityEnvelope owner = identity("user-1");
		repository.save(new AssistantAttachmentRepository.UploadRecord("expired", "tenant-1", "user-1", "tenant/old.md",
				"old.md", "text/markdown", 12, AttachmentKind.DOCUMENT, Instant.now().minusSeconds(3600),
				Instant.now().minusSeconds(1)));

		assertThrows(ResponseStatusException.class, () -> service.resolve(owner, List.of("expired")));
	}

	@Test
	void consumedUploadsRemainReusableAfterExpiry() {
		IdentityEnvelope owner = identity("user-1");
		var attachment = new com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment(
				"consumed", "tenant/kept.md", "kept.md", "text/markdown", 12, AttachmentKind.DOCUMENT);
		repository.save(new AssistantAttachmentRepository.UploadRecord("consumed", "tenant-1", "user-1",
				"tenant/kept.md", "kept.md", "text/markdown", 12, AttachmentKind.DOCUMENT,
				Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1)));

		service.markConsumed(owner, List.of(attachment));

		assertEquals("consumed", service.resolve(owner, List.of("consumed")).get(0).uploadId());
	}

	@Test
	void rejectsDuplicateCountAndAggregateLimitViolations() {
		IdentityEnvelope owner = identity("user-1");
		var uploaded = service.register(owner, "tenant/report.md", "report.md", "text/markdown", 256,
				AttachmentKind.DOCUMENT);
		assertThrows(ResponseStatusException.class,
				() -> service.resolve(owner, List.of(uploaded.uploadId(), uploaded.uploadId())));
		assertThrows(ResponseStatusException.class,
				() -> service.resolve(owner, List.of("a", "b", "c", "d", "e", "f")));

		List<String> largeDocuments = List.of("large-1", "large-2", "large-3");
		for (String uploadId : largeDocuments) {
			repository.save(new AssistantAttachmentRepository.UploadRecord(uploadId, "tenant-1", "user-1",
					"tenant/" + uploadId + ".md", uploadId + ".md", "text/markdown", 15L * 1024 * 1024,
					AttachmentKind.DOCUMENT, Instant.now(), Instant.now().plusSeconds(600)));
		}
		assertThrows(ResponseStatusException.class, () -> service.resolve(owner, largeDocuments));
	}

	@Test
	void rejectsImagePayloadAboveTenMegabytes() {
		IdentityEnvelope owner = identity("user-1");
		assertThrows(ResponseStatusException.class, () -> service.register(owner, "tenant/large.png", "large.png",
				"image/png", 10L * 1024 * 1024 + 1, AttachmentKind.IMAGE));
		for (String uploadId : List.of("image-1", "image-2")) {
			repository.save(new AssistantAttachmentRepository.UploadRecord(uploadId, "tenant-1", "user-1",
					"tenant/" + uploadId + ".png", uploadId + ".png", "image/png", 6L * 1024 * 1024,
					AttachmentKind.IMAGE, Instant.now(), Instant.now().plusSeconds(600)));
		}
		assertThrows(ResponseStatusException.class, () -> service.resolve(owner, List.of("image-1", "image-2")));
	}

	private IdentityEnvelope identity(String userId) {
		return new IdentityEnvelope("tenant-1", userId, "org-1", List.of("org-1"), List.of(), List.of(), "corr-1",
				"assistant");
	}

}
