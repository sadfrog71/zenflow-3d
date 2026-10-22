package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.ManagementActor;

/**
 * Persisted bounded investigation, not part of every online query and never a publication
 * tool.
 */
final class DataQueryInvestigationService implements AutoCloseable {

	interface Advisor {

		String choose(Identity owner, String queryId, String facts);

	}

	interface Source {

		<T> T snapshot(Identity owner, Authorization scope, Function<DataQueryRelationProbe.Reader, T> work);

	}

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private final ObjectMapper json;

	private final Clock clock;

	private final DataQueryMetadataRepository metadata;

	private final DataQueryQueryEvidence vault;

	private final DataQueryRelationStore rules;

	private final Advisor advisor;

	private final Source source;

	private final BiConsumer<ManagementActor, String> access;

	private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(4), daemon("dataquery-investigation"), new ThreadPoolExecutor.AbortPolicy());

	private final ExecutorService modelCalls = new ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS,
			new SynchronousQueue<>(), daemon("dataquery-investigation-model"), new ThreadPoolExecutor.AbortPolicy());

	private final ScheduledExecutorService maintenance = Executors
		.newSingleThreadScheduledExecutor(daemon("dataquery-investigation-retention"));

	private static ThreadFactory daemon(String name) {
		return task -> {
			var thread = new Thread(task, name);
			thread.setDaemon(true);
			return thread;
		};
	}

	DataQueryInvestigationService(DataSource platform, ObjectMapper json, Clock clock,
			DataQueryMetadataRepository metadata, DataQueryQueryEvidence vault, DataQueryRelationStore rules,
			Advisor advisor, Source source, BiConsumer<ManagementActor, String> access) {
		this.jdbc = new JdbcTemplate(platform);
		jdbc.setQueryTimeout(5);
		tx = new TransactionTemplate(new DataSourceTransactionManager(platform));
		tx.setTimeout(5);
		this.json = json;
		this.clock = clock;
		this.metadata = metadata;
		this.vault = vault;
		this.rules = rules;
		this.advisor = advisor;
		this.source = source;
		this.access = access;
	}

	void maintain() {
		// Only this feature's expired evidence/tasks. Never replay interrupted work
		// automatically.
		jdbc.update(
				"UPDATE ai_dataquery_investigation SET status='INTERRUPTED',error_code='INVESTIGATION_INTERRUPTED',updated_at=? WHERE status IN ('QUEUED','RUNNING') AND updated_at<?",
				now(), now().minusMinutes(3));
		vault.purge();
		jdbc.update(
				"UPDATE ai_dataquery_investigation SET evidence_json=? WHERE updated_at<? AND status NOT IN ('QUEUED','RUNNING')",
				"{\"expired\":true}", now().minusDays(30));
	}

	void scheduleMaintenance() {
		maintenance.scheduleWithFixedDelay(() -> {
			try {
				maintain();
			}
			catch (RuntimeException ignored) {
				/* A schema/configuration failure cannot grant source access. */}
		}, 0, 60, TimeUnit.SECONDS);
	}

	Map<String, Object> start(ManagementActor actor, String feedbackId, DataQueryRelation.Definition requested) {
		actor.require("edit", false);
		access.accept(actor, "edit");
		var created = new java.util.concurrent.atomic.AtomicBoolean();
		String id = tx.execute(s -> {
			var feedback = feedback(actor, feedbackId, true);
			Identity owner = owner(actor, feedback);
			if (!Set.of("RELATION_OR_NAME_MAPPING", "BUSINESS_DEFINITION")
				.contains(String.valueOf(feedback.get("category"))))
				throw new DataQueryException("UNSUPPORTED_CORRECTION");
			var scope = metadata.resolve(owner);
			if (!scope.source().equals(metadata.resolve(actor.identity()).source()))
				throw new DataQueryException("DATASET_NOT_AUTHORIZED");
			var inventory = metadata.inventory(owner, scope.source());
			var d = requested == null ? DataQueryRelation.Definition.patrol(scope.source().dataId(), inventory.digest(),
					scope.source().revision()) : requested;
			validateDefinition(d, scope, inventory);
			var prior = jdbc.queryForList(
					"SELECT investigation_id FROM ai_dataquery_investigation WHERE tenant_id=? AND feedback_id=? AND status IN ('QUEUED','RUNNING')",
					String.class, actor.identity().tenantId(), feedbackId);
			if (!prior.isEmpty())
				return prior.get(0);
			String task = UUID.randomUUID().toString();
			jdbc.update(
					"INSERT INTO ai_dataquery_investigation (investigation_id,tenant_id,owner_id,actor_id,feedback_id,query_id,status,evidence_json,proposal_json,created_at,updated_at) VALUES (?,?,?,?,?,?,'QUEUED','{}',?,?,?)",
					task, actor.identity().tenantId(), owner.userId(), actor.identity().userId(), feedbackId,
					feedback.get("query_id"), encode(d), now(), now());
			created.set(true);
			return task;
		});
		try {
			if (created.get())
				worker.submit(() -> run(actor, id));
		}
		catch (RejectedExecutionException full) {
			finish(actor, id, "FAILED", Map.of(), "INVESTIGATION_BUSY");
		}
		return read(actor, id);
	}

	Map<String, Object> read(ManagementActor actor, String id) {
		actor.requireRead();
		access.accept(actor, "read");
		var row = task(actor, id, false);
		Identity owner = new Identity(actor.identity().tenantId(), row.get("owner_id").toString());
		var scope = metadata.resolve(owner);
		if (!scope.source().equals(metadata.resolve(actor.identity()).source()))
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		var out = new LinkedHashMap<String, Object>();
		for (var key : List.of("investigation_id", "feedback_id", "query_id", "status", "error_code", "draft_version",
				"created_at", "updated_at"))
			out.put(key, row.get(key));
		out.put("evidence", decode((String) row.get("evidence_json")));
		out.put("proposedDefinition", decode((String) row.get("proposal_json")));
		out.put("businessConfirmation", "REQUIRED_INDEPENDENT_HUMAN");
		return out;
	}

	List<Map<String, Object>> list(ManagementActor actor, String feedbackId) {
		actor.requireRead();
		access.accept(actor, "read");
		feedback(actor, feedbackId, false);
		return jdbc.queryForList(
				"SELECT investigation_id,status,created_at,draft_version FROM ai_dataquery_investigation WHERE tenant_id=? AND feedback_id=? ORDER BY created_at DESC LIMIT 10",
				actor.identity().tenantId(), feedbackId);
	}

	String prepare(ManagementActor actor, String id) {
		actor.require("edit", false);
		access.accept(actor, "edit");
		return tx.execute(s -> {
			var task = task(actor, id, true);
			if (task.get("draft_version") != null)
				return task.get("draft_version").toString();
			if (!"PROPOSED".equals(task.get("status")))
				throw new DataQueryException("INVESTIGATION_NOT_READY");
			var d = definition(task);
			var payload = new DataQueryRelationStore.Payload(d, id, task.get("query_id").toString(),
					task.get("owner_id").toString(),
					DataQueryMetadataRepository.fingerprint((String) task.get("evidence_json")), "UNCONFIRMED", "",
					actor.identity().userId());
			String version = rules.create(actor, task.get("feedback_id").toString(), payload);
			jdbc.update(
					"UPDATE ai_dataquery_investigation SET draft_version=?,updated_at=? WHERE tenant_id=? AND investigation_id=?",
					version, now(), actor.identity().tenantId(), id);
			return version;
		});
	}

	void run(ManagementActor actor, String id) {
		if (jdbc.update(
				"UPDATE ai_dataquery_investigation SET status='RUNNING',updated_at=? WHERE tenant_id=? AND investigation_id=? AND status='QUEUED'",
				now(), actor.identity().tenantId(), id) != 1)
			return;
		var trace = new LinkedHashMap<String, Object>();
		try {
			var task = task(actor, id, false);
			var d = definition(task);
			Identity owner = new Identity(actor.identity().tenantId(), task.get("owner_id").toString());
			String queryId = task.get("query_id").toString();
			var snapshot = vault.load(owner, queryId);
			var feedback = feedback(actor, task.get("feedback_id").toString(), false);
			var budget = new Budget();
			Runnable check = () -> {
				budget.check();
				access.accept(actor, "edit");
				if (!"RUNNING".equals(task(actor, id, false).get("status")))
					throw new DataQueryException("INVESTIGATION_INTERRUPTED");
				checkSnapshot(actor, owner, queryId, snapshot, d);
			};
			check.run();
			DataQueryRelationProbe.window(snapshot.query(), d);
			var parsed = DataQueryRelation.parse(snapshot.query(), d);
			trace.put("origin",
					Map.of("queryId", queryId, "originalStatus", String.valueOf(feedback.get("query_status")), "mode",
							"CAPTURED_QUERY_REEXECUTION_ON_CURRENT_DATA", "sqlDigest",
							DataQueryMetadataRepository.fingerprint(snapshot.query().sql()), "conditionProvenance",
							snapshot.conditionProvenance(), "originalJoinKey", parsed.originalKey(), "originalShape",
							parsed.shape(), "authorizationRevision", snapshot.authorization().revision(),
							"inventoryDigest", snapshot.inventoryDigest()));
			trace.put("queryFacts", DataQuerySqlDiagnostics.describe(snapshot.query()));
			trace.put("originalGenerationModelRevision", "NOT_CAPTURED_BY_PREVIOUS_AUDIT");
			String problem = encode(Map.of("question", bounded(String.valueOf(feedback.get("original_question")), 600),
					"feedback", bounded(String.valueOf(feedback.get("description")), 600), "candidates",
					List.of(d.parentKey(), d.previousKey())));
			for (int step = 0; step < 6; step++) {
				check.run();
				String facts = problem + "\n服务端证据：" + encode(trace);
				if (facts.length() > 16000)
					throw new DataQueryException("INVESTIGATION_BUDGET_EXCEEDED");
				var future = modelCalls.submit(() -> advisor.choose(owner, queryId, facts));
				String response;
				try {
					response = future.get(Math.min(15, budget.remainingSeconds()), TimeUnit.SECONDS);
				}
				catch (Exception e) {
					future.cancel(true);
					throw new DataQueryException("INVESTIGATION_MODEL_UNAVAILABLE");
				}
				check.run();
				if (response == null || response.length() > 4096)
					throw new DataQueryException("INVESTIGATION_TOOL_REJECTED");
				var action = json.readTree(response);
				if (!action.isObject() || action.size() > 4 || !action.hasNonNull("tool"))
					throw new DataQueryException("INVESTIGATION_TOOL_REJECTED");
				var fields = action.fieldNames();
				while (fields.hasNext())
					if (!Set.of("tool", "evidenceIds", "selectedKey", "reason").contains(fields.next()))
						throw new DataQueryException("INVESTIGATION_TOOL_REJECTED");
				String tool = action.path("tool").asText();
				Object output;
				switch (tool) {
					case "METADATA" -> {
						var inv = metadata.inventory(owner, snapshot.authorization().source());
						var selected = new ArrayList<Object>();
						for (var table : json.readTree(inv.schema()))
							if (Set.of(d.parentTable(), d.childTable()).contains(table.path("table_name").asText())) {
								var item = new LinkedHashMap<String, Object>();
								item.put("table", table.path("table_name").asText());
								var columns = new ArrayList<Object>();
								for (var field : table.path("fields"))
									if (new HashSet<>(List.of(d.parentKey(), d.previousKey(), d.childReference(),
											d.childKey(), d.timeField()))
										.contains(field.path("field_name").asText()))
										columns.add(Map.of("name", field.path("field_name").asText(), "type",
												field.path("field_type").asText(), "untrustedComment",
												bounded(field.path("virtual_comment").asText(), 200)));
								item.put("columns", columns);
								selected.add(item);
							}
						output = selected;
					}
					case "SEMANTICS" -> {
						var current = metadata.optionalCurrent(owner);
						output = Map.of("businessRuleVersion", current == null ? "NONE" : current.version(),
								"publishedRelations",
								metadata
									.relationMatches(owner, snapshot.authorization(),
											metadata.inventory(owner, snapshot.authorization().source()))
									.stream()
									.map(m -> Map.of("version", m.version(), "status", m.status(), "definition",
											m.definition()))
									.toList(),
								"authority", "RESEARCH_AND_METADATA_ARE_NOT_BUSINESS_APPROVAL");
					}
					case "PROBE_CANDIDATES" -> output = source.snapshot(owner, snapshot.authorization(), reader -> {
						DataQueryRelationProbe.Reader limited = q -> {
							check.run();
							budget.query();
							var rows = reader.read(q);
							check.run();
							return rows;
						};
						return List.of(DataQueryRelationProbe.probe(snapshot.query(), d, d.previousKey(), limited),
								DataQueryRelationProbe.probe(snapshot.query(), d, d.parentKey(), limited));
					});
					case "TRIAL_REGRESSION" -> output = verify(owner, snapshot, d, check, budget);
					case "PROPOSE" -> {
						if (!trace.keySet()
							.containsAll(Set.of("METADATA", "SEMANTICS", "PROBE_CANDIDATES", "TRIAL_REGRESSION"))
								|| !d.parentKey().equals(action.path("selectedKey").asText())
								|| !Boolean.TRUE.equals(((Map<?, ?>) trace.get("TRIAL_REGRESSION")).get("passed"))
								|| !Boolean.TRUE
									.equals(((Map<?, ?>) trace.get("TRIAL_REGRESSION")).get("changeObserved")))
							throw new DataQueryException("INVESTIGATION_EVIDENCE_INSUFFICIENT");
						var refs = new HashSet<String>();
						for (var r : action.path("evidenceIds"))
							refs.add(r.asText());
						if (!refs.containsAll(Set.of("PROBE_CANDIDATES", "TRIAL_REGRESSION")))
							throw new DataQueryException("INVESTIGATION_EVIDENCE_INSUFFICIENT");
						trace.put("aiSuggestion",
								Map.of("reason", bounded(action.path("reason").asText(), 800), "evidenceIds", refs,
										"businessAuthority", "UNCONFIRMED", "changes",
										List.of("Captured relation key and parent-entity counting"), "unchanged",
										List.of("Time/company/department/status predicates", "Authorization",
												"Source records and metadata comments")));
						check.run();
						finish(actor, id, "PROPOSED", trace, null);
						return;
					}
					case "NEEDS_EVIDENCE" -> {
						trace.put("aiSuggestion", Map.of("reason", bounded(action.path("reason").asText(), 800)));
						finish(actor, id, "NEEDS_CONFIRMATION", trace, "INVESTIGATION_EVIDENCE_INSUFFICIENT");
						return;
					}
					default -> throw new DataQueryException("INVESTIGATION_TOOL_REJECTED");
				}
				if (trace.containsKey(tool))
					throw new DataQueryException("INVESTIGATION_TOOL_REJECTED");
				trace.put(tool, output);
				trace.put("budget", Map.of("modelCalls", step + 1, "sourceQueries", budget.queries));
				checkpoint(actor, id, trace);
			}
			throw new DataQueryException("INVESTIGATION_BUDGET_EXCEEDED");
		}
		catch (DataQueryException e) {
			if (!e.databaseDiagnostic().isEmpty())
				trace.put("databaseDiagnostic", e.databaseDiagnostic());
			finish(actor, id, "INVESTIGATION_EVIDENCE_UNAVAILABLE".equals(e.getMessage()) ? "NEEDS_EVIDENCE"
					: "INVESTIGATION_EVIDENCE_INSUFFICIENT".equals(e.getMessage()) ? "NEEDS_CONFIRMATION" : "FAILED",
					trace, e.getMessage());
		}
		catch (Exception e) {
			finish(actor, id, "FAILED", trace, "INVESTIGATION_FAILED");
		}
	}

	Map<String, Object> verify(Identity owner, DataQueryQueryEvidence.Snapshot snapshot, DataQueryRelation.Definition d,
			Runnable check) {
		return verifyWith(source, json, owner, snapshot, d, check, new Budget());
	}

	private Map<String, Object> verify(Identity owner, DataQueryQueryEvidence.Snapshot snapshot,
			DataQueryRelation.Definition d, Runnable check, Budget budget) {
		return verifyWith(source, json, owner, snapshot, d, check, budget);
	}

	static Map<String, Object> verifyWith(Source source, ObjectMapper json, Identity owner,
			DataQueryQueryEvidence.Snapshot snapshot, DataQueryRelation.Definition d, Runnable check, Budget budget) {
		return source.snapshot(owner, snapshot.authorization(), reader -> {
			DataQueryRelationProbe.Reader limited = q -> {
				check.run();
				budget.query();
				var rows = reader.read(q);
				check.run();
				return rows;
			};
			var original = limited.read(snapshot.query());
			var applied = DataQueryRelation.apply(snapshot.query(), d);
			if (!Set.of("APPLIED", "CHECKED").contains(applied.status()))
				throw new DataQueryException("RELATION_SHAPE_UNVERIFIED");
			var corrected = limited.read(applied.query());
			var reference = limited.read(DataQueryRelationProbe.referenceQuery(snapshot.query(), d));
			boolean actualMatches = corrected.equals(reference);
			var current = DataQueryRelationProbe.probe(snapshot.query(), d, d.parentKey(), limited);
			var previous = DataQueryRelationProbe.probe(DataQueryRelationProbe.previousMonth(snapshot.query(), d), d,
					d.parentKey(), limited);
			long total = ((Number) ((Map<?, ?>) current.get("parent")).get("parent_rows")).longValue();
			var currentCounts = (Map<?, ?>) current.get("independentJoin");
			var previousCounts = (Map<?, ?>) previous.get("independentJoin");
			boolean cohorts = ((Number) currentCounts.get("with_items")).longValue() > 0
					&& ((Number) currentCounts.get("without_items")).longValue() > 0
					&& ((Number) previousCounts.get("with_items")).longValue() > 0
					&& ((Number) previousCounts.get("without_items")).longValue() > 0
					&& ((Number) currentCounts.get("matched_child_rows"))
						.longValue() > ((Number) currentCounts.get("with_items")).longValue();
			boolean passed = actualMatches && cohorts && Boolean.TRUE.equals(current.get("agrees"))
					&& Boolean.TRUE.equals(previous.get("agrees")) && total > 0;
			return Map.of("passed", passed, "level", "EXECUTED_SQL_AND_INDEPENDENT_JOIN_NOT_BUSINESS_APPROVAL",
					"currentMonth", current, "previousMonth", previous, "before",
					Map.of("rowCount", original.values().size(), "resultDigest", rowsDigest(json, original)), "after",
					Map.of("rowCount", corrected.values().size(), "resultDigest", rowsDigest(json, corrected)),
					"actualIndependentComparison",
					Map.of("matches", actualMatches, "referenceDigest", rowsDigest(json, reference),
							"positiveNegativeAndMultiplicityCohorts", cohorts),
					"unchangedPredicates", true, "changeObserved",
					!rowsDigest(json, original).equals(rowsDigest(json, corrected)), "unverified",
					List.of("Business confirmation required",
							"Different wording/real-model generation is a separate acceptance step",
							"Empty month or missing positive/negative cohorts requires additional real acceptance"));
		});
	}

	private void checkSnapshot(ManagementActor actor, Identity owner, String queryId,
			DataQueryQueryEvidence.Snapshot snapshot, DataQueryRelation.Definition d) {
		if (!snapshot.equals(vault.load(owner, queryId)) || !snapshot.authorization().equals(metadata.resolve(owner)))
			throw new DataQueryException("AUTHORIZATION_CHANGED");
		var inv = metadata.inventory(owner, snapshot.authorization().source());
		validateDefinition(d, snapshot.authorization(), inv);
		if (!inv.digest().equals(snapshot.inventoryDigest()))
			throw new DataQueryException("DATASET_METADATA_CHANGED");
		if (!snapshot.relationVersions().equals(metadata.relationMatches(owner, snapshot.authorization(), inv)))
			throw new DataQueryException("RULE_CHANGED");
		if (!snapshot.authorization().source().equals(metadata.resolve(actor.identity()).source()))
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		var current = metadata.optionalCurrent(owner);
		if (!Objects.equals(snapshot.ruleVersion(), current == null ? null : current.version()))
			throw new DataQueryException("RULE_CHANGED");
	}

	void validateDefinition(DataQueryRelation.Definition d, Authorization scope,
			DataQueryMetadataRepository.DatasetInventory inv) {
		d.validate();
		if (d.dataId() != scope.source().dataId() || !d.bindingRevision().equals(scope.source().revision())
				|| !d.inventoryDigest().equals(inv.digest())
				|| !inv.tables().containsAll(Set.of(d.parentTable(), d.childTable())))
			throw new DataQueryException("RELATION_DEFINITION_INVALID");
		try {
			var required = Map.of(d.parentTable(), Set.of(d.parentKey(), d.previousKey(), d.timeField()),
					d.childTable(), Set.of(d.childKey(), d.childReference()));
			for (var entry : required.entrySet()) {
				var found = new HashSet<String>();
				for (var t : json.readTree(inv.schema()))
					if (entry.getKey().equals(t.path("table_name").asText()))
						for (var f : t.path("fields"))
							found.add(f.path("field_name").asText());
				if (!found.containsAll(entry.getValue()))
					throw new IllegalArgumentException();
			}
		}
		catch (Exception e) {
			throw new DataQueryException("RELATION_DEFINITION_INVALID");
		}
	}

	private Map<String, Object> feedback(ManagementActor actor, String id, boolean lock) {
		var rows = jdbc.queryForList(
				"SELECT f.*,q.original_question,q.status AS query_status FROM ai_dataquery_feedback f JOIN ai_dataquery_query q ON q.query_id=f.query_id AND q.tenant_id=f.tenant_id AND q.user_id=f.user_id WHERE f.tenant_id=? AND f.feedback_id=?"
						+ (lock ? " FOR UPDATE" : ""),
				actor.identity().tenantId(), id);
		if (rows.size() != 1)
			throw new DataQueryException("FEEDBACK_NOT_FOUND");
		return rows.get(0);
	}

	private Map<String, Object> task(ManagementActor actor, String id, boolean lock) {
		var rows = jdbc.queryForList("SELECT * FROM ai_dataquery_investigation WHERE tenant_id=? AND investigation_id=?"
				+ (lock ? " FOR UPDATE" : ""), actor.identity().tenantId(), id);
		if (rows.size() != 1)
			throw new DataQueryException("INVESTIGATION_NOT_FOUND");
		return rows.get(0);
	}

	private Identity owner(ManagementActor actor, Map<String, Object> feedback) {
		return new Identity(actor.identity().tenantId(), feedback.get("user_id").toString());
	}

	private DataQueryRelation.Definition definition(Map<String, Object> task) {
		try {
			return json.readValue((String) task.get("proposal_json"), DataQueryRelation.Definition.class);
		}
		catch (Exception e) {
			throw new DataQueryException("RELATION_DEFINITION_INVALID");
		}
	}

	private void checkpoint(ManagementActor actor, String id, Map<String, Object> trace) {
		String encoded = encode(trace);
		if (encoded.length() > 24000)
			throw new DataQueryException("INVESTIGATION_BUDGET_EXCEEDED");
		jdbc.update(
				"UPDATE ai_dataquery_investigation SET evidence_json=?,updated_at=? WHERE tenant_id=? AND investigation_id=? AND status='RUNNING'",
				encoded, now(), actor.identity().tenantId(), id);
	}

	private void finish(ManagementActor actor, String id, String state, Map<String, Object> trace, String code) {
		String output = encode(trace);
		if (output.length() > 24000) {
			output = "{\"evidenceTruncated\":true}";
			state = "FAILED";
			code = "INVESTIGATION_BUDGET_EXCEEDED";
		}
		jdbc.update(
				"UPDATE ai_dataquery_investigation SET status=?,evidence_json=?,error_code=?,updated_at=? WHERE tenant_id=? AND investigation_id=? AND status IN ('QUEUED','RUNNING')",
				state, output, code, now(), actor.identity().tenantId(), id);
	}

	private static String rowsDigest(ObjectMapper json, DataQuerySqlExecutor.Rows rows) {
		try {
			return DataQueryMetadataRepository.fingerprint(json.writeValueAsString(rows));
		}
		catch (Exception e) {
			throw new DataQueryException("INVESTIGATION_FAILED");
		}
	}

	private String encode(Object v) {
		try {
			return json.writeValueAsString(v);
		}
		catch (Exception e) {
			throw new DataQueryException("INVALID_REQUEST");
		}
	}

	private Object decode(String text) {
		try {
			return json.readValue(text, Object.class);
		}
		catch (Exception e) {
			throw new DataQueryException("INVESTIGATION_FAILED");
		}
	}

	private static String bounded(String value, int max) {
		return value.length() > max ? value.substring(0, max) : value;
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	@Override
	public void close() {
		worker.shutdownNow();
		modelCalls.shutdownNow();
		maintenance.shutdownNow();
	}

	static final class Budget {

		final long started = System.nanoTime();

		int queries;

		void check() {
			if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(120) || Thread.currentThread().isInterrupted())
				throw new DataQueryException("INVESTIGATION_BUDGET_EXCEEDED");
		}

		int remainingSeconds() {
			check();
			return Math.max(1, 120 - (int) TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));
		}

		void query() {
			check();
			if (++queries > 16)
				throw new DataQueryException("INVESTIGATION_BUDGET_EXCEEDED");
		}

	}

}
