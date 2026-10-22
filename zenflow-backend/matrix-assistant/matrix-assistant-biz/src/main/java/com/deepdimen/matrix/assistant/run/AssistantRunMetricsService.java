package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Pure metrics calculation. Callers must authorize and bound repository reads before
 * invoking.
 */
@Component
public class AssistantRunMetricsService {

	public enum Metric {

		QUEUE_WAIT, ROUTE_TIME, CONTEXT_TIME, MODEL_WAIT, VALIDATION_HOLD, REMAINING_GENERATION, FIRST_VISIBLE, TOTAL

	}

	public record Input(String runId, RunStatus status, ConversationIntent intent, ExecutionMode executionMode,
			Instant acceptedAt, Instant startedAt, Instant completedAt, long lastEventSeq, Instant routeDecidedAt,
			Instant modelFirstChunkAt, Instant firstVisibleAnswerAt, String provider, String model) {
		public Input(String runId, RunStatus status, ConversationIntent intent, ExecutionMode executionMode,
				Instant acceptedAt, Instant startedAt, Instant completedAt, long lastEventSeq, Instant routeDecidedAt,
				Instant modelFirstChunkAt, Instant firstVisibleAnswerAt) {
			this(runId, status, intent, executionMode, acceptedAt, startedAt, completedAt, lastEventSeq, routeDecidedAt,
					modelFirstChunkAt, firstVisibleAnswerAt, null, null);
		}

		public Input(String runId, RunStatus status, ConversationIntent intent, ExecutionMode executionMode,
				Instant acceptedAt, Instant startedAt, Instant completedAt, long lastEventSeq) {
			this(runId, status, intent, executionMode, acceptedAt, startedAt, completedAt, lastEventSeq, null, null,
					null);
		}
	}

	public record Timing(String runId, RunStatus status, ConversationIntent intent, ExecutionMode executionMode,
			Instant t0, Instant t1, Instant t2, Instant t3, Instant t4, Instant t5, Instant t6,
			boolean eventHistoryComplete, Map<Metric, Long> durationsMillis, List<Metric> invalidClockOrder,
			List<Metric> notApplicable, String provider, String model) {
		public Timing {
			durationsMillis = Collections.unmodifiableMap(new EnumMap<>(durationsMillis));
			invalidClockOrder = List.copyOf(invalidClockOrder);
			notApplicable = List.copyOf(notApplicable);
		}
	}

	public record Distribution(int sampleCount, int unavailableCount, int notApplicableCount, Long p50Millis,
			Long p95Millis, Long p99Millis) {
	}

	public record Summary(int runCount, Map<RunStatus, Long> statusCounts, Map<Metric, Distribution> metrics,
			List<String> slowRunIds, int incompleteEventHistories) {
	}

	public record IntentSummary(ConversationIntent intent, Summary summary) {
	}

	public record ModelSummary(String provider, String model, Summary summary) {
	}

	private record ModelKey(String provider, String model) {
	}

	public record QueryResult(Instant from, Instant to, int sampleLimit, boolean truncated, Summary summary,
			List<IntentSummary> byIntent, List<ModelSummary> byModel, AssistantStepMetrics.Summary subStages) {
	}

	public QueryResult query(com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope identity,
			AssistantRunMetricsRepository repository, Instant from, Instant to, int limit) {
		if (identity == null || identity.tenantId() == null || identity.tenantId().isBlank()
				|| identity.userId() == null || identity.userId().isBlank())
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "无有效租户身份");
		if (from == null || to == null || !from.isBefore(to)
				|| Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0 || limit < 1 || limit > 200)
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST, "查询须为不超过7天的时间窗，样本上限1至200");
		List<AssistantRunMetricsRepository.Sample> samples = repository.read(identity.tenantId(), from, to, limit + 1);
		if (samples.size() > limit + 1)
			throw new IllegalStateException("METRICS_REPOSITORY_LIMIT_VIOLATION");
		for (var sample : samples)
			if (!identity.tenantId().equals(sample.tenantId()) || sample.run().acceptedAt() == null
					|| sample.run().acceptedAt().isBefore(from) || !sample.run().acceptedAt().isBefore(to))
				throw new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.FORBIDDEN, "指标结果超出当前租户或时间范围");
		List<Timing> timings = samples.stream()
			.limit(limit)
			.map(sample -> calculate(sample.run(), sample.events()))
			.toList();
		var intents = new java.util.LinkedHashMap<ConversationIntent, List<Timing>>();
		var models = new java.util.LinkedHashMap<ModelKey, List<Timing>>();
		for (Timing timing : timings) {
			intents.computeIfAbsent(timing.intent(), ignored -> new ArrayList<>()).add(timing);
			models.computeIfAbsent(new ModelKey(timing.provider(), timing.model()), ignored -> new ArrayList<>())
				.add(timing);
		}
		return new QueryResult(from, to, limit, samples.size() > limit, summarize(timings),
				intents.entrySet()
					.stream()
					.map(entry -> new IntentSummary(entry.getKey(), summarize(entry.getValue())))
					.toList(),
				models.entrySet()
					.stream()
					.map(entry -> new ModelSummary(entry.getKey().provider(), entry.getKey().model(),
							summarize(entry.getValue())))
					.toList(),
				AssistantStepMetrics.summarize(samples.stream().limit(limit).toList()));
	}

	public Timing calculate(Input run, List<RunEventEnvelope> events) {
		if (run == null || run.runId() == null || run.status() == null || run.lastEventSeq() < 0)
			throw new IllegalArgumentException("METRICS_INPUT_INVALID");
		TreeMap<Long, RunEventEnvelope> ordered = new TreeMap<>();
		for (RunEventEnvelope event : events) {
			if (!run.runId().equals(event.runId()) || event.seq() <= 0 || event.seq() > run.lastEventSeq())
				throw new IllegalArgumentException("METRICS_EVENT_SCOPE_INVALID");
			RunEventEnvelope prior = ordered.putIfAbsent(event.seq(), event);
			if (prior != null && (prior.type() != event.type()
					|| !java.util.Objects.equals(prior.occurredAt(), event.occurredAt())))
				throw new IllegalArgumentException("METRICS_EVENT_CONFLICT");
		}
		// With unique positive seq <= lastSeq, count equality proves continuity from seq
		// 1.
		boolean complete = ordered.size() == run.lastEventSeq();
		List<RunEventEnvelope> facts = complete ? new ArrayList<>(ordered.values()) : List.of();
		Instant t2 = run.routeDecidedAt() != null ? run.routeDecidedAt() : first(facts, RunEventType.ROUTE_DECIDED);
		Instant t3 = first(facts, RunEventType.ANALYSIS_STARTED);
		if (t3 == null && facts.stream().noneMatch(event -> event.type() == RunEventType.WORKFLOW_STARTED))
			t3 = first(facts, RunEventType.CONTEXT_COMPLETED);
		boolean direct = run.executionMode() == ExecutionMode.DIRECT_CHAT
				|| run.executionMode() == ExecutionMode.CAPABILITY_HELP;
		if (direct)
			t3 = null;
		Instant t4 = run.modelFirstChunkAt() != null ? run.modelFirstChunkAt()
				: first(facts, RunEventType.MODEL_FIRST_CHUNK);
		Instant t5 = run.firstVisibleAnswerAt() != null ? run.firstVisibleAnswerAt()
				: first(facts, RunEventType.RESPONSE_DELTA);
		var durations = new EnumMap<Metric, Long>(Metric.class);
		var invalid = new ArrayList<Metric>();
		duration(durations, invalid, Metric.QUEUE_WAIT, run.acceptedAt(), run.startedAt());
		duration(durations, invalid, Metric.ROUTE_TIME, run.startedAt(), t2);
		duration(durations, invalid, Metric.CONTEXT_TIME, t2, t3);
		duration(durations, invalid, Metric.MODEL_WAIT, direct ? t2 : t3, t4);
		duration(durations, invalid, Metric.VALIDATION_HOLD, t4, t5);
		duration(durations, invalid, Metric.REMAINING_GENERATION, t5, run.completedAt());
		duration(durations, invalid, Metric.FIRST_VISIBLE, run.acceptedAt(), t5);
		duration(durations, invalid, Metric.TOTAL, run.acceptedAt(), run.completedAt());
		return new Timing(run.runId(), run.status(), run.intent(), run.executionMode(), run.acceptedAt(),
				run.startedAt(), t2, t3, t4, t5, run.completedAt(), complete, durations, invalid,
				direct ? List.of(Metric.CONTEXT_TIME) : List.of(), normalizedMetadata(run.provider()),
				normalizedMetadata(run.model()));
	}

	/** Nearest-rank percentiles; missing and invalid durations never enter the sample. */
	public Summary summarize(List<Timing> timings) {
		if (timings.size() > 10000)
			throw new IllegalArgumentException("METRICS_SAMPLE_LIMIT");
		var ids = new java.util.HashSet<String>();
		for (Timing timing : timings)
			if (!ids.add(timing.runId()))
				throw new IllegalArgumentException("METRICS_DUPLICATE_RUN");
		var statuses = new EnumMap<RunStatus, Long>(RunStatus.class);
		timings.forEach(timing -> statuses.merge(timing.status(), 1L, Long::sum));
		var distributions = new EnumMap<Metric, Distribution>(Metric.class);
		for (Metric metric : Metric.values()) {
			List<Long> values = timings.stream()
				.map(timing -> timing.durationsMillis().get(metric))
				.filter(java.util.Objects::nonNull)
				.sorted()
				.toList();
			int notApplicable = (int) timings.stream()
				.filter(timing -> timing.notApplicable().contains(metric))
				.count();
			distributions.put(metric, new Distribution(values.size(), timings.size() - values.size() - notApplicable,
					notApplicable, percentile(values, 50), percentile(values, 95), percentile(values, 99)));
		}
		List<String> slow = timings.stream()
			.filter(timing -> timing.durationsMillis().get(Metric.TOTAL) != null)
			.sorted(java.util.Comparator.<Timing, Long>comparing(timing -> timing.durationsMillis().get(Metric.TOTAL))
				.reversed()
				.thenComparing(Timing::runId))
			.limit(20)
			.map(Timing::runId)
			.toList();
		return new Summary(timings.size(), Map.copyOf(statuses), Map.copyOf(distributions), slow,
				(int) timings.stream().filter(timing -> !timing.eventHistoryComplete()).count());
	}

	private String normalizedMetadata(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private Instant first(List<RunEventEnvelope> facts, RunEventType type) {
		return facts.stream()
			.filter(event -> event.type() == type)
			.findFirst()
			.map(RunEventEnvelope::occurredAt)
			.orElse(null);
	}

	private void duration(Map<Metric, Long> output, List<Metric> invalid, Metric metric, Instant start, Instant end) {
		Long millis = null;
		if (start != null && end != null) {
			if (end.isBefore(start))
				invalid.add(metric);
			else {
				try {
					millis = Duration.between(start, end).toMillis();
				}
				catch (ArithmeticException ex) {
					invalid.add(metric);
				}
			}
		}
		output.put(metric, millis);
	}

	private Long percentile(List<Long> values, int percentile) {
		return values.isEmpty() ? null : values.get((int) Math.ceil(values.size() * percentile / 100.0) - 1);
	}

}
