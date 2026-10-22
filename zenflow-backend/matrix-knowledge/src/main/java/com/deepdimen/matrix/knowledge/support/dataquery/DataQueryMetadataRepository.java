package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Platform metadata only. Every read/write includes the trusted platform tenant
 * explicitly; no reliance on request headers or MyBatis thread-local tenant state. No
 * publication API. Existing ai_data / ai_datasource rows are reused, never a second
 * credential inventory.
 */
public final class DataQueryMetadataRepository {

	public static final String DATASET = "top.patrol";

	// Versioned executable definition, not a user-editable SQL/formula document.
	public static final String DEFINITION_DIGEST = fingerprint(DEFINITION,
			"count records;start_date>=from;start_date<to;del_flag=0;group company+department;record-time names;v1");

	private final JdbcTemplate jdbc;

	private final TransactionTemplate auditTransaction;

	private final ObjectMapper json;

	private final Clock clock;

	private final DataQueryReviewPolicy reviewPolicy;

	private final DataQueryTechnicalExperienceStore technicalExperiences;

	private final DataQueryVisualizations visualizations;

	public DataQueryVisualizations visualizations() {
		return visualizations;
	}

	private DataQueryQueryEvidence queryEvidence;

	private DataQueryRelationStore relationStore;

	private DataQuerySqlExamples sqlExamples;

	void sqlExamples(DataQuerySqlExamples store) {
		this.sqlExamples = store;
	}

	DataQuerySqlExamples sqlExamples() {
		return sqlExamples;
	}

	DataQueryQueryEvidence.Snapshot exampleSource(Identity owner, String queryId) {
		if (queryEvidence == null)
			throw new DataQueryException("SQL_EXAMPLE_SEED_UNAVAILABLE");
		return queryEvidence.load(owner, queryId);
	}

	java.util.List<DataQuerySqlExamples.Match> exampleMatches(Identity owner, String question, Authorization scope,
			DatasetInventory inventory) {
		return sqlExamples == null ? java.util.List.of() : sqlExamples.retrieve(owner, question, scope, inventory);
	}

	void investigations(DataQueryQueryEvidence evidence, DataQueryRelationStore relations) {
		this.queryEvidence = evidence;
		this.relationStore = relations;
	}

	DataQueryRelationStore relations() {
		return relationStore;
	}

	java.util.List<DataQueryRelationStore.Match> relationMatches(Identity identity, Authorization scope,
			DatasetInventory inventory) {
		return relationStore == null ? java.util.List.of() : relationStore.matches(identity, scope, inventory);
	}

	void captureQueryEvidence(DataQueryAiChatService.Context context, DataQuerySqlGuard.Prepared sql) {
		if (queryEvidence != null)
			queryEvidence.save(context, sql);
	}

	void captureQueryEvidence(DataQueryAiChatService.Context context, DataQuerySqlGuard.Prepared executed,
			DataQuerySqlGuard.Prepared logicalQuery) {
		if (queryEvidence != null)
			queryEvidence.save(context, executed, logicalQuery);
	}

	DataQueryTopFollowup.Source topFollowup(DataQueryAiChatService.Context current) {
		var request = DataQueryTopFollowup.parse(current.question());
		if (request == null)
			return null;
		for (var row : ownedConversationRows(current)) {
			String code = Objects.toString(row.get("result_code"), "");
			if (code.startsWith("ASSISTANCE_"))
				continue;
			if (!request.matches((String) row.get("question_text")))
				return null;
			if (!java.util.Set.of("OK", "QUERY_TIMEOUT", "RESULT_LIMIT_EXCEEDED").contains(code)
					|| queryEvidence == null)
				throw new DataQueryException("FOLLOWUP_CONTEXT_UNAVAILABLE");
			String queryId = (String) row.get("query_id");
			try {
				var source = queryEvidence.load(current.identity(), queryId);
				if (!source.authorization().equals(current.authorization())
						|| !source.inventoryDigest().equals(current.inventory().digest())
						|| !Objects.equals(source.ruleVersion(), current.ruleVersion())
						|| !DataQueryAiChatService.EXECUTION_VERSION.equals(source.executionVersion())
						|| !Objects.equals(source.relationVersions(), current.relations()))
					throw new DataQueryException("FOLLOWUP_CONTEXT_UNAVAILABLE");
				var query = source.logicalQuery();
				// Legacy device SQL is reusable; instrumented department SQL must not
				// leak hidden projection columns.
				if (query == null && !source.query().sql().toLowerCase(java.util.Locale.ROOT).contains("__dq"))
					query = source.query();
				if (query == null || !current.inventory().tables().containsAll(query.tables()))
					throw new DataQueryException("FOLLOWUP_CONTEXT_UNAVAILABLE");
				return new DataQueryTopFollowup.Source(queryId, query, request);
			}
			catch (DataQueryException unavailable) {
				throw new DataQueryException("FOLLOWUP_CONTEXT_UNAVAILABLE");
			}
		}
		if (request.shortRequest())
			throw new DataQueryException("FOLLOWUP_CONTEXT_UNAVAILABLE");
		return null;
	}

	public DataQueryMetadataRepository(DataSource platformDataSource, ObjectMapper json, Clock clock) {
		this(platformDataSource, json, clock, DataQueryReviewPolicy.INDEPENDENT);
	}

	public DataQueryMetadataRepository(DataSource platformDataSource, ObjectMapper json, Clock clock,
			DataQueryReviewPolicy reviewPolicy) {
		this.reviewPolicy = Objects.requireNonNull(reviewPolicy);
		this.jdbc = new JdbcTemplate(Objects.requireNonNull(platformDataSource));
		this.jdbc.setQueryTimeout(5);
		this.auditTransaction = new TransactionTemplate(new DataSourceTransactionManager(platformDataSource));
		this.auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.auditTransaction.setTimeout(5);
		this.json = Objects.requireNonNull(json);
		this.clock = Objects.requireNonNull(clock);
		this.technicalExperiences = new DataQueryTechnicalExperienceStore(platformDataSource, json, clock);
		this.visualizations = new DataQueryVisualizations(this, jdbc, json, clock, auditTransaction);
	}

	public DataQueryTechnicalExperienceStore technicalExperiences() {
		return technicalExperiences;
	}

	public void recordRepair(String id, Identity identity, java.util.Map<String, Object> report) {
		auditTransaction.executeWithoutResult(status -> {
			var query = findOwnedQuery(identity, id).orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
			try {
				var candidate = json.readValue(query.candidateJson(),
						new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
						});
				candidate.put("repair", report);
				if (jdbc.update(
						"UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=? AND tenant_id=? AND user_id=?",
						encode(candidate), id, tenant(identity), user(identity)) != 1)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
			catch (JsonProcessingException invalid) {
				throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
		});
	}

	void recordEvidence(String id, Identity identity, String key, Object value) {
		if (!java.util.Set
			.of("databaseDiagnostic", "department", "page", "sqlExamples", "executionTrace", "assistance",
					"resultPreview", "visualization", "scopeReuse", "executionPolicy")
			.contains(key))
			throw new DataQueryException("INVALID_REQUEST");
		auditTransaction.executeWithoutResult(status -> {
			var query = findOwnedQuery(identity, id).orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
			try {
				var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(query.candidateJson());
				candidate.set(key, json.valueToTree(value));
				if (jdbc.update(
						"UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=? AND tenant_id=? AND user_id=?",
						encode(candidate), id, tenant(identity), user(identity)) != 1)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
			catch (JsonProcessingException e) {
				throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
		});
	}

	/**
	 * Only expose the requesting user's own matching draft readiness; never another
	 * user's feedback.
	 */
	String departmentDraftStatus(DataQueryAiChatService.Context context) {
		try {
			var rows = jdbc.queryForList("""
					SELECT v.department_payload_json,v.base_version
					FROM ai_dataquery_rule_version v JOIN ai_dataquery_feedback f
					ON f.feedback_id=v.feedback_id AND f.tenant_id=v.tenant_id
					JOIN ai_dataquery_query q ON q.query_id=f.query_id AND q.tenant_id=f.tenant_id
					AND q.user_id=f.user_id AND q.dataset_key=v.dataset_key
					WHERE v.tenant_id=? AND v.dataset_key=? AND f.user_id=?
					AND v.state IN ('DRAFT','VERIFIED','APPROVED') AND v.department_payload_json IS NOT NULL
					ORDER BY v.created_at DESC,v.rule_version DESC LIMIT 21
					""", tenant(context.identity()), DATASET, user(context.identity()));
			if (rows.size() > 20)
				return "UNAVAILABLE";
			for (var row : rows) {
				var definition = json.readValue((String) row.get("department_payload_json"),
						DataQueryDepartmentDimension.Definition.class);
				definition.validate();
				if (Objects.equals(row.get("base_version"), context.ruleVersion())
						&& definition.dataId() == context.authorization().source().dataId()
						&& definition.bindingRevision().equals(context.authorization().source().revision())
						&& definition.inventoryDigest().equals(context.inventory().digest()))
					return "UNPUBLISHED";
			}
			return "NONE";
		}
		catch (RuntimeException | JsonProcessingException unavailable) {
			return "UNAVAILABLE"; // Presentation unavailability is not a new execution
									// gate.
		}
	}

	void recordExplanation(DataQueryAiChatService.Context context, DataQueryExecutionExplanation.Snapshot snapshot) {
		if (!context.queryId().equals(snapshot.queryId())
				|| !Objects.equals(context.ruleVersion(), snapshot.ruleVersion()))
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		auditTransaction.executeWithoutResult(status -> {
			var query = findOwnedQuery(context.identity(), context.queryId())
				.orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
			try {
				var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(query.candidateJson());
				var explanation = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(snapshot);
				if (candidate.has("sqlExamples"))
					explanation.set("sqlExamples", candidate.get("sqlExamples"));
				candidate.set("executionExplanation", explanation);
				if (jdbc.update(
						"UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=? AND tenant_id=? AND user_id=? AND dataset_key=?",
						encode(candidate), context.queryId(), tenant(context.identity()), user(context.identity()),
						DATASET) != 1)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
			catch (JsonProcessingException invalid) {
				throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
		});
	}

	com.fasterxml.jackson.databind.JsonNode storedExplanation(Identity identity, String queryId) {
		var query = findOwnedQuery(identity, queryId).orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
		try {
			return validExplanation(json.readTree(query.candidateJson()), queryId, query.ruleVersion(),
					"OK".equals(query.resultCode()) ? "SUCCEEDED" : "ERROR");
		}
		catch (JsonProcessingException invalid) {
			return null;
		}
	}

	private com.fasterxml.jackson.databind.JsonNode validExplanation(com.fasterxml.jackson.databind.JsonNode candidate,
			String queryId, String version, String status) {
		var record = candidate == null ? null : candidate.get("executionExplanation");
		if (record == null || !record.isObject()
				|| !DataQueryExecutionExplanation.VERSION.equals(record.path("version").asText())
				|| !queryId.equals(record.path("queryId").asText())
				|| !status.equals(record.path("executionStatus").asText()) || !Objects.equals(version,
						record.path("ruleVersion").isNull() ? null : record.path("ruleVersion").asText()))
			return null;
		return record;
	}

	public boolean testSelfReviewAllowed(Identity identity) {
		return reviewPolicy.allows(identity);
	}

	public java.util.Set<Long> registeredPilotIds() {
		return new java.util.HashSet<>(
				jdbc.queryForList("SELECT DISTINCT data_id FROM ai_dataquery_binding", Long.class));
	}

	static final String REFERENCE_ONLY = "reference-metadata-only-v1";

	public record ExampleDataset(String dataId, String name, String executionStatus) {
	}

	/**
	 * Management metadata is not a database execution grant. Never selects credentials.
	 */
	SourceBinding exampleBinding(Identity identity, String dataId) {
		long id = DataQuerySetupService.id(dataId);
		var configured = jdbc.queryForList(
				"SELECT data_id FROM ai_dataquery_binding WHERE tenant_id=? AND dataset_key=?", Long.class,
				tenant(identity), DATASET);
		if (configured.contains(id))
			return resolve(identity).source(); // Disabled/revoked/changed never falls
												// back.
		var candidates = jdbc.query("""
				SELECT d.data_id,s.id,s.url,s.username,s.ds_type FROM ai_data d JOIN ai_datasource s
				ON s.tenant_id=d.tenant_id AND s.name=d.ds_name AND s.del_flag='0'
				WHERE d.tenant_id=? AND d.data_id=? AND d.del_flag='0'
				""",
				(rs, n) -> "mysql".equalsIgnoreCase(rs.getString(5))
						? new SourceBinding(rs.getLong(1), rs.getLong(2), REFERENCE_ONLY, connectionFingerprint(
								rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)))
						: null,
				tenant(identity), id);
		if (candidates.size() != 1 || candidates.get(0) == null || !canReadDatasetMetadata(identity))
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		return candidates.get(0);
	}

	private boolean canReadDatasetMetadata(Identity identity) {
		return canReadDatasetMetadata(jdbc, identity);
	}

	static boolean canReadDatasetMetadata(JdbcTemplate jdbc, Identity identity) {
		return !jdbc.queryForList("""
				SELECT DISTINCT m.permission FROM sys_user u JOIN sys_tenant_user tu ON tu.user_id=u.user_id
				JOIN sys_user_role ur ON ur.user_id=u.user_id AND ur.tenant_id=tu.tenant_id
				JOIN sys_role r ON r.role_id=ur.role_id AND r.tenant_id=ur.tenant_id
				JOIN sys_role_menu rm ON rm.role_id=r.role_id
				JOIN sys_menu m ON m.menu_id=rm.menu_id AND m.tenant_id=r.tenant_id
				WHERE tu.tenant_id=? AND u.user_id=? AND u.del_flag='0' AND u.lock_flag='0'
				AND r.del_flag='0' AND m.del_flag='0' AND m.permission='knowledge_aiData_view'
				""", String.class, tenant(identity), user(identity)).isEmpty();
	}

	List<ExampleDataset> exampleDatasets(Identity identity) {
		var rows = jdbc.query(
				"SELECT data_id,dataset_name FROM ai_data WHERE tenant_id=? AND del_flag='0' ORDER BY data_id LIMIT 501",
				(rs, n) -> new ExampleDataset(rs.getString(1), rs.getString(2), null), tenant(identity));
		if (rows.size() > 500)
			throw new DataQueryException("SQL_EXAMPLE_DATASET_LIMIT");
		var result = new ArrayList<ExampleDataset>();
		for (var row : rows) {
			try {
				var source = exampleBinding(identity, row.dataId());
				result.add(new ExampleDataset(row.dataId(), row.name(),
						REFERENCE_ONLY.equals(source.revision()) ? "MANAGEMENT_ONLY" : "EXECUTABLE"));
			}
			catch (DataQueryException denied) {
				if (!java.util.Set
					.of("DATASET_NOT_AUTHORIZED", "DATASOURCE_BINDING_REQUIRED", "DATASOURCE_BINDING_CHANGED",
							"AUTHORIZATION_REQUIRED", "AUTHORIZATION_EXPIRED", "MATRIX_USER_NOT_ACTIVE")
					.contains(denied.getMessage()))
					throw denied;
			}
		}
		return List.copyOf(result);
	}

	String exampleSchema(Identity identity, SourceBinding source) {
		if (!source.equals(exampleBinding(identity, Long.toString(source.dataId()))))
			throw new DataQueryException("DATASOURCE_BINDING_CHANGED");
		var urls = jdbc.queryForList("SELECT url FROM ai_datasource WHERE tenant_id=? AND id=? AND del_flag='0'",
				String.class, tenant(identity), source.datasourceId());
		try {
			if (urls.size() != 1 || !urls.get(0).startsWith("jdbc:mysql://"))
				throw new IllegalArgumentException();
			var uri = new java.net.URI(urls.get(0).substring(5));
			String path = uri.getRawPath();
			if (path == null || !path.matches("/[A-Za-z_][A-Za-z0-9_]{0,63}"))
				throw new IllegalArgumentException();
			return path.substring(1);
		}
		catch (java.net.URISyntaxException | IllegalArgumentException invalid) {
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		}
	}

	DatasetInventory exampleInventory(Identity identity, SourceBinding source) {
		if (!source.equals(exampleBinding(identity, Long.toString(source.dataId()))))
			throw new DataQueryException("DATASOURCE_BINDING_CHANGED");
		return readInventory(identity, source);
	}

	public Authorization resolve(Identity identity) {
		SourceBinding source = binding(identity);
		return new DataQueryAccessRepository(jdbc, json, clock).resolve(identity, source);
	}

	public PublishedRule current(Identity identity) {
		List<PublishedRule> rules = jdbc.query(
				"""
						SELECT r.rule_version, r.definition_key, r.definition_digest, r.regression_digest, r.intent_terms_json, r.content_digest, r.department_payload_json
						FROM ai_dataquery_rule_active a JOIN ai_dataquery_rule_version r
						ON r.tenant_id = a.tenant_id AND r.dataset_key = a.dataset_key AND r.rule_version = a.rule_version
						WHERE a.tenant_id = ? AND a.dataset_key = ? AND r.state = 'PUBLISHED'
						AND r.created_by > 0 AND r.approved_by > 0
						AND (r.approved_by <> r.created_by OR (r.tenant_id = ? AND r.created_by = ?))
						AND r.published_by > 0 AND r.validated_at >= r.created_at
						AND r.approved_at >= r.validated_at AND r.published_at >= r.approved_at AND r.published_at <= ?
						""",
				(rs, index) -> {
					if (!DEFINITION.equals(rs.getString(2)) || !DEFINITION_DIGEST.equals(rs.getString(3))
							|| rs.getString(4) == null || !rs.getString(4).matches("[0-9a-f]{64}")) {
						throw new DataQueryException("METRIC_DEFINITION_UNCONFIRMED");
					}
					List<String> terms = readTerms(rs.getString(5));
					String digest = rs.getString(6);
					if (rs.getString(7) != null && digest == null)
						throw new DataQueryException("DEPARTMENT_DEFINITION_INVALID");
					if (digest == null ? !terms.equals(List.of("巡视"))
							: !digest.equals(ruleContentDigest(terms, rs.getString(7))))
						throw new DataQueryException("METRIC_DEFINITION_UNCONFIRMED");
					return new PublishedRule(rs.getString(1), rs.getString(2), true, terms,
							readDepartment(rs.getString(7)));
				}, tenant(identity), DATASET, reviewPolicy.tenantId(), reviewPolicy.userId(), now());
		return rules.size() == 1 ? rules.get(0) : null;
	}

	private static final String SOURCE_SELECT = """
			SELECT b.data_id, b.datasource_id, b.binding_revision, b.connection_fingerprint,
			s.url, s.username, s.ds_type%s FROM ai_dataquery_binding b
			JOIN ai_data d ON d.data_id = b.data_id AND d.tenant_id = b.tenant_id AND d.del_flag = '0'
			JOIN ai_datasource s ON s.id = b.datasource_id AND s.tenant_id = b.tenant_id
			AND s.name = d.ds_name AND s.del_flag = '0'
			WHERE b.tenant_id = ? AND b.dataset_key = ? AND b.enabled = 1
			AND b.approved_by > 0 AND b.approved_at IS NOT NULL AND b.approved_at <= ?
			""";

	public SourceBinding binding(Identity identity) {
		// Authorization lookup never selects the password column.
		List<SourceBinding> bindings = jdbc.query(SOURCE_SELECT.formatted(""), (rs, index) -> checkedBinding(rs),
				tenant(identity), DATASET, now());
		if (bindings.size() != 1)
			throw new DataQueryException("DATASOURCE_BINDING_REQUIRED");
		return bindings.get(0);
	}

	private SourceBinding checkedBinding(ResultSet rs) throws SQLException {
		String type = rs.getString("ds_type");
		String revision = rs.getString("binding_revision");
		String expected = rs.getString("connection_fingerprint");
		String actual = connectionFingerprint(rs.getLong("data_id"), rs.getLong("datasource_id"), rs.getString("url"),
				rs.getString("username"), type);
		if (!"mysql".equalsIgnoreCase(type) || revision == null || revision.isBlank() || !actual.equals(expected)) {
			throw new DataQueryException("DATASOURCE_BINDING_CHANGED");
		}
		return new SourceBinding(rs.getLong("data_id"), rs.getLong("datasource_id"), revision, expected);
	}

	/**
	 * Short-lived credentials for backend execution only. Never serialize, log, or
	 * persist again.
	 */
	static final class ReaderCredentials {

		final String url;

		final String username;

		final String password;

		ReaderCredentials(String url, String username, String password) {
			this.url = url;
			this.username = username;
			this.password = password;
		}

		@Override
		public String toString() {
			return "ReaderCredentials[REDACTED]";
		}

	}

	ReaderCredentials credentials(Identity identity, SourceBinding expected) {
		if (expected == null)
			throw new DataQueryException("DATASOURCE_BINDING_REQUIRED");
		List<ReaderCredentials> rows = jdbc.query(SOURCE_SELECT.formatted(", s.password"), (rs, index) -> {
			if (!checkedBinding(rs).equals(expected))
				throw new DataQueryException("DATASOURCE_BINDING_CHANGED");
			return new ReaderCredentials(rs.getString("url"), rs.getString("username"), rs.getString("password"));
		}, tenant(identity), DATASET, now());
		if (rows.size() != 1)
			throw new DataQueryException("DATASOURCE_BINDING_REQUIRED");
		return rows.get(0);
	}

	public void requireAiSqlSchema() {
		try {
			Boolean ready = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
				try (var statement = connection
					.prepareStatement("SELECT rule_version,chart_json FROM ai_dataquery_query WHERE 1=0");
						var result = statement.executeQuery()) {
					return result.getMetaData().isNullable(1) == java.sql.ResultSetMetaData.columnNullable;
				}
			});
			if (!Boolean.TRUE.equals(ready))
				throw new DataQueryException("AI_SQL_SCHEMA_REQUIRED");
		}
		catch (RuntimeException e) {
			throw new DataQueryException("AI_SQL_SCHEMA_REQUIRED");
		}
	}

	public record DatasetInventory(java.util.Set<String> tables, String schema, String digest) {
		public DatasetInventory {
			tables = java.util.Set.copyOf(tables);
		}
	}

	/**
	 * Reads only the tenant's exact selected table inventory. Never syncs or evaluates
	 * metadata.
	 */
	public DatasetInventory inventory(Identity identity, SourceBinding source) {
		if (!source.equals(binding(identity)))
			throw new DataQueryException("DATASOURCE_BINDING_CHANGED");
		return readInventory(identity, source);
	}

	private DatasetInventory readInventory(Identity identity, SourceBinding source) {
		return readInventory(jdbc, json, identity, source.dataId());
	}

	static DatasetInventory readInventory(JdbcTemplate jdbc, ObjectMapper json, Identity identity, long dataId) {
		var selections = jdbc.queryForList(
				"SELECT table_name FROM ai_data WHERE tenant_id=? AND data_id=? AND del_flag='0'", String.class,
				tenant(identity), dataId);
		if (selections.size() != 1 || selections.get(0) == null)
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		var names = new java.util.TreeSet<String>();
		for (String selected : selections.get(0).split(",", -1)) {
			String name = selected.trim();
			if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || !names.add(name))
				throw new DataQueryException("DATASET_METADATA_REQUIRED");
		}
		if (names.isEmpty() || names.size() > 200)
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		var tables = new ArrayList<Object>();
		for (String name : names) {
			var table = jdbc.queryForList("""
					SELECT t.table_id,t.table_name,t.table_comment,t.virtual_comment FROM ai_data_table t
					JOIN ai_data d ON d.tenant_id=t.tenant_id AND d.ds_name=t.ds_name
					WHERE d.tenant_id=? AND d.data_id=? AND d.del_flag='0' AND t.table_name=?
					""", tenant(identity), dataId, name);
			if (table.size() != 1 || !name.equals(table.get(0).get("table_name")))
				throw new DataQueryException("DATASET_METADATA_REQUIRED");
			var fields = jdbc.queryForList("""
					SELECT f.field_name,f.field_type,f.field_comment,f.virtual_comment FROM ai_data_field f
					JOIN ai_data d ON d.tenant_id=f.tenant_id AND d.ds_name=f.ds_name
					WHERE d.tenant_id=? AND d.data_id=? AND d.del_flag='0' AND f.table_id=? ORDER BY f.field_name
					""", tenant(identity), dataId, table.get(0).get("table_id"));
			if (fields.isEmpty())
				throw new DataQueryException("DATASET_METADATA_REQUIRED");
			var fieldsSeen = new java.util.HashSet<String>();
			for (var field : fields) {
				Object nameValue = field.get("field_name");
				if (!(nameValue instanceof String fieldName) || !fieldName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")
						|| !fieldsSeen.add(fieldName.toLowerCase(java.util.Locale.ROOT)))
					throw new DataQueryException("DATASET_METADATA_REQUIRED");
			}
			var item = new java.util.LinkedHashMap<String, Object>();
			for (String key : List.of("table_name", "table_comment", "virtual_comment"))
				item.put(key, table.get(0).get(key));
			var columns = new ArrayList<java.util.Map<String, Object>>();
			for (var field : fields) {
				var column = new java.util.LinkedHashMap<String, Object>();
				for (String key : List.of("field_name", "field_type", "field_comment", "virtual_comment"))
					column.put(key, field.get(key));
				columns.add(column);
			}
			item.put("fields", columns);
			tables.add(item);
		}
		String schema;
		try {
			schema = json.writeValueAsString(tables);
		}
		catch (JsonProcessingException e) {
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		}
		if (schema.length() > 200000)
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		return new DatasetInventory(names, schema, fingerprint(schema));
	}

	/**
	 * No active business rule is valid for ordinary AI SQL; a corrupt active pointer is
	 * not.
	 */
	public PublishedRule optionalCurrent(Identity identity) {
		var rule = current(identity);
		if (rule == null && !jdbc
			.queryForList("SELECT rule_version FROM ai_dataquery_rule_active WHERE tenant_id=? AND dataset_key=?",
					String.class, tenant(identity), DATASET)
			.isEmpty())
			throw new DataQueryException("METRIC_DEFINITION_UNCONFIRMED");
		return rule;
	}

	public void startedSql(String id, Identity identity, PublishedRule rule, Authorization scope, QueryOrigin origin,
			String inventoryDigest) {
		String candidate = encode(
				java.util.Map.of("kind", "AI_SQL", "executionVersion", DataQueryAiChatService.EXECUTION_VERSION,
						"dataId", Long.toString(scope.source().dataId()), "inventoryDigest", inventoryDigest));
		auditTransaction.executeWithoutResult(status -> jdbc.update(
				"""
						INSERT INTO ai_dataquery_query
						(query_id,tenant_id,user_id,dataset_key,rule_version,scope_revision,candidate_json,status,started_at,original_question,chat_record_id)
						VALUES (?,?,?,?,?,?,?,'STARTED',?,?,?)
						""",
				id, tenant(identity), user(identity), DATASET, rule == null ? null : rule.version(), scope.revision(),
				candidate, now(), origin.question(), origin.chatRecordId()));
	}

	public void recordSqlDigest(String id, Identity identity, DataQuerySqlGuard.Prepared sql) {
		auditTransaction.executeWithoutResult(status -> {
			var query = findOwnedQuery(identity, id).orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
			try {
				var candidate = json.readValue(query.candidateJson(),
						new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
						});
				candidate.put("sqlDigest", fingerprint(sql.sql()));
				candidate.put("sqlFacts", DataQuerySqlDiagnostics.describe(sql));
				candidate.put("tables", new java.util.TreeSet<>(sql.tables()));
				if (jdbc.update(
						"UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=? AND tenant_id=? AND user_id=? AND status='STARTED'",
						encode(candidate), id, tenant(identity), user(identity)) != 1)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
			catch (JsonProcessingException e) {
				throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
		});
	}

	public void saveChartReference(Identity identity, String username, long messageId, String question, String chartId,
			String chartType) {
		var details = jdbc.queryForList(
				"SELECT ext_details FROM ai_chat_record WHERE record_id=? AND tenant_id=? AND username=? AND question_text=? AND del_flag='0'",
				String.class, messageId, tenant(identity), username, question);
		if (details.size() != 1)
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		try {
			var ext = json.readValue(details.get(0),
					new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
					});
			ext.put("chartId", chartId);
			ext.put("chartType", chartType);
			if (jdbc.update(
					"UPDATE ai_chat_record SET ext_details=? WHERE record_id=? AND tenant_id=? AND username=? AND question_text=? AND del_flag='0' AND ext_details=?",
					encode(ext), messageId, tenant(identity), username, question, details.get(0)) != 1)
				throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		}
		catch (JsonProcessingException e) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
	}

	public void saveChart(Identity identity, String queryId, String chartJson) {
		if (chartJson == null || chartJson.length() > 1000000)
			throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
		if (jdbc.update(
				"UPDATE ai_dataquery_query SET chart_json=? WHERE query_id=? AND tenant_id=? AND user_id=? AND dataset_key=? AND status='FINISHED' AND result_code='OK'",
				chartJson, queryId, tenant(identity), user(identity), DATASET) != 1)
			throw new DataQueryException("QUERY_NOT_FOUND");
	}

	public String chart(Identity identity, String queryId) {
		var charts = jdbc.queryForList(
				"SELECT chart_json FROM ai_dataquery_query WHERE query_id=? AND tenant_id=? AND user_id=? AND dataset_key=? AND status='FINISHED' AND result_code='OK'",
				String.class, queryId, tenant(identity), user(identity), DATASET);
		if (charts.size() != 1)
			throw new DataQueryException("QUERY_NOT_FOUND");
		return charts.get(0);
	}

	private String encode(Object value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
	}

	public void started(String queryId, Identity identity, String ruleVersion, String scopeRevision,
			PatrolQueryCompiler.Candidate candidate) {
		String candidateJson;
		try {
			candidateJson = json.writeValueAsString(candidate);
		}
		catch (JsonProcessingException e) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
		auditTransaction.executeWithoutResult(status -> jdbc.update(
				"""
						INSERT INTO ai_dataquery_query
						(query_id, tenant_id, user_id, dataset_key, rule_version, scope_revision, candidate_json, status, started_at)
						VALUES (?, ?, ?, ?, ?, ?, ?, 'STARTED', ?)
						""",
				queryId, tenant(identity), user(identity), DATASET, ruleVersion, scopeRevision, candidateJson, now()));
	}

	public void finished(String queryId, Identity identity, String code, int groupCount) {
		if (code == null || !code.matches("[A-Z_]{1,64}") || groupCount < 0 || groupCount > 1000) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
		auditTransaction.executeWithoutResult(status -> {
			int changed = jdbc.update("""
					UPDATE ai_dataquery_query SET status = 'FINISHED', result_code = ?, group_count = ?, finished_at = ?
					WHERE query_id = ? AND tenant_id = ? AND user_id = ? AND status = 'STARTED'
					""", code, groupCount, now(), queryId, tenant(identity), user(identity));
			if (changed != 1)
				throw new DataQueryException("AUDIT_UNAVAILABLE");
		});
	}

	public void failedSql(String queryId, Identity identity, String code) {
		auditTransaction.executeWithoutResult(status -> {
			if (jdbc.update(
					"UPDATE ai_dataquery_query SET status='FINISHED',result_code=?,group_count=0,chart_json=NULL,finished_at=? WHERE query_id=? AND tenant_id=? AND user_id=?",
					code, now(), queryId, tenant(identity), user(identity)) != 1)
				throw new DataQueryException("AUDIT_UNAVAILABLE");
		});
	}

	public record QueryRecord(String queryId, String ruleVersion, String scopeRevision, String candidateJson,
			String status, String resultCode, Integer groupCount, String originalQuestion, Long chatRecordId,
			LocalDateTime startedAt, LocalDateTime finishedAt) {
		public QueryRecord(String queryId, String ruleVersion, String scopeRevision, String candidateJson,
				String status, String resultCode, Integer groupCount, String originalQuestion, Long chatRecordId) {
			this(queryId, ruleVersion, scopeRevision, candidateJson, status, resultCode, groupCount, originalQuestion,
					chatRecordId, null, null);
		}
	}

	/**
	 * Ownership-checked lookup for the upcoming feedback flow. No global query-id lookup
	 * API.
	 */
	public Optional<QueryRecord> findOwnedQuery(Identity identity, String queryId) {
		List<QueryRecord> records = jdbc.query(
				"""
						SELECT query_id, rule_version, scope_revision, candidate_json, status, result_code, group_count, original_question, chat_record_id, started_at, finished_at
						FROM ai_dataquery_query WHERE query_id = ? AND tenant_id = ? AND user_id = ? AND dataset_key = ?
						""",
				(rs, index) -> new QueryRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6), (Integer) rs.getObject(7), rs.getString(8),
						(Long) rs.getObject(9), rs.getObject(10, LocalDateTime.class),
						rs.getObject(11, LocalDateTime.class)),
				queryId, tenant(identity), user(identity), DATASET);
		return records.stream().findFirst();
	}

	public record HistoryReference(String state, java.util.Map<String, Object> dataQuery, String feedbackId) {
	}

	/**
	 * Bounded presentation lookup. Multiple queries for one chat message are ambiguous,
	 * never guessed.
	 */
	public java.util.Map<Long, HistoryReference> findOwnedChatReferences(Identity identity, String username,
			List<Long> messageIds) {
		if (messageIds == null || messageIds.isEmpty())
			return java.util.Map.of();
		if (messageIds.size() > 200 || messageIds.stream().anyMatch(java.util.Objects::isNull))
			throw new DataQueryException("INVALID_REQUEST");
		String placeholders = String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
		List<Object> args = new ArrayList<>(List.of(tenant(identity), user(identity), DATASET, username));
		args.addAll(messageIds);
		var rows = jdbc.query(
				"""
						SELECT c.record_id,c.username,c.question_text,q.query_id,q.original_question,q.rule_version,q.status,q.result_code,
						(SELECT f.feedback_id FROM ai_dataquery_feedback f WHERE f.query_id=q.query_id AND f.tenant_id=q.tenant_id AND f.user_id=q.user_id ORDER BY f.created_at DESC,f.feedback_id DESC LIMIT 1) AS feedback_id,q.candidate_json
						FROM ai_dataquery_query q JOIN ai_chat_record c ON c.record_id=q.chat_record_id AND c.tenant_id=q.tenant_id AND c.del_flag='0'
						WHERE q.tenant_id=? AND q.user_id=? AND q.dataset_key=? AND c.username=? AND c.record_id IN (%s)
						ORDER BY q.query_id LIMIT 2001
						"""
					.formatted(placeholders),
				(rs, n) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
						rs.getString(10) },
				args.toArray());
		java.util.Map<Long, HistoryReference> result = new java.util.HashMap<>();
		if (rows.size() > 2000) {
			messageIds.forEach(id -> result.put(id, new HistoryReference("UNAVAILABLE", null, null)));
			return result;
		}
		for (var row : rows) {
			long id = (Long) row[0];
			if (result.containsKey(id)) {
				result.put(id, new HistoryReference("AMBIGUOUS", null, null));
				continue;
			}
			if (!username.equals(row[1]) || row[4] == null || !row[4].equals(row[2])) {
				result.put(id, new HistoryReference("UNAVAILABLE", null, null));
				continue;
			}
			String code = (String) row[7];
			boolean running = "STARTED".equals(row[6]);
			String status = running ? "RUNNING"
					: "FINISHED".equals(row[6]) && "ASSISTANCE_COMPLETED".equals(code) ? "ANSWERED"
							: "FINISHED".equals(row[6]) && "OK".equals(code) ? "SUCCEEDED"
									: DataQueryResponses.isClarification(code) ? "NEEDS_CLARIFICATION" : "ERROR";
			var reference = new java.util.LinkedHashMap<String, Object>();
			reference.put("queryId", row[3]);
			reference.put("ruleVersion", row[5]);
			try {
				var candidate = json.readTree((String) row[9]);
				var explanation = validExplanation(candidate, (String) row[3], (String) row[5], status);
				// A live audit is not a failed or incomplete historical result. History
				// reads
				// only report durable state; they never resume/replay model or SQL
				// execution.
				if (!running)
					reference.put("explanation", explanation == null
							? DataQueryExecutionExplanation.historicalUnknown((String) row[3], (String) row[5], status)
							: json.convertValue(explanation, java.util.Map.class));
				if (candidate != null && "AI_SQL".equals(candidate.path("kind").asText())) {
					reference.put("executionVersion", candidate.path("executionVersion").asText());
					reference.put("queryMode", "AI_SQL");
					if (java.util.Set.of("ASSISTANCE_COMPLETED", "ASSISTANCE_CLARIFICATION")
						.contains(code == null ? "" : code) && candidate.path("assistance").isObject()) {
						reference.remove("explanation");
						reference.put("queryMode", "AI_ASSISTANCE");
						reference.put("responseType", candidate.path("assistance").path("responseType").asText());
						reference.put("ruleApplication", "NONE");
						if (candidate.path("assistance").has("source"))
							reference.put("source", json.convertValue(candidate.path("assistance").path("source"),
									java.util.Map.class));
					}
					if ("SUCCEEDED".equals(status) && candidate.path("visualization").isObject())
						reference.put("visualization",
								json.convertValue(candidate.path("visualization"), java.util.Map.class));
					if ("SUCCEEDED".equals(status) && candidate.path("resultPreview").isObject())
						reference.put("resultPreview",
								json.convertValue(candidate.path("resultPreview"), java.util.Map.class));
					if (candidate.path("repair").isObject())
						reference.put("repair", json.convertValue(candidate.path("repair"), java.util.Map.class));
				}
			}
			catch (JsonProcessingException e) {
				result.put(id, new HistoryReference("UNAVAILABLE", null, null));
				continue;
			}
			reference.put("mock", false);
			reference.put("status", status);
			reference.put("code", code);
			if ("QUERY_YEAR_REQUIRED".equals(code))
				reference.put("clarification", new PatrolQuestionInterpreter().yearPrompt((String) row[4]));
			result.put(id, new HistoryReference("AVAILABLE", reference, (String) row[8]));
		}
		return result;
	}

	public void origin(String id, Identity identity, QueryOrigin origin) {
		auditTransaction.executeWithoutResult(status -> {
			if (jdbc.update(
					"UPDATE ai_dataquery_query SET original_question=?,chat_record_id=? WHERE query_id=? AND tenant_id=? AND user_id=? AND original_question IS NULL",
					origin.question(), origin.chatRecordId(), id, tenant(identity), user(identity)) != 1)
				throw new DataQueryException("AUDIT_UNAVAILABLE");
		});
	}

	public void saveOwnedChatAnswer(Identity identity, String username, long messageId, String question,
			String answer) {
		int changed = jdbc.update(
				"UPDATE ai_chat_record SET answer_text=? WHERE record_id=? AND tenant_id=? AND username=? AND question_text=? AND del_flag='0'",
				answer, messageId, tenant(identity), username, question);
		if (changed != 1)
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
	}

	/**
	 * Rehydrate bounded, owned turns; never share provider memory or replay an answer.
	 */
	private List<java.util.Map<String, Object>> ownedConversationRows(DataQueryAiChatService.Context current) {
		var sessions = jdbc.queryForList(
				"SELECT conversation_id FROM ai_chat_record WHERE record_id=? AND tenant_id=? AND username=? AND del_flag='0'",
				String.class, current.messageId(), tenant(current.identity()), current.username());
		if (sessions.size() != 1)
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		String session = sessions.get(0);
		if (session == null || session.isBlank())
			return List.of();
		var rows = jdbc.queryForList(
				"""
						SELECT c.conversation_id,c.username,c.question_text,c.answer_text,q.query_id,q.result_code,q.candidate_json,q.rule_version,q.finished_at,q.original_question
						FROM ai_chat_record c JOIN ai_chat_record anchor ON c.conversation_id=anchor.conversation_id
						JOIN ai_dataquery_query q ON q.chat_record_id=c.record_id AND q.tenant_id=c.tenant_id
						WHERE anchor.record_id=? AND anchor.tenant_id=? AND anchor.username=? AND anchor.del_flag='0'
						AND c.tenant_id=? AND c.username=? AND c.del_flag='0' AND c.record_id<?
						AND q.user_id=? AND q.dataset_key=? AND q.scope_revision=? AND q.status='FINISHED'
						AND q.finished_at<=(SELECT started_at FROM ai_dataquery_query WHERE query_id=? AND tenant_id=? AND user_id=?)
						AND (SELECT COUNT(*) FROM ai_dataquery_query other_query
						     WHERE other_query.tenant_id=q.tenant_id AND other_query.user_id=q.user_id AND other_query.chat_record_id=q.chat_record_id)=1
						ORDER BY c.record_id DESC LIMIT 12
						""",
				current.messageId(), tenant(current.identity()), current.username(), tenant(current.identity()),
				current.username(), current.messageId(), user(current.identity()), DATASET,
				current.authorization().revision(), current.queryId(), tenant(current.identity()),
				user(current.identity()));
		var eligible = new ArrayList<java.util.Map<String, Object>>();
		for (var row : rows) {
			// MySQL collation must not merge case-distinct session/user identifiers.
			if (!session.equals(row.get("conversation_id")) || !current.username().equals(row.get("username"))
					|| !Objects.equals(current.ruleVersion(), row.get("rule_version"))
					|| !Objects.equals(row.get("question_text"), row.get("original_question")))
				continue;
			try {
				var candidate = json.readTree((String) row.get("candidate_json"));
				if (candidate == null || !"AI_SQL".equals(candidate.path("kind").asText())
						|| !Long.toString(current.authorization().source().dataId())
							.equals(candidate.path("dataId").asText())
						|| !current.inventory().digest().equals(candidate.path("inventoryDigest").asText()))
					continue;
			}
			catch (JsonProcessingException invalid) {
				continue; // Missing provenance is not permission to trust a cached turn.
			}
			eligible.add(row);
		}
		return eligible;
	}

	public String conversationContext(DataQueryAiChatService.Context current) {
		var rows = ownedConversationRows(current);
		var history = new ArrayList<java.util.Map<String, Object>>();
		int remaining = 24000;
		for (var row : rows) {
			String question = (String) row.get("question_text"), answer = (String) row.get("answer_text");
			if (question == null || question.length() > 2000)
				continue;
			if (answer == null)
				answer = "";
			boolean excerpt = answer.length() > 6000;
			if (excerpt) {
				int end = answer.lastIndexOf('\n', 6000);
				answer = answer.substring(0, end > 0 ? end : 6000);
			}
			if (question.length() + answer.length() > remaining)
				break;
			remaining -= question.length() + answer.length();
			var turn = new java.util.LinkedHashMap<String, Object>();
			turn.put("queryId", row.get("query_id"));
			turn.put("queriedAt", String.valueOf(row.get("finished_at")));
			turn.put("question", question);
			turn.put("status", row.get("result_code"));
			turn.put("answer", answer);
			turn.put("answerIsExcerpt", excerpt);
			try {
				var candidate = json.readTree((String) row.get("candidate_json"));
				var assistance = candidate.path("assistance");
				if ("RESULT_EXPLANATION".equals(assistance.path("responseType").asText())
						&& assistance.path("source").isObject()) {
					var explanationSource = json.convertValue(assistance.path("source"), java.util.Map.class);
					turn.put("responseType", "RESULT_EXPLANATION");
					turn.put("source", explanationSource);
					turn.put("answer", DataQueryAiChatService.explanationBody(answer, explanationSource));
				}
				if ("OK".equals(row.get("result_code")) && candidate.path("resultPreview").isObject())
					turn.put("resultPreview", json.convertValue(candidate.path("resultPreview"), java.util.Map.class));
			}
			catch (JsonProcessingException invalid) {
				continue;
			}
			history.add(turn);
			if (history.size() == 6)
				break;
		}
		java.util.Collections.reverse(history);
		return encode(history);
	}

	/** Same bounded, authorization-checked history that was supplied to the model. */
	java.util.Map<String, Object> conversationResult(DataQueryAiChatService.Context current, String queryId) {
		if (queryId == null || !queryId.matches("[0-9a-f-]{36}"))
			throw new DataQueryException("RESULT_CONTEXT_UNAVAILABLE");
		try {
			for (var turn : json.readTree(conversationContext(current))) {
				if (queryId.equals(turn.path("queryId").asText()) && "OK".equals(turn.path("status").asText())
						&& !turn.path("answer").asText().isBlank()) {
					var source = new java.util.LinkedHashMap<String, Object>();
					source.put("queryId", queryId);
					source.put("queriedAt", turn.path("queriedAt").asText());
					source.put("answerIsExcerpt", turn.path("answerIsExcerpt").asBoolean());
					if (turn.path("resultPreview").isObject())
						source.put("resultPreview", json.convertValue(turn.path("resultPreview"), java.util.Map.class));
					return source;
				}
			}
		}
		catch (JsonProcessingException invalid) {
			throw new DataQueryException("RESULT_CONTEXT_UNAVAILABLE");
		}
		throw new DataQueryException("RESULT_CONTEXT_UNAVAILABLE");
	}

	/**
	 * Only the nearest successful owned turn may supply drilldown, never an older
	 * unrelated result.
	 */
	com.fasterxml.jackson.databind.JsonNode drilldownReference(DataQueryAiChatService.Context current) {
		for (var row : ownedConversationRows(current)) {
			String code = Objects.toString(row.get("result_code"), "");
			if (code.startsWith("ASSISTANCE_"))
				continue;
			// A failed newer data request is not permission to reuse an older scope.
			if (!"OK".equals(code))
				return null;
			try {
				var node = json.readTree((String) row.get("candidate_json"));
				if (node.has("page"))
					return node.path("page");
				if (node.has("department"))
					return node.path("department");
				return null;
			}
			catch (JsonProcessingException invalid) {
				throw new DataQueryException("DETAIL_SCOPE_UNAVAILABLE");
			}
		}
		return null;
	}

	public String ownedChatQuestion(Identity identity, String username, long messageId) {
		var questions = jdbc.query(
				"SELECT username,question_text FROM ai_chat_record WHERE record_id=? AND tenant_id=? AND username=? AND del_flag='0'",
				(rs, index) -> username.equals(rs.getString(1)) ? rs.getString(2) : null, messageId, tenant(identity),
				username);
		if (questions.size() != 1 || questions.get(0) == null)
			throw new DataQueryException("CHAT_RECORD_NOT_OWNED");
		return questions.get(0);
	}

	public static List<String> readTerms(String payload) {
		if (payload == null)
			return List.of("巡视");
		try {
			return PatrolQuestionInterpreter.normalizedTerms(new ObjectMapper().readValue(payload,
					new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
					}));
		}
		catch (Exception e) {
			throw new DataQueryException("METRIC_DEFINITION_UNCONFIRMED");
		}
	}

	public static String ruleContentDigest(List<String> terms) {
		return fingerprint(DEFINITION_DIGEST, String.join("\n", PatrolQuestionInterpreter.normalizedTerms(terms)));
	}

	static DataQueryDepartmentDimension.Definition readDepartment(String payload) {
		if (payload == null)
			return null;
		try {
			var definition = new ObjectMapper().readValue(payload, DataQueryDepartmentDimension.Definition.class);
			definition.validate();
			return definition;
		}
		catch (Exception invalid) {
			throw new DataQueryException("DEPARTMENT_DEFINITION_INVALID");
		}
	}

	static String ruleContentDigest(List<String> terms, String department) {
		return department == null ? ruleContentDigest(terms) : fingerprint(ruleContentDigest(terms), department);
	}

	void checkDepartmentBinding(Identity who, DataQueryDepartmentDimension.Definition definition) {
		definition.validate();
		var source = binding(who);
		if (source.dataId() != definition.dataId() || !source.revision().equals(definition.bindingRevision())
				|| !inventory(who, source).digest().equals(definition.inventoryDigest()))
			throw new DataQueryException("DEPARTMENT_RULE_STALE");
	}

	public static String connectionFingerprint(long dataId, long datasourceId, String url, String username,
			String type) {
		// Pin the approved target/reader identity, not password material (rotation is
		// allowed).
		return fingerprint(Long.toString(dataId), Long.toString(datasourceId), url, username, type);
	}

	static String fingerprint(String... fields) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String field : fields) {
				if (field == null)
					throw new DataQueryException("DATASOURCE_BINDING_REQUIRED");
				byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
				digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
				digest.update(bytes);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable");
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private static long tenant(Identity identity) {
		return numericId(identity == null ? null : identity.tenantId());
	}

	private static long user(Identity identity) {
		return numericId(identity == null ? null : identity.userId());
	}

	private static long numericId(String id) {
		try {
			if (id == null || !id.matches("[1-9][0-9]{0,18}"))
				throw new NumberFormatException();
			return Long.parseLong(id);
		}
		catch (NumberFormatException e) {
			throw new DataQueryException("UNAUTHENTICATED");
		}
	}

}
