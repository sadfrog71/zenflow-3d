package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.FinishReason;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.Identity;

/**
 * Optional, no-tools interpretation of a saved result; never invokes a query executor.
 */
public final class DataQueryResultAnalysis implements AutoCloseable {

	static final int MAX_OUTPUT = 16000;
	static final int TIMEOUT_SECONDS = 180;

	public record Request(String requestId, int revision, String modelName, String focus) {
	}

	record Source(String question, DataQueryVisualizations.Snapshot snapshot, String digest) {
	}

	interface Access {

		Source load(Identity owner, String queryId);

	}

	record Model(String alias, String name, Supplier<StreamingChatModel> create) {
	}

	interface Models {

		Model resolve(Identity owner, String queryId, String requestedAlias);

	}

	public record Analysis(String requestId, String queryId, String status, String modelAlias, String modelName,
			String sourceDigest, String promptVersion, String focus, String content, String code,
			DataQueryAnalysisPrompt.Scope scope, String startedAt, String finishedAt, long elapsedMs,
			Integer inputTokens, Integer outputTokens, Integer totalTokens, String finishReason) {
	}

	public record View(String queryId, int revision, Analysis attempt, Analysis result) {
	}

	public record Event(String type, String text, View view, String code) {
	}

	private final Access access;

	private final Models models;

	private final DataQueryAnalysisStore store;

	private final ObjectMapper json;

	private final Clock clock;

	private final Map<String, Run> running = new ConcurrentHashMap<>();

	private final Map<Identity, String> users = new HashMap<>();

	private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2, r -> {
		var t = new Thread(r, "data-query-analysis-guard");
		t.setDaemon(true);
		return t;
	});

	DataQueryResultAnalysis(DataSource platform, ObjectMapper json, Clock clock, Access access, Models models) {
		this.access = access;
		this.models = models;
		this.store = new DataQueryAnalysisStore(platform, json, clock);
		this.json = json;
		this.clock = clock;
	}

	public View read(Identity owner, String queryId) {
		Source source = access.load(owner, queryId);
		var e = store.read(owner, queryId);
		verifyDigest(e, source);
		// A process restart must not leave a permanent RUNNING lease. No implicit rerun.
		if ("RUNNING".equals(e.status()) && e.attempt() != null
				&& Instant.parse(e.attempt().startedAt()).plusSeconds(TIMEOUT_SECONDS + 15).isBefore(clock.instant())) {
			store.finish(owner, queryId, e.revision(),
					terminal(e.attempt(), "FAILED", "ANALYSIS_INTERRUPTED", "", null, clock));
			e = store.read(owner, queryId);
		}
		return view(queryId, e);
	}

	public Flux<Event> start(Identity owner, String queryId, Request request) {
		validate(request);
		return Flux.<Event>create(sink -> {
			Run run = null;
			try {
				Source source = access.load(owner, queryId);
				var previous = read(owner, queryId);
				var model = models.resolve(owner, queryId, request.modelName());
				var prompt = DataQueryAnalysisPrompt.prepare(json, source.question(), source.snapshot(),
						request.focus());
				String digest = DataQueryMetadataRepository.fingerprint(model.alias(), model.name(), source.digest(),
						request.focus() == null ? "" : request.focus());
				var prior = store.read(owner, queryId);
				if (request.requestId().equals(prior.requestId())) {
					if (!digest.equals(prior.requestDigest()))
						throw new DataQueryException("ANALYSIS_REQUEST_CHANGED");
					if ("RUNNING".equals(prior.status()))
						throw new DataQueryException("ANALYSIS_IN_PROGRESS");
					sink.next(new Event("done", null, view(queryId, prior), null));
					sink.complete();
					return;
				}
				Analysis attempt = new Analysis(request.requestId(), queryId, "RUNNING", model.alias(), model.name(),
						source.digest(), DataQueryAnalysisPrompt.VERSION,
						request.focus() == null ? "" : request.focus(), "", null, prompt.scope(),
						clock.instant().toString(), null, 0, null, null, null, null);
				synchronized (users) {
					if (running.size() >= 4 || users.containsKey(owner) || running.containsKey(queryId))
						throw new DataQueryException("ANALYSIS_BUSY");
					var entry = store.begin(owner, queryId, request.revision(), request.requestId(), digest, attempt);
					if (!entry.attempt().requestId().equals(attempt.requestId()) || !"RUNNING".equals(entry.status()))
						throw new DataQueryException("ANALYSIS_CHANGED");
					run = new Run(owner, source, entry, model, prompt, sink);
					running.put(queryId, run);
					users.put(owner, queryId);
				}
				Run current = run;
				sink.onCancel(() -> current.stop("CANCELLED", "ANALYSIS_CANCELLED", null));
				sink.next(new Event("started", null,
						new View(queryId, run.entry.revision(), attempt, previous.result()), null));
				run.timer = timers.scheduleWithFixedDelay(run::check, 5, 5, TimeUnit.SECONDS);
				if (!sink.isCancelled())
					run.generate();
				else
					run.stop("CANCELLED", "ANALYSIS_CANCELLED", null);
			}
			catch (Exception failure) {
				if (run != null)
					run.stop("FAILED", safe(failure), null);
				else if (!sink.isCancelled()) {
					sink.next(new Event("error", null, null, safe(failure)));
					sink.complete();
				}
			}
		}, FluxSink.OverflowStrategy.BUFFER).subscribeOn(Schedulers.boundedElastic());
	}

	public View cancel(Identity owner, String queryId, String requestId) {
		var view = read(owner, queryId);
		if (view.attempt() == null || !Objects.equals(view.attempt().requestId(), requestId))
			throw new DataQueryException("ANALYSIS_CHANGED");
		var run = running.get(queryId);
		if (run != null && run.owner.equals(owner) && run.entry.requestId().equals(requestId))
			run.stop("CANCELLED", "ANALYSIS_CANCELLED", null);
		else if ("RUNNING".equals(view.attempt().status()))
			throw new DataQueryException("ANALYSIS_CANCEL_UNAVAILABLE");
		return read(owner, queryId);
	}

	private final class Run {

		final Identity owner;

		final Source source;

		final DataQueryAnalysisStore.Entry entry;

		final Model model;

		final DataQueryAnalysisPrompt.Prepared prompt;

		final FluxSink<Event> sink;

		final long started = System.nanoTime();

		final StringBuilder text = new StringBuilder();

		boolean stopped;

		StreamingHandle handle;

		ScheduledFuture<?> timer;

		Run(Identity owner, Source source, DataQueryAnalysisStore.Entry entry, Model model,
				DataQueryAnalysisPrompt.Prepared prompt, FluxSink<Event> sink) {
			this.owner = owner;
			this.source = source;
			this.entry = entry;
			this.model = model;
			this.prompt = prompt;
			this.sink = sink;
		}

		void generate() {
			// Analysis has its own usage/persistence; inherited chat listeners must not
			// overwrite the original question's answer, timing, or billing attribution.
			var inherited = com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.get();
			com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.clear();
			try {
				generateIsolated();
			}
			finally {
				com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.clear();
				if (inherited != null)
					com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.set(inherited);
			}
		}

		private void generateIsolated() {
			var chat = model.create().get();
			synchronized (this) {
				if (stopped)
					return;
			}
			chat.chat(prompt.request(), new StreamingChatResponseHandler() {
				@Override
				public void onPartialResponse(String partial) {
					part(partial, null);
				}

				@Override
				public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
					part(partial.text(), context.streamingHandle());
				}

				@Override
				public void onPartialThinking(PartialThinking ignored, PartialThinkingContext context) {
					retain(context.streamingHandle());
				}

				@Override
				public void onCompleteResponse(ChatResponse response) {
					if (response == null || response.aiMessage() == null) {
						stop("FAILED", "ANALYSIS_EMPTY", response);
						return;
					}
					if (response.aiMessage().hasToolExecutionRequests()) {
						stop("FAILED", "ANALYSIS_UNEXPECTED_TOOL", response);
						return;
					}
					if (response.finishReason() == FinishReason.LENGTH) {
						stop("INCOMPLETE", "ANALYSIS_OUTPUT_LIMIT", response);
						return;
					}
					if (response.finishReason() != FinishReason.STOP) {
						stop("FAILED", "ANALYSIS_INCOMPLETE", response);
						return;
					}
					synchronized (Run.this) {
						if (stopped)
							return;
						String finalText = response.aiMessage().text();
						if (finalText != null && !finalText.isBlank()) {
							text.setLength(0);
							text.append(finalText, 0, Math.min(MAX_OUTPUT, finalText.length()));
							if (finalText.length() > MAX_OUTPUT) {
								stop("INCOMPLETE", "ANALYSIS_OUTPUT_LIMIT", response);
								return;
							}
						}
					}
					stop("SUCCEEDED", null, response);
				}

				@Override
				public void onError(Throwable ignored) {
					stop("FAILED", "ANALYSIS_MODEL_FAILED", null);
				}
			});
		}

		synchronized void retain(StreamingHandle value) {
			if (value != null)
				handle = value;
			if (stopped && handle != null)
				cancelProvider();
		}

		synchronized void part(String part, StreamingHandle value) {
			retain(value);
			if (stopped || part == null || part.isEmpty())
				return;
			if (text.length() + part.length() > MAX_OUTPUT) {
				stop("INCOMPLETE", "ANALYSIS_OUTPUT_LIMIT", null);
				return;
			}
			text.append(part);
			if (!sink.isCancelled())
				sink.next(new Event("delta", part, null, null));
		}

		void check() {
			synchronized (this) {
				if (stopped) {
					if (timer != null)
						timer.cancel(false);
					return;
				}
			}
			if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)) {
				stop("FAILED", "ANALYSIS_TIMEOUT", null);
				return;
			}
			try {
				if (!source.digest().equals(access.load(owner, entry.attempt().queryId()).digest()))
					throw new DataQueryException("ANALYSIS_SOURCE_CHANGED");
				synchronized (this) {
					if (!stopped && !sink.isCancelled())
						sink.next(new Event("heartbeat", null, null, null));
				}
			}
			catch (Exception revoked) {
				stop("FAILED", safe(revoked), null);
			}
		}

		synchronized void stop(String status, String code, ChatResponse response) {
			if (stopped)
				return;
			stopped = true;
			if (timer != null)
				timer.cancel(false);
			if (!"SUCCEEDED".equals(status))
				cancelProvider();
			boolean authorized = true;
			try {
				if (!source.digest().equals(access.load(owner, entry.attempt().queryId()).digest()))
					throw new DataQueryException("ANALYSIS_SOURCE_CHANGED");
			}
			catch (Exception revoked) {
				status = "FAILED";
				code = safe(revoked);
				authorized = false;
				text.setLength(0);
				cancelProvider();
			}
			if ("SUCCEEDED".equals(status) && text.toString().isBlank()) {
				status = "FAILED";
				code = "ANALYSIS_EMPTY";
			}
			try {
				var result = terminal(entry.attempt(), status, code, text.toString(), response, clock);
				store.finish(owner, result.queryId(), entry.revision(), result);
				if (!sink.isCancelled())
					sink.next(authorized
							? new Event("done", null, view(result.queryId(), store.read(owner, result.queryId())), null)
							: new Event("error", null, null, code));
			}
			catch (Exception unavailable) {
				if (!sink.isCancelled())
					sink.next(new Event("error", null, null, "ANALYSIS_STORAGE_UNAVAILABLE"));
			}
			finally {
				synchronized (users) {
					running.remove(entry.attempt().queryId(), this);
					users.remove(owner, entry.attempt().queryId());
				}
				if (!sink.isCancelled())
					sink.complete();
			}
		}

		private void cancelProvider() {
			try {
				if (handle != null)
					handle.cancel();
			}
			catch (RuntimeException ignored) {
			}
		}

	}

	private static Analysis terminal(Analysis a, String status, String code, String content, ChatResponse response,
			Clock clock) {
		var tokens = response == null ? null : response.tokenUsage();
		if (tokens != null && ((tokens.inputTokenCount() != null && tokens.inputTokenCount() < 0)
				|| (tokens.outputTokenCount() != null && tokens.outputTokenCount() < 0)
				|| (tokens.totalTokenCount() != null && tokens.totalTokenCount() < 0)))
			tokens = null;
		return new Analysis(a.requestId(), a.queryId(), status, a.modelAlias(), a.modelName(), a.sourceDigest(),
				a.promptVersion(), a.focus(), content, code, a.scope(), a.startedAt(), clock.instant().toString(),
				Math.max(0, Duration.between(Instant.parse(a.startedAt()), clock.instant()).toMillis()),
				tokens == null ? null : tokens.inputTokenCount(), tokens == null ? null : tokens.outputTokenCount(),
				tokens == null ? null : tokens.totalTokenCount(),
				response == null || response.finishReason() == null ? null : response.finishReason().name());
	}

	private static View view(String queryId, DataQueryAnalysisStore.Entry e) {
		return new View(queryId, e.revision(), e.attempt(), e.result());
	}

	private static void verifyDigest(DataQueryAnalysisStore.Entry e, Source source) {
		for (var a : Arrays.asList(e.attempt(), e.result()))
			if (a != null && !source.digest().equals(a.sourceDigest()))
				throw new DataQueryException("ANALYSIS_SOURCE_CHANGED");
	}

	private static void validate(Request request) {
		if (request == null || request.requestId() == null
				|| !request.requestId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
				|| request.revision() < 0 || request.revision() == Integer.MAX_VALUE
				|| request.modelName() != null && (request.modelName().isBlank() || request.modelName().length() > 128)
				|| request.focus() != null && request.focus().length() > 500)
			throw new DataQueryException("INVALID_REQUEST");
	}

	private static String safe(Throwable error) {
		return error instanceof DataQueryException e ? e.getMessage() : "ANALYSIS_UNAVAILABLE";
	}

	@Override
	public void close() {
		for (var run : List.copyOf(running.values()))
			run.stop("CANCELLED", "ANALYSIS_INTERRUPTED", null);
		timers.shutdownNow();
	}

}
