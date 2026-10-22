package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;

public interface AssistantRunEventRepository {

	RunEventEnvelope append(String runId, RunEventType type, String correlationId, Map<String, Object> data,
			Instant occurredAt);

	Optional<RunEventEnvelope> appendDeltaOwned(String runId, String ownerInstanceId, String responseMessageId,
			String correlationId, String delta, String draftContent, Instant occurredAt);

	List<RunEventEnvelope> findAfter(String runId, long afterSeq, int limit);

	int deleteTerminalEventsBefore(Instant cutoff, int limit);

}
