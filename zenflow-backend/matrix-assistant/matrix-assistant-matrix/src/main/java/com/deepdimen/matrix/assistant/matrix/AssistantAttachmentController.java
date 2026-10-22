package com.deepdimen.matrix.assistant.matrix;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantUploadResponse;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;

@RestController
@RequestMapping("/assistant/uploads")
public class AssistantAttachmentController {

	private final AssistantIdentityResolver identityResolver;

	private final AssistantAttachmentUploadService uploadService;

	public AssistantAttachmentController(AssistantIdentityResolver identityResolver,
			AssistantAttachmentUploadService uploadService) {
		this.identityResolver = identityResolver;
		this.uploadService = uploadService;
	}

	@PostMapping
	public AssistantUploadResponse upload(@RequestPart("file") MultipartFile file) {
		return uploadService.upload(identityResolver.currentIdentity(), file);
	}

}
