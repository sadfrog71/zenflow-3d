package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;

public interface AssistantRunMetricsRepository {

	record Sample(String tenantId, AssistantRunMetricsService.Input run, List<RunEventEnvelope> events,
			List<AssistantStepMetrics.Step> steps, boolean stepsTruncated) {
		public Sample(String tenantId, AssistantRunMetricsService.Input run, List<RunEventEnvelope> events) {
			this(tenantId, run, events, List.of(), false);
		}

		public Sample {
			events = List.copyOf(events);
			steps = List.copyOf(steps);
		}
	}

	List<Sample> read(String tenantId, Instant from, Instant to, int limit);

}
