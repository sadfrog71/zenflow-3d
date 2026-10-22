package com.deepdimen.matrix.assistant.run;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Deletes only old events belonging to terminal runs; messages and traces remain. */
@Service
public class AssistantRunEventCleanupService {

	private final AssistantRunEventRepository repository;

	private final Duration retention;

	public AssistantRunEventCleanupService(AssistantRunEventRepository repository,
			@Value("${matrix.assistant.events.retention-days:7}") long retentionDays) {
		this.repository = repository;
		this.retention = Duration.ofDays(Math.max(1, retentionDays));
	}

	@Scheduled(fixedDelayString = "${matrix.assistant.events.cleanup-interval-ms:3600000}")
	public int cleanup() {
		int deleted = 0;
		int batch;
		do {
			batch = repository.deleteTerminalEventsBefore(Instant.now().minus(retention), 1000);
			deleted += batch;
		}
		while (batch == 1000);
		return deleted;
	}

}
