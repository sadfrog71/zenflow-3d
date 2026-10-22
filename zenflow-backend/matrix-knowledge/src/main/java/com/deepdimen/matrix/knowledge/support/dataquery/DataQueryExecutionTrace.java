package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Observed application/JDBC stages only. Never model reasoning, SQL text or parameter
 * values.
 */
public final class DataQueryExecutionTrace {

	public static final String VERSION = "execution-trace-v1";

	public record Step(int sequence, String phase, String status, String startedAt, Integer durationMs, String code,
			int attempt, String sqlDigest,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Usage usage) {
		public Step(int sequence, String phase, String status, String startedAt, Integer durationMs, String code,
				int attempt, String sqlDigest) {
			this(sequence, phase, status, startedAt, durationMs, code, attempt, sqlDigest, null);
		}
	}

	/**
	 * Only counts returned by the model response; never a tokenizer estimate or a bill
	 * join.
	 */
	public record Usage(String status, Integer inputTokens, Integer outputTokens, Integer totalTokens) {
		static Usage unavailable(String status) {
			return new Usage(status, null, null, null);
		}

		static Usage reported(Integer input, Integer output, Integer total) {
			if ((input != null && input < 0) || (output != null && output < 0) || (total != null && total < 0))
				return unavailable("INVALID");
			if (input == null && output == null && total == null)
				return unavailable("NOT_RETURNED");
			return new Usage(input != null && output != null && total != null ? "REPORTED" : "PARTIAL", input, output,
					total);
		}
	}

	public record Snapshot(String version, List<Step> steps) {
	}

	private final List<Step> steps = new ArrayList<>();

	private final Map<Integer, Long> running = new LinkedHashMap<>();

	private Consumer<Snapshot> sink = value -> {
	};

	private Consumer<Snapshot> observer = value -> { };

	public synchronized void observe(Consumer<Snapshot> observer) {
		this.observer = Objects.requireNonNull(observer);
		observer.accept(snapshot());
	}

	private int attempts;

	synchronized void attach(Consumer<Snapshot> sink) {
		this.sink = Objects.requireNonNull(sink);
		persist();
	}

	synchronized int nextAttempt() {
		return ++attempts;
	}

	public synchronized Snapshot snapshot() {
		return new Snapshot(VERSION, List.copyOf(steps));
	}

	public synchronized int start(String phase, int attempt, String digest) {
		if (!phase.matches("[A-Z_]{1,40}") || steps.size() >= 64 || attempt < 0 || attempt > 3
				|| (digest != null && !digest.matches("[0-9a-f]{64}")))
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		int index = steps.size();
		steps.add(new Step(index + 1, phase, "RUNNING", Instant.now().toString(), null, null, attempt, digest,
				modelPhase(phase) ? Usage.unavailable("PENDING") : null));
		running.put(index, System.nanoTime());
		try {
			persist();
		}
		catch (RuntimeException error) {
			// The action has not started; do not retain an apparent invocation.
			running.remove(index);
			steps.remove(index);
			throw error;
		}
		return index;
	}

	public void success(int index) {
		finish(index, "SUCCEEDED", null, null);
	}

	public void failure(int index, String code) {
		finish(index, "FAILED", safe(code), null);
	}

	private synchronized void finish(int index, String state, String code, Usage usage) {
		Long start = running.remove(index);
		if (start == null)
			return;
		var old = steps.get(index);
		if (usage == null && modelPhase(old.phase()))
			usage = Usage.unavailable("FAILED".equals(state) ? "CALL_FAILED" : "NOT_RETURNED");
		steps.set(index,
				new Step(old.sequence(), old.phase(), state, old.startedAt(),
						(int) Math.min(Integer.MAX_VALUE, Math.max(0, (System.nanoTime() - start) / 1_000_000)), code,
						old.attempt(), old.sqlDigest(), usage));
		persist();
	}

	synchronized void failOpen(String code) {
		for (int index : List.copyOf(running.keySet()))
			failure(index, code);
		// The failure handler was actually reached; do not infer an unobserved
		// lower-level stage.
		int terminal = start("QUERY_FAILURE", 0, null);
		failure(terminal, code);
	}

	public <T> T measure(String phase, Supplier<T> action) {
		int index = start(phase, 0, null);
		try {
			var result = action.get();
			success(index);
			return result;
		}
		catch (RuntimeException error) {
			failure(index, error instanceof DataQueryException ? error.getMessage() : "STAGE_FAILED");
			throw error;
		}
	}

	public <T> T measureModel(String phase, Supplier<T> action, java.util.function.Function<T, Usage> usage) {
		if (!modelPhase(phase))
			throw new IllegalArgumentException("Not an observed model phase");
		int index = start(phase, 0, null);
		try {
			T result = action.get();
			finish(index, "SUCCEEDED", null, usage.apply(result));
			return result;
		}
		catch (RuntimeException error) {
			failure(index, error instanceof DataQueryException ? error.getMessage() : "STAGE_FAILED");
			throw error;
		}
	}

	private static boolean modelPhase(String phase) {
		return "MODEL_GENERATE".equals(phase) || "MODEL_REGENERATE".equals(phase)
				|| "MODEL_TECHNICAL_REPAIR".equals(phase);
	}

	private void persist() {
		sink.accept(snapshot());
		observer.accept(snapshot());
	}

	private static String safe(String code) {
		return code != null && code.matches("[A-Z_]{1,64}") ? code : "STAGE_FAILED";
	}

	@Override
	public String toString() {
		return "ExecutionTrace[structured stages only]";
	}

}
