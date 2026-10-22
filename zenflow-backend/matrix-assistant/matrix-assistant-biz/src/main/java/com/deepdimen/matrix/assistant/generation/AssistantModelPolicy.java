package com.deepdimen.matrix.assistant.generation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelMessage;

/** Fail-closed checks that every local or remote gateway runs before invocation. */
public final class AssistantModelPolicy {

	private AssistantModelPolicy() {
	}

	public static void validateForStreaming(AssistantModelRequest request) {
		if (request.requireNativeStreaming() && !request.model().capabilities().supportsNativeStreaming()) {
			throw new AssistantModelException("MODEL_STREAMING_NOT_SUPPORTED", "当前模型不支持原生流式输出");
		}
		if (!request.model().capabilities().supportsSystemMessage()) {
			throw new AssistantModelException("MODEL_SYSTEM_MESSAGE_NOT_SUPPORTED", "当前模型不支持必需的系统安全指令");
		}
		List<AssistantAttachment> attachments = allAttachments(request);
		DataClassification highest = effectiveContextClassification(request.contextFragments(), attachments);
		if (!request.model()
			.assistantEgress()
			.allows(highest, containsBusinessContext(request.contextFragments(), attachments))) {
			throw new AssistantModelException("MODEL_EGRESS_DENIED", "当前模型不允许接收该级别的业务上下文");
		}
	}

	public static DataClassification effectiveContextClassification(List<ModelContextFragment> fragments,
			List<AssistantAttachment> attachments) {
		DataClassification highest = fragments
			.stream()
			.map(ModelContextFragment::dataClassification)
			.max(Comparator.comparingInt(Enum::ordinal))
			.orElse(DataClassification.PUBLIC);
		if (!attachments.isEmpty() && highest.ordinal() < DataClassification.SENSITIVE.ordinal()) {
			highest = DataClassification.SENSITIVE;
		}
		return highest;
	}

	public static boolean containsBusinessContext(List<ModelContextFragment> fragments,
			List<AssistantAttachment> attachments) {
		return !fragments.isEmpty() || !attachments.isEmpty();
	}

	public static List<AssistantAttachment> allAttachments(AssistantModelRequest request) {
		return allAttachments(request.history(), request.attachments());
	}

	public static List<AssistantAttachment> allAttachments(List<ModelMessage> history,
			List<AssistantAttachment> current) {
		LinkedHashMap<String, AssistantAttachment> attachments = new LinkedHashMap<>();
		history
			.stream()
			.flatMap(message -> message.attachments().stream())
			.forEach(attachment -> attachments.putIfAbsent(attachment.uploadId(), attachment));
		current.forEach(attachment -> attachments.putIfAbsent(attachment.uploadId(), attachment));
		return List.copyOf(attachments.values());
	}

}
