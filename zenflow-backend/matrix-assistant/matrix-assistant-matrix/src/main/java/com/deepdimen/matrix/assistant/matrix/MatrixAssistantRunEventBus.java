package com.deepdimen.matrix.assistant.matrix;

import java.util.function.Consumer;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.run.AssistantRunEventBus;
import com.deepdimen.matrix.common.sse.distribute.MessageDistributor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Primary
@Component
public class MatrixAssistantRunEventBus implements AssistantRunEventBus {

	private static final String TOPIC_PREFIX = "assistant-run:";

	private final MessageDistributor distributor;

	private final ObjectMapper objectMapper;

	public MatrixAssistantRunEventBus(MessageDistributor distributor, ObjectMapper objectMapper) {
		this.distributor = distributor;
		this.objectMapper = objectMapper;
	}

	@Override
	public void publish(RunEventEnvelope event) {
		distributor.distributeToKey(topic(event.runId()), toJson(event));
	}

	@Override
	public Subscription subscribe(String runId, Consumer<RunEventEnvelope> consumer, Runnable overflowHandler) {
		MessageDistributor.MessageSubscription subscription = distributor.subscribe(topic(runId),
				payload -> consumer.accept(fromJson(payload)), overflowHandler);
		return subscription::close;
	}

	private String topic(String runId) {
		return TOPIC_PREFIX + runId;
	}

	private String toJson(RunEventEnvelope event) {
		try {
			return objectMapper.writeValueAsString(event);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant event serialization failed", ex);
		}
	}

	private RunEventEnvelope fromJson(String payload) {
		try {
			return objectMapper.readValue(payload, RunEventEnvelope.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant event deserialization failed", ex);
		}
	}

}
