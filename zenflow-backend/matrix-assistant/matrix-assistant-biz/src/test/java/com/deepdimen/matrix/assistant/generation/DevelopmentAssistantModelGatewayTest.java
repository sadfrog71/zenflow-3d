package com.deepdimen.matrix.assistant.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import org.junit.jupiter.api.Test;

class DevelopmentAssistantModelGatewayTest {

	private final DevelopmentAssistantModelGateway gateway = new DevelopmentAssistantModelGateway();

	@Test
	void streamsDeterministicCitationAwareResponse() {
		IdentityEnvelope identity = new IdentityEnvelope("1", "2", "3", List.of(), List.of(), List.of(), "correlation",
				"READ_ONLY");
		var model = gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, "run-1", identity,
				"ignored", true, DataClassification.INTERNAL, true));
		var request = new AssistantModelRequest(AssistantModelContract.VERSION, "run-1", "correlation", identity, model,
				"system", List.of(), List.of(new ModelContextFragment("knowledge:1", "title", "content",
						DataClassification.INTERNAL, Map.of())),
				"question", 0.1, 0.8, 256, true);

		var chunks = gateway.stream(request).collectList().block();

		assertEquals(3, chunks.size());
		assertTrue(chunks.get(1).delta().contains("[knowledge:1]"));
		assertTrue(chunks.get(2).terminal());
	}

}
