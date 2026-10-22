package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.ManagementActor;

/**
 * Existing human-reviewed lifecycle: intent aliases and one bounded department-grain
 * correction.
 */
public final class DataQueryCorrectionService {

	public enum Category {

		INTENT_UNDERSTANDING, BUSINESS_DEFINITION, RELATION_OR_NAME_MAPPING, SOURCE_DATA_MISSING, SQL_TECHNICAL_ERROR

	}

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private final DataQueryMetadataRepository metadata;

	private final ObjectMapper json;

	private final Clock clock;

	private final PatrolQuestionInterpreter interpreter = new PatrolQuestionInterpreter();

	@FunctionalInterface
	interface DepartmentTrial {

		DataQueryDepartmentVerifier.Trial verify(Identity who, Authorization scope,
				DataQueryDepartmentDimension.Captured original);

	}

	private final DepartmentTrial departmentTrial;

	public DataQueryCorrectionService(DataSource source, DataQueryMetadataRepository metadata, ObjectMapper json,
			Clock clock) {
		this(source, metadata, json, clock, new DataQueryDepartmentVerifier(metadata)::verify);
	}

	DataQueryCorrectionService(DataSource source, DataQueryMetadataRepository metadata, ObjectMapper json, Clock clock,
			DepartmentTrial departmentTrial) {
		this.departmentTrial = departmentTrial;
		this.jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(5);
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(source));
		tx.setTimeout(5);
		this.metadata = metadata;
		this.json = json;
		this.clock = clock;
	}

	public String feedback(Identity identity, String queryId, Category category, String description, Long chatId) {
		return feedback(identity, queryId, category, description, chatId, null);
	}

	public String feedback(Identity identity, String queryId, Category category, String description, Long chatId,
			String requestId) {
		var query = metadata.findOwnedQuery(identity, queryId)
			.orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
		if (query.originalQuestion() == null || (chatId != null && !chatId.equals(query.chatRecordId())))
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		if (category == null)
			throw new DataQueryException("INVALID_REQUEST");
		text(description, 2000);
		String id = requestId == null ? UUID.randomUUID().toString() : retryFeedbackId(identity, requestId);
		try {
			jdbc.update("INSERT INTO ai_dataquery_feedback VALUES (?,?,?,?,?,?,?)", id, identity.tenantId(),
					identity.userId(), queryId, category.name(), description, now());
		}
		catch (org.springframework.dao.DuplicateKeyException duplicate) {
			var prior = jdbc.queryForList(
					"SELECT query_id,category,description FROM ai_dataquery_feedback WHERE feedback_id=? AND tenant_id=? AND user_id=?",
					id, identity.tenantId(), identity.userId());
			if (requestId == null || prior.size() != 1 || !queryId.equals(prior.get(0).get("query_id"))
					|| !category.name().equals(prior.get(0).get("category"))
					|| !description.equals(prior.get(0).get("description")))
				throw new DataQueryException("IDEMPOTENCY_CONFLICT");
		}
		return id;
	}

	private String retryFeedbackId(Identity identity, String requestId) {
		try {
			var uuid = UUID.fromString(requestId);
			if (!uuid.toString().equalsIgnoreCase(requestId))
				throw new IllegalArgumentException();
			String hash = DataQueryMetadataRepository.fingerprint("dataquery-feedback-v1", identity.tenantId(),
					identity.userId(), uuid.toString());
			return new UUID(Long.parseUnsignedLong(hash.substring(0, 16), 16),
					Long.parseUnsignedLong(hash.substring(16, 32), 16))
				.toString();
		}
		catch (IllegalArgumentException e) {
			throw new DataQueryException("INVALID_REQUEST");
		}
	}

	public Map<Long, DataQueryMetadataRepository.HistoryReference> history(Identity identity, String username,
			List<Long> messageIds) {
		return metadata.findOwnedChatReferences(identity, username, messageIds);
	}

	/**
	 * Bootstrap the same version lifecycle, never insert an already-approved/published
	 * rule.
	 */
	public String initialDraft(ManagementActor actor, String dataId, String definitionDigest, boolean acknowledged,
			String month, String reason) {
		actor.require("edit", true);
		month(month);
		text(reason, 500);
		if (!acknowledged || !DataQueryMetadataRepository.DEFINITION_DIGEST.equals(definitionDigest))
			fail("INVALID_REQUEST");
		return tx.execute(status -> {
			var binding = metadata.binding(actor.identity());
			if (!Long.toString(binding.dataId()).equals(dataId))
				fail("DATASET_NOT_AUTHORIZED");
			if (hasActive(actor))
				fail("ACTIVE_RULE_CHANGED");
			String version = "dq-" + UUID.randomUUID();
			var terms = List.of("巡视");
			jdbc.update(
					"INSERT INTO ai_dataquery_rule_version (tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,intent_terms_json,content_digest,edit_revision,expected_month,created_by,created_at) VALUES (?,'top.patrol',?,?,?,'DRAFT',?,?,1,?,?,?)",
					actor.identity().tenantId(), version, DEFINITION, DataQueryMetadataRepository.DEFINITION_DIGEST,
					encode(terms), DataQueryMetadataRepository.ruleContentDigest(terms), month,
					actor.identity().userId(), now());
			jdbc.update(
					"INSERT INTO ai_dataquery_config_event (event_id,tenant_id,dataset_key,data_id,actor_id,action,before_json,after_json,reason,created_at) VALUES (?,?,'top.patrol',?,?,'INITIAL_RULE','null',?,?,?)",
					UUID.randomUUID().toString(), actor.identity().tenantId(), binding.dataId(),
					actor.identity().userId(),
					"{\"ruleVersion\":\"" + version + "\",\"definitionDigest\":\"" + definitionDigest + "\"}", reason,
					now());
			return version;
		});
	}

	private boolean hasActive(ManagementActor actor) {
		return !jdbc
			.queryForList(
					"SELECT rule_version FROM ai_dataquery_rule_active WHERE tenant_id=? AND dataset_key='top.patrol'",
					String.class, actor.identity().tenantId())
			.isEmpty();
	}

	public String draft(ManagementActor actor, String feedbackId, List<String> terms, String month) {
		return draft(actor, feedbackId, terms, month, null);
	}

	public String draft(ManagementActor actor, String feedbackId, List<String> terms, String month,
			String correctionKind) {
		if (DataQueryDepartmentDimension.KIND.equals(correctionKind))
			return departmentDraft(actor, feedbackId, terms, month);
		if (correctionKind != null && !"INTENT_TERMS".equals(correctionKind))
			fail("UNSUPPORTED_CORRECTION");
		actor.require("edit", false);
		var normalized = PatrolQuestionInterpreter.normalizedTerms(terms);
		month(month);
		return tx.execute(status -> {
			var feedback = feedbackRow(actor, feedbackId);
			if (!Category.INTENT_UNDERSTANDING.name().equals(feedback.get("category")))
				throw new DataQueryException("UNSUPPORTED_CORRECTION");
			var base = metadata.current(actor.identity());
			if (base != null && base.department() != null)
				fail("UNSUPPORTED_CORRECTION");
			if (base == null || !base.version().equals(feedback.get("rule_version")))
				throw new DataQueryException("ACTIVE_RULE_CHANGED");
			String version = "dq-" + UUID.randomUUID();
			jdbc.update(
					"""
							INSERT INTO ai_dataquery_rule_version
							(tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,intent_terms_json,content_digest,feedback_id,base_version,edit_revision,expected_month,created_by,created_at)
							VALUES (?,'top.patrol',?,?,?,'DRAFT',?,?,?,?,1,?,?,?)
							""",
					actor.identity().tenantId(), version, DEFINITION, DataQueryMetadataRepository.DEFINITION_DIGEST,
					encode(normalized), DataQueryMetadataRepository.ruleContentDigest(normalized), feedbackId,
					base.version(), month, actor.identity().userId(), now());
			return version;
		});
	}

	public void edit(ManagementActor actor, String version, int revision, List<String> terms, String month) {
		edit(actor, version, revision, terms, month, null);
	}

	public void edit(ManagementActor actor, String version, int revision, List<String> terms, String month,
			String correctionKind) {
		actor.require("edit", false);
		var normalized = PatrolQuestionInterpreter.normalizedTerms(terms);
		month(month);
		tx.executeWithoutResult(status -> {
			var row = locked(actor, version, revision);
			String department = (String) row.get("department_payload_json");
			if (department != null) {
				if (!DataQueryDepartmentDimension.KIND.equals(correctionKind))
					fail("UNSUPPORTED_CORRECTION");
				department = departmentPayload(actor, feedbackRow(actor, (String) row.get("feedback_id")), month);
			}
			else if (correctionKind != null && !"INTENT_TERMS".equals(correctionKind))
				fail("UNSUPPORTED_CORRECTION");
			var effectiveTerms = department == null ? normalized : terms(row);
			if ("PUBLISHED".equals(row.get("state")))
				fail("STATE_CONFLICT");
			jdbc.update(
					"""
							UPDATE ai_dataquery_rule_version SET state='DRAFT',intent_terms_json=?,department_payload_json=?,content_digest=?,expected_month=?,edit_revision=edit_revision+1,
							created_by=?,regression_report=NULL,regression_digest=NULL,validated_revision=NULL,validated_at=NULL,approved_revision=NULL,approved_by=NULL,approved_at=NULL
							WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=?
							""",
					encode(effectiveTerms), department,
					DataQueryMetadataRepository.ruleContentDigest(effectiveTerms, department), month,
					actor.identity().userId(), actor.identity().tenantId(), version);
		});
	}

	public String verify(ManagementActor actor, String version, int revision) {
		if (version != null && version.startsWith("rel-"))
			return relationStore().verify(actor, version, revision);
		actor.require("verify", false);
		var snapshot = tx.execute(status -> {
			Map<String, Object> copy = new LinkedHashMap<>();
			locked(actor, version, revision).forEach((key, value) -> copy.put(key.toLowerCase(Locale.ROOT), value));
			return copy;
		});
		if (snapshot.get("department_payload_json") != null)
			return verifyDepartment(actor, version, revision, snapshot);
		return tx.execute(status -> {
			var row = locked(actor, version, revision);
			if ("PUBLISHED".equals(row.get("state")))
				fail("STATE_CONFLICT");
			boolean initial = row.get("feedback_id") == null && row.get("base_version") == null;
			var base = metadata.current(actor.identity());
			if (initial ? hasActive(actor) : base == null || !base.version().equals(row.get("base_version")))
				fail("ACTIVE_RULE_CHANGED");
			var terms = terms(row);
			var expected = month((String) row.get("expected_month"));
			String question = initial ? expected.getYear() + "年" + expected.getMonthValue() + "月各部门巡视记录数"
					: (String) feedbackRow(actor, (String) row.get("feedback_id")).get("original_question");
			boolean beforeFails = initial;
			if (!initial) {
				try {
					interpreter.interpret(question, base.intentTerms());
				}
				catch (DataQueryException e) {
					beforeFails = true;
				}
			}
			boolean pass = beforeFails;
			try {
				pass &= interpreter.interpret(question, terms).from().equals(expected.atDay(1));
				pass &= interpreter.interpret(expected.getYear() + "年" + expected.getMonthValue() + "月各部门巡视记录数", terms)
					.from()
					.equals(expected.atDay(1));
				for (String bad : List.of("各部门巡视记录数", "2026年8月各部门巡视完成率是多少", "2026年8月和9月各部门巡视记录数",
						"SELECT * FROM device_task_record")) {
					try {
						interpreter.interpret(bad, terms);
						pass = false;
					}
					catch (DataQueryException expectedRejection) {
					}
				}
			}
			catch (DataQueryException e) {
				pass = false;
			}
			String report = pass ? (initial
					? "PASS: initial TEST definition explicitly acknowledged; expected month; baseline and negative intent checks; NOT source-data or authorized-aggregate validation"
					: "PASS: intent-only; before rejected; after expected month; baseline preserved; unsupported metrics/date/SQL rejected; NOT source-data validation")
					: "FAIL: intent regression";
			jdbc.update(
					"""
							UPDATE ai_dataquery_rule_version SET state=?,regression_report=?,regression_digest=?,validated_revision=?,validated_at=?,approved_revision=NULL,approved_by=NULL,approved_at=NULL
							WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=?
							""",
					pass ? "VERIFIED" : "DRAFT", report, pass ? evidence(row, report) : null, pass ? revision : null,
					pass ? now() : null, actor.identity().tenantId(), version);
			return report;
		});
	}

	private String departmentDraft(ManagementActor actor, String feedbackId, List<String> terms, String month) {
		actor.require("edit", false);
		month(month);
		return tx.execute(status -> {
			var feedback = feedbackRow(actor, feedbackId);
			var base = metadata.current(actor.identity());
			if (base == null || !base.version().equals(feedback.get("rule_version")))
				fail("ACTIVE_RULE_CHANGED");
			var normalized = base.intentTerms();
			String payload = departmentPayload(actor, feedback, month), version = "dq-" + UUID.randomUUID();
			jdbc.update(
					"""
							INSERT INTO ai_dataquery_rule_version
							(tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,intent_terms_json,department_payload_json,content_digest,feedback_id,base_version,edit_revision,expected_month,created_by,created_at)
							VALUES (?,'top.patrol',?,?,?,'DRAFT',?,?,?,?,?,1,?,?,?)
							""",
					actor.identity().tenantId(), version, DEFINITION, DataQueryMetadataRepository.DEFINITION_DIGEST,
					encode(normalized), payload, DataQueryMetadataRepository.ruleContentDigest(normalized, payload),
					feedbackId, base.version(), month, actor.identity().userId(), now());
			return version;
		});
	}

	private DataQueryDepartmentDimension.Captured departmentSource(Map<String, Object> feedback) {
		if (!Set.of("BUSINESS_DEFINITION", "RELATION_OR_NAME_MAPPING").contains(feedback.get("category")))
			fail("UNSUPPORTED_CORRECTION");
		try {
			var candidate = json.readTree((String) feedback.get("candidate_json"));
			if (!"AI_SQL".equals(candidate.path("kind").asText()) || !candidate.path("department").has("captured"))
				fail("DEPARTMENT_SOURCE_REQUIRED");
			return json.convertValue(candidate.path("department").path("captured"),
					DataQueryDepartmentDimension.Captured.class);
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (Exception e) {
			throw new DataQueryException("DEPARTMENT_SOURCE_REQUIRED");
		}
	}

	private String departmentPayload(ManagementActor actor, Map<String, Object> feedback, String expectedMonth) {
		var captured = departmentSource(feedback);
		if (!captured.scope().from().equals(month(expectedMonth).atDay(1).toString()))
			fail("DEPARTMENT_SOURCE_REQUIRED");
		var scope = metadata.resolve(actor.identity());
		var inventory = metadata.inventory(actor.identity(), scope.source());
		try {
			var candidate = json.readTree((String) feedback.get("candidate_json"));
			if (!Long.toString(scope.source().dataId()).equals(candidate.path("dataId").asText())
					|| !inventory.digest().equals(candidate.path("inventoryDigest").asText()))
				fail("DEPARTMENT_RULE_STALE");
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new DataQueryException("DEPARTMENT_SOURCE_REQUIRED");
		}
		return encode(new DataQueryDepartmentDimension.Definition(DataQueryDepartmentDimension.KIND,
				List.of("company_id", "patrol_dept_id"), "RECORD_TIME_NOT_AUTHORITY", "KEEP_COUNT_NULL_NAME",
				"KEEP_COUNT_AND_WARN", scope.source().dataId(), inventory.digest(), scope.source().revision()));
	}

	private String verifyDepartment(ManagementActor actor, String version, int revision, Map<String, Object> snapshot) {
		if ("PUBLISHED".equals(snapshot.get("state")))
			fail("STATE_CONFLICT");
		var definition = DataQueryMetadataRepository.readDepartment((String) snapshot.get("department_payload_json"));
		metadata.checkDepartmentBinding(actor.identity(), definition);
		var base = metadata.current(actor.identity());
		if (base == null || !base.version().equals(snapshot.get("base_version")))
			fail("ACTIVE_RULE_CHANGED");
		var original = departmentSource(feedbackRow(actor, (String) snapshot.get("feedback_id")));
		var scope = metadata.resolve(actor.identity());
		// No platform transaction or FOR UPDATE lock is held during datasource trial
		// queries.
		var trial = departmentTrial.verify(actor.identity(), scope, original);
		if (!scope.equals(metadata.resolve(actor.identity())))
			fail("AUTHORIZATION_CHANGED");
		metadata.checkDepartmentBinding(actor.identity(), definition);
		String report = encode(Map.of("kind", DataQueryDepartmentDimension.KIND, "trial", trial, "verifiedUserId",
				actor.identity().userId(), "scope", original.scope(), "authorizationRevision", scope.revision(),
				"verifiedAt", clock.instant().toString(), "verificationLevel",
				"ACTUAL_READONLY_TRIAL_AND_INDEPENDENT_AGGREGATION", "notVerified",
				List.of("authoritative names", "device-detail joins", "similar wording with real model",
						"post-publication current-data reuse")));
		return tx.execute(status -> {
			var row = locked(actor, version, revision);
			var current = metadata.current(actor.identity());
			if (!Objects.equals(snapshot.get("state"), row.get("state"))
					|| !Objects.equals(snapshot.get("content_digest"), row.get("content_digest")))
				fail("REVISION_CONFLICT");
			if (current == null || !current.version().equals(row.get("base_version")))
				fail("ACTIVE_RULE_CHANGED");
			metadata.checkDepartmentBinding(actor.identity(), definition);
			jdbc.update(
					"""
							UPDATE ai_dataquery_rule_version SET state=?,regression_report=?,regression_digest=?,validated_revision=?,validated_at=?,approved_revision=NULL,approved_by=NULL,approved_at=NULL
							WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=?
							""",
					trial.passed() ? "VERIFIED" : "DRAFT", report, trial.passed() ? evidence(row, report) : null,
					trial.passed() ? revision : null, trial.passed() ? now() : null, actor.identity().tenantId(),
					version);
			return report;
		});
	}

	private void checkedDepartmentEvidence(ManagementActor actor, Map<String, Object> row) {
		if (row.get("department_payload_json") == null)
			return;
		metadata.checkDepartmentBinding(actor.identity(),
				DataQueryMetadataRepository.readDepartment((String) row.get("department_payload_json")));
		metadata.resolve(actor.identity());
		try {
			var report = json.readTree((String) row.get("regression_report"));
			if (!DataQueryDepartmentDimension.KIND.equals(report.path("kind").asText())
					|| !report.path("trial").path("passed").asBoolean()
					|| !Instant.parse(report.path("verifiedAt").asText())
						.plus(Duration.ofMinutes(30))
						.isAfter(clock.instant()))
				fail("DEPARTMENT_VERIFICATION_STALE");
			var verifier = new Identity(actor.identity().tenantId(), report.path("verifiedUserId").asText());
			if (!metadata.resolve(verifier).revision().equals(report.path("authorizationRevision").asText()))
				fail("AUTHORIZATION_CHANGED");
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (Exception e) {
			throw new DataQueryException("DEPARTMENT_VERIFICATION_STALE");
		}
	}

	public void approve(ManagementActor actor, String version, int revision) {
		if (version != null && version.startsWith("rel-")) {
			relationStore().approve(actor, version, revision);
			return;
		}
		actor.require("review", true);
		tx.executeWithoutResult(status -> {
			var row = locked(actor, version, revision);
			independent(actor, row);
			if (!"VERIFIED".equals(row.get("state")))
				fail("STATE_CONFLICT");
			checkedEvidence(row, revision);
			checkedDepartmentEvidence(actor, row);
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET state='APPROVED',approved_revision=?,approved_by=?,approved_at=? WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=?",
					revision, actor.identity().userId(), now(), actor.identity().tenantId(), version);
			if (actor.identity().userId().equals(String.valueOf(row.get("created_by")))) {
				var binding = metadata.binding(actor.identity());
				jdbc.update(
						"INSERT INTO ai_dataquery_config_event (event_id,tenant_id,dataset_key,data_id,actor_id,action,before_json,after_json,reason,created_at) VALUES (?,?,'top.patrol',?,?,'TEST_SELF_REVIEW','null',?,?,?)",
						UUID.randomUUID().toString(), actor.identity().tenantId(), binding.dataId(),
						actor.identity().userId(),
						encode(Map.of("ruleVersion", version, "revision", revision, "mode",
								"TEST_SINGLE_ACCOUNT_HUMAN")),
						"Explicit test tenant:user exception; human approval required", now());
			}
		});
	}

	public void publish(ManagementActor actor, String version, int revision, String reason) {
		if (version != null && version.startsWith("rel-")) {
			relationStore().publish(actor, version, revision, reason);
			return;
		}
		actor.require("publish", true);
		text(reason, 500);
		tx.executeWithoutResult(status -> {
			var row = locked(actor, version, revision);
			independent(actor, row);
			if (!"APPROVED".equals(row.get("state")) || !Integer.valueOf(revision).equals(row.get("approved_revision")))
				fail("STATE_CONFLICT");
			checkedEvidence(row, revision);
			checkedDepartmentEvidence(actor, row);
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET state='PUBLISHED',published_by=?,published_at=? WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=?",
					actor.identity().userId(), now(), actor.identity().tenantId(), version);
			switchActive(actor, (String) row.get("base_version"), version, "PUBLISH", reason);
		});
	}

	public void rollback(ManagementActor actor, String expectedActive, String target, String reason) {
		if (expectedActive != null && expectedActive.startsWith("rel-")) {
			relationStore().rollback(actor, expectedActive, target, reason);
			return;
		}
		actor.require("publish", true);
		text(reason, 500);
		tx.executeWithoutResult(status -> {
			var row = locked(actor, target, null);
			if (!"PUBLISHED".equals(row.get("state")) || row.get("approved_by") == null
					|| row.get("regression_digest") == null || row.get("published_at") == null)
				fail("STATE_CONFLICT");
			if (row.get("department_payload_json") != null)
				metadata.checkDepartmentBinding(actor.identity(),
						DataQueryMetadataRepository.readDepartment((String) row.get("department_payload_json")));
			switchActive(actor, expectedActive, target, "ROLLBACK", reason);
			if (metadata.current(actor.identity()) == null)
				fail("METRIC_DEFINITION_UNCONFIRMED");
		});
	}

	public Map<String, Object> inspect(ManagementActor actor, String version) {
		if (version != null && version.startsWith("rel-"))
			return relationStore().inspect(actor, version);
		actor.requireRead();
		return tx.execute(s -> {
			var row = locked(actor, version, null);
			if (row.get("feedback_id") != null)
				row.put("source_feedback", transportRow(feedbackRow(actor, (String) row.get("feedback_id"))));
			return transportRow(row);
		});
	}

	public Map<String, Object> technicalExperiences(ManagementActor actor) {
		return metadata.technicalExperiences().list(actor);
	}

	public void disableTechnicalExperience(ManagementActor actor, String version, String reason) {
		metadata.technicalExperiences().disable(actor, version, reason);
	}

	private DataQueryRelationStore relationStore() {
		if (metadata.relations() == null)
			throw new DataQueryException("INVESTIGATION_DISABLED");
		return metadata.relations();
	}

	public record Workbench(String dataset, String actorId, Set<String> permissions, String activeVersion,
			List<Map<String, Object>> feedback, List<Map<String, Object>> versions, boolean testSelfReviewAllowed) {
	}

	/**
	 * Bounded, tenant-scoped management view; no TOP rows, credentials or inferred
	 * grants.
	 */
	public Workbench workbench(ManagementActor actor) {
		actor.requireRead();
		return tx.execute(status -> {
			String tenant = actor.identity().tenantId();
			var active = jdbc.queryForList(
					"SELECT rule_version FROM ai_dataquery_rule_active WHERE tenant_id=? AND dataset_key='top.patrol'",
					String.class, tenant);
			var feedback = jdbc.queryForList("""
					SELECT f.feedback_id,f.query_id,f.category,f.description,f.created_at,
					       q.original_question,q.rule_version,q.status AS query_status,q.candidate_json
					FROM ai_dataquery_feedback f JOIN ai_dataquery_query q
					  ON q.query_id=f.query_id AND q.tenant_id=f.tenant_id AND q.user_id=f.user_id
					WHERE f.tenant_id=? AND q.dataset_key='top.patrol'
					ORDER BY f.created_at DESC,f.feedback_id DESC LIMIT 50
					""", tenant);
			var versions = jdbc.queryForList("""
					SELECT rule_version,state,edit_revision,base_version,feedback_id,created_by,created_at,
					       approved_by,published_by,published_at
					FROM ai_dataquery_rule_version WHERE tenant_id=? AND dataset_key='top.patrol'
					ORDER BY created_at DESC,rule_version DESC LIMIT 50
					""", tenant);
			if (metadata.relations() != null)
				versions.addAll(metadata.relations().versions(actor));
			var permissions = actor.permissions()
				.stream()
				.filter(p -> p.startsWith("knowledge_dataquery_"))
				.collect(java.util.stream.Collectors.toSet());
			return new Workbench("top.patrol", actor.identity().userId(), permissions,
					active.isEmpty() ? null : active.get(0), feedback.stream().map(this::transportRow).toList(),
					versions.stream().map(this::transportRow).toList(),
					metadata.testSelfReviewAllowed(actor.identity()));
		});
	}

	private Map<String, Object> transportRow(Map<String, Object> row) {
		Map<String, Object> result = new LinkedHashMap<>();
		row.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), value));
		Object candidate = result.remove("candidate_json");
		if (candidate instanceof String text) {
			try {
				var node = json.readTree(text);
				result.put("technical_experience_version", node.path("repair").path("experienceVersion").asText(null));
				boolean eligible = Set.of("BUSINESS_DEFINITION", "RELATION_OR_NAME_MAPPING")
					.contains(result.get("category")) && node.path("department").has("captured");
				result.put("department_source_available", eligible);
				String from = node.path("department").path("captured").path("scope").path("from").asText();
				if (eligible && from.matches("[0-9]{4}-[0-9]{2}-01"))
					result.put("department_month", from.substring(0, 7));
			}
			catch (Exception invalid) {
				throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
		}

		return result;
	}

	private void switchActive(ManagementActor actor, String from, String to, String action, String reason) {
		if (Objects.equals(from, to))
			fail("STATE_CONFLICT");
		if (from == null) {
			try {
				jdbc.update(
						"INSERT INTO ai_dataquery_rule_active (tenant_id,dataset_key,rule_version) VALUES (?,'top.patrol',?)",
						actor.identity().tenantId(), to);
			}
			catch (org.springframework.dao.DuplicateKeyException ex) {
				fail("ACTIVE_RULE_CHANGED");
			}
		}
		else {
			int changed = jdbc.update(
					"UPDATE ai_dataquery_rule_active SET rule_version=? WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=?",
					to, actor.identity().tenantId(), from);
			if (changed != 1)
				fail("ACTIVE_RULE_CHANGED");
		}
		jdbc.update("INSERT INTO ai_dataquery_release_event VALUES (?,?,'top.patrol',?,?,?,?,?,?)",
				UUID.randomUUID().toString(), actor.identity().tenantId(), actor.identity().userId(), action,
				from == null ? "" : from, to, reason, now());
	}

	private Map<String, Object> locked(ManagementActor actor, String version, Integer revision) {
		var rows = jdbc.queryForList(
				"SELECT * FROM ai_dataquery_rule_version WHERE tenant_id=? AND dataset_key='top.patrol' AND rule_version=? FOR UPDATE",
				actor.identity().tenantId(), version);
		if (rows.size() != 1)
			throw new DataQueryException("DRAFT_NOT_FOUND");
		var row = rows.get(0);
		if (revision != null && !revision.equals(row.get("edit_revision")))
			fail("REVISION_CONFLICT");
		return row;
	}

	private Map<String, Object> feedbackRow(ManagementActor actor, String id) {
		var rows = jdbc.queryForList(
				"""
						SELECT f.feedback_id,f.query_id,f.category,f.description,f.created_at,
						q.original_question,q.rule_version,q.status AS query_status,q.candidate_json FROM ai_dataquery_feedback f
						JOIN ai_dataquery_query q ON q.query_id=f.query_id AND q.tenant_id=f.tenant_id AND q.user_id=f.user_id
						WHERE f.tenant_id=? AND f.feedback_id=? AND q.dataset_key='top.patrol'
						""",
				actor.identity().tenantId(), id);
		if (rows.size() != 1)
			throw new DataQueryException("FEEDBACK_NOT_FOUND");
		return rows.get(0);
	}

	private void checkedEvidence(Map<String, Object> row, int revision) {
		if (!Integer.valueOf(revision).equals(row.get("validated_revision")) || row.get("regression_report") == null
				|| !evidence(row, (String) row.get("regression_report")).equals(row.get("regression_digest")))
			fail("STATE_CONFLICT");
	}

	private String evidence(Map<String, Object> row, String report) {
		return digest(row.get("content_digest") + "|" + row.get("edit_revision") + "|" + row.get("expected_month") + "|"
				+ row.get("feedback_id") + "|" + row.get("base_version") + "|" + report);
	}

	private List<String> terms(Map<String, Object> row) {
		try {
			return PatrolQuestionInterpreter.normalizedTerms(json.readValue((String) row.get("intent_terms_json"),
					json.getTypeFactory().constructCollectionType(List.class, String.class)));
		}
		catch (Exception e) {
			throw new DataQueryException("UNSUPPORTED_CORRECTION");
		}
	}

	private void independent(ManagementActor actor, Map<String, Object> row) {
		if (actor.identity().userId().equals(String.valueOf(row.get("created_by")))
				&& !metadata.testSelfReviewAllowed(actor.identity()))
			fail("REVIEWER_MUST_DIFFER");
	}

	private String encode(Object terms) {
		try {
			return json.writeValueAsString(terms);
		}
		catch (Exception e) {
			throw new DataQueryException("INVALID_REQUEST");
		}
	}

	private static String digest(String text) {
		try {
			return HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static YearMonth month(String month) {
		try {
			if (month == null || !month.matches("[0-9]{4}-[0-9]{2}"))
				fail("INVALID_REQUEST");
			return YearMonth.parse(month);
		}
		catch (DateTimeException e) {
			throw new DataQueryException("INVALID_REQUEST");
		}
	}

	private static void text(String value, int max) {
		if (value == null || value.isBlank() || value.length() > max)
			fail("INVALID_REQUEST");
	}

	private static void fail(String code) {
		throw new DataQueryException(code);
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

}
