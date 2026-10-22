package com.deepdimen.matrix.assistant.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import org.junit.jupiter.api.Test;

class AssistantModelPolicyTest {

	@Test
	void deniesSensitiveBusinessContextBeforeInvocation() {
		AssistantModelException failure = assertThrows(AssistantModelException.class, () -> AssistantModelPolicy
			.validateForStreaming(request(true, DataClassification.PUBLIC, DataClassification.SENSITIVE)));
		assertEquals("MODEL_EGRESS_DENIED", failure.errorCode());
	}

	@Test
	void deniesNonStreamingModelBeforeInvocation() {
		AssistantModelException failure = assertThrows(AssistantModelException.class, () -> AssistantModelPolicy
			.validateForStreaming(request(false, DataClassification.SENSITIVE, DataClassification.PUBLIC)));
		assertEquals("MODEL_STREAMING_NOT_SUPPORTED", failure.errorCode());
	}

	@Test
	void deniesModelWithoutSystemMessageSupport() {
		AssistantModelException failure = assertThrows(AssistantModelException.class, () -> AssistantModelPolicy
			.validateForStreaming(request(true, DataClassification.SENSITIVE, DataClassification.PUBLIC, false)));
		assertEquals("MODEL_SYSTEM_MESSAGE_NOT_SUPPORTED", failure.errorCode());
	}

	@Test
	void treatsUnclassifiedUserUploadsAsSensitive() {
		AssistantModelRequest request = request(true, DataClassification.INTERNAL, DataClassification.PUBLIC);
		AssistantAttachment attachment = new AssistantAttachment("upload-1", "tenant/report.md", "report.md",
				"text/markdown", 128, AttachmentKind.DOCUMENT);
		AssistantModelRequest withAttachment = new AssistantModelRequest(request.contractVersion(), request.runId(),
				request.correlationId(), request.identity(), request.model(), request.systemPrompt(), request.history(),
				List.of(), request.userMessage(), request.temperature(), request.topP(), request.maxOutputTokens(),
				request.requireNativeStreaming(), List.of(attachment));

		AssistantModelException failure = assertThrows(AssistantModelException.class,
				() -> AssistantModelPolicy.validateForStreaming(withAttachment));

		assertEquals("MODEL_EGRESS_DENIED", failure.errorCode());
	}

	private AssistantModelRequest request(boolean streaming, DataClassification allowed, DataClassification actual) {
		return request(streaming, allowed, actual, true);
	}

	private AssistantModelRequest request(boolean streaming, DataClassification allowed, DataClassification actual,
			boolean systemMessage) {
		IdentityEnvelope identity = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(),
				"policy-test", "ASSISTANT_MODEL");
		ResolvedAssistantModel model = new ResolvedAssistantModel(AssistantModelContract.VERSION, "100", "model",
				"openai", new ModelCapabilities(streaming, 8192, 1024, systemMessage),
				new AssistantEgress(DeploymentDomain.EXTERNAL, "cn", true, allowed, "policy-v1"));
		return new AssistantModelRequest(AssistantModelContract.VERSION, "run-1", "policy-test", identity, model,
				"platform safety", List.of(), List.of(new ModelContextFragment("C1", "title", "content", actual, null)),
				"question", null, null, null, true);
	}

}
