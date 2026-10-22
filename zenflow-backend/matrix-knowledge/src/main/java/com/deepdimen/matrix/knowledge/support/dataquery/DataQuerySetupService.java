package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.ManagementActor;

/**
 * Human-only configuration of existing platform inventory. Never connects to TOP or
 * changes server settings.
 */
public final class DataQuerySetupService {

	public static final String UNCONFIGURED = "unconfigured";

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private final ObjectMapper json;

	private final Clock clock;

	private final boolean persistenceEnabled;

	private final boolean executionEnabled;

	private final DataQueryMetadataRepository metadata;

	public DataQuerySetupService(DataSource source, ObjectMapper json, Clock clock, boolean persistenceEnabled,
			boolean executionEnabled) {
		this(source, json, clock, persistenceEnabled, executionEnabled, DataQueryReviewPolicy.INDEPENDENT);
	}

	public DataQuerySetupService(DataSource source, ObjectMapper json, Clock clock, boolean persistenceEnabled,
			boolean executionEnabled, DataQueryReviewPolicy reviewPolicy) {
		this.jdbc = new JdbcTemplate(source);
		this.jdbc.setQueryTimeout(5);
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(source));
		this.tx.setTimeout(5);
		this.json = json;
		this.clock = clock;
		this.persistenceEnabled = persistenceEnabled;
		this.executionEnabled = executionEnabled;
		this.metadata = new DataQueryMetadataRepository(source, json, clock, reviewPolicy);
	}

	public record Binding(String dataId, String datasourceId, String revision, String fingerprint, boolean enabled) {
	}

	public record UserOption(String userId, String username, boolean active) {
	}

	private record Source(String dataId, String name, String datasourceId, String datasourceName, String fingerprint,
			String safeEndpoint, String problem) {
	}

	public Map<String, Object> view(ManagementActor actor, String dataId) {
		requireView(actor);
		var source = source(actor, dataId, false);
		var result = new LinkedHashMap<String, Object>();
		result.put("dataId", source.dataId());
		result.put("authorizationModel", DataQueryAccessRepository.MODEL);
		result.put("datasetName", source.name());
		result.put("datasetKey", DataQueryMetadataRepository.DATASET);
		result.put("datasourceId", source.datasourceId());
		result.put("datasourceName", source.datasourceName());
		result.put("connectionFingerprint", source.fingerprint());
		result.put("endpoint", source.safeEndpoint());
		result.put("persistenceEnabled", persistenceEnabled);
		result.put("executionEnabled", executionEnabled);
		result.put("canConfigure", canConfigure(actor));
		result.put("canCreateInitialRule", actor.permissions().contains("knowledge_dataquery_edit")
				&& actor.permissions().contains("knowledge_dataquery_human_review"));
		result.put("definitionDigest", DataQueryMetadataRepository.DEFINITION_DIGEST);
		result.put("definition",
				"仅测试候选：按开始时间、北京时间自然月统计巡视记录；排除 del_flag 非0记录；按公司和部门编码分组、次数降序；使用记录时部门名，缺失/冲突明确提示。del_flag=0 不自动等于正式业务有效口径。该规则只覆盖此定义；原AI问数可查询其他指标，意图说明不是通用SQL白名单，也不是脱敏策略。");
		var blockers = new ArrayList<String>();
		if (source.problem() != null)
			blockers.add(source.problem());
		if (!persistenceEnabled)
			blockers.add("SETUP_STORAGE_DISABLED");
		if (!executionEnabled)
			blockers.add("CONTROLLED_EXECUTION_DISABLED");
		boolean schemaReady = schemaReady();
		result.put("schemaReady", schemaReady);
		if (persistenceEnabled && !schemaReady)
			blockers.add("SETUP_SCHEMA_REQUIRED");
		Binding binding = schemaReady ? binding(actor, false) : null;
		result.put("binding", binding);
		result.put("bindingRevision", binding == null ? UNCONFIGURED : binding.revision());
		boolean boundHere = binding != null && binding.dataId().equals(source.dataId());
		result.put("registeredPilot", boundHere);
		if (binding != null && !boundHere)
			blockers.add("TENANT_PILOT_ALREADY_BOUND");
		if (!boundHere || !binding.enabled())
			blockers.add("DATASOURCE_BINDING_REQUIRED");
		else if (!binding.fingerprint().equals(source.fingerprint()))
			blockers.add("DATASOURCE_BINDING_CHANGED");
		String active = null;
		if (schemaReady && boundHere) {
			try {
				var rule = metadata.optionalCurrent(actor.identity());
				active = rule == null ? null : rule.version();
			}
			catch (DataQueryException ex) {
				blockers.add("METRIC_DEFINITION_UNCONFIRMED");
			}
		}
		result.put("activeRuleVersion", active);
		result.put("queryMode", "AI_SQL");
		result.put("businessRuleRequired", false);
		if (schemaReady) {
			try {
				metadata.requireAiSqlSchema();
			}
			catch (DataQueryException ex) {
				blockers.add(ex.getMessage());
			}
		}
		result.put("blockers", blockers);
		result.put("tlsStatus", "NOT_PROBED");
		result.put("acceptanceStatus", "NOT_VERIFIED");
		return result;
	}

	public Binding saveBinding(ManagementActor actor, String dataId, String expectedRevision,
			String expectedFingerprint, Boolean enabled, String reason) {
		requireConfigure(actor);
		requireStorage();
		reason(reason);
		if (enabled == null)
			fail("INVALID_REQUEST");
		return tx.execute(status -> {
			var currentSource = source(actor, dataId, true);
			Binding before = binding(actor, true);
			if (before != null && !before.dataId().equals(dataId))
				fail("TENANT_PILOT_ALREADY_BOUND");
			if (!Objects.equals(before == null ? UNCONFIGURED : before.revision(), expectedRevision))
				fail("REVISION_CONFLICT");
			// Revocation remains possible after a source changes; re-approval must match
			// the displayed fingerprint.
			if (enabled || before == null) {
				if (currentSource.problem() != null)
					fail(currentSource.problem());
				if (!Objects.equals(currentSource.fingerprint(), expectedFingerprint))
					fail("DATASOURCE_BINDING_CHANGED");
			}
			String revision = UUID.randomUUID().toString();
			String sourceId = !enabled && before != null ? before.datasourceId() : currentSource.datasourceId();
			String fingerprint = !enabled && before != null ? before.fingerprint() : currentSource.fingerprint();
			if (before == null) {
				jdbc.update(
						"INSERT INTO ai_dataquery_binding (tenant_id,dataset_key,data_id,datasource_id,binding_revision,connection_fingerprint,enabled,approved_by,approved_at) VALUES (?,'top.patrol',?,?,?,?,?,?,?)",
						tenant(actor), id(dataId), id(sourceId), revision, fingerprint, enabled ? 1 : 0, user(actor),
						now());
			}
			else {
				jdbc.update(
						"UPDATE ai_dataquery_binding SET datasource_id=?,binding_revision=?,connection_fingerprint=?,enabled=?,approved_by=?,approved_at=? WHERE tenant_id=? AND dataset_key='top.patrol'",
						id(sourceId), revision, fingerprint, enabled ? 1 : 0, user(actor), now(), tenant(actor));
			}
			var after = new Binding(dataId, sourceId, revision, fingerprint, enabled);
			if (before != null && !before.fingerprint().equals(fingerprint)) {
				int revoked = jdbc.update(
						"UPDATE ai_dataquery_grant SET enabled=0,grant_revision=?,approved_by=?,approved_at=? WHERE tenant_id=? AND dataset_key='top.patrol'",
						UUID.randomUUID().toString(), user(actor), now(), tenant(actor));
				int accessRevoked = jdbc.update(
						"UPDATE ai_dataquery_access SET targets_json='[]',valid_until=NULL,access_revision=?,approved_by=?,approved_at=? WHERE tenant_id=? AND dataset_key='top.patrol' AND data_id=?",
						UUID.randomUUID().toString(), user(actor), now(), tenant(actor), id(dataId));
				audit(actor, dataId, "BINDING_SOURCE_CHANGE", before, Map.of("binding", after, "revokedLegacyGrantRows",
						revoked, "revokedAccessPolicies", accessRevoked), reason);
			}
			else
				audit(actor, dataId, "BINDING", before, after, reason);
			return after;
		});
	}

	public List<UserOption> users(ManagementActor actor, String dataId, String search) {
		requireConfigure(actor);
		source(actor, dataId, false);
		if (search == null || search.length() > 80)
			fail("INVALID_REQUEST");
		// No wildcard interpolation, tokens, phone numbers or credentials in lookup
		// results.
		return jdbc.query(
				"SELECT u.user_id,u.username,u.lock_flag FROM sys_user u WHERE EXISTS (SELECT 1 FROM sys_tenant_user tu WHERE tu.user_id=u.user_id AND tu.tenant_id=?) AND u.del_flag='0' AND LOCATE(?,u.username)>0 ORDER BY u.user_id LIMIT 50",
				(rs, n) -> new UserOption(rs.getString(1), rs.getString(2), "0".equals(rs.getString(3))), tenant(actor),
				search);
	}

	public DataQueryAccessRepository.Access access(ManagementActor actor, String dataId) {
		requireConfigure(actor);
		requireStorage();
		source(actor, dataId, false);
		requireBound(actor, dataId, false);
		return new DataQueryAccessRepository(jdbc, json, clock).read(tenant(actor), id(dataId));
	}

	public DataQueryAccessRepository.Access saveAccess(ManagementActor actor, String dataId, String expectedRevision,
			List<DataQueryAccessRepository.Target> targets, String validUntil, String reason) {
		requireConfigure(actor);
		requireStorage();
		reason(reason);
		var checked = DataQueryAccessRepository.checked(targets);
		LocalDateTime expiry = null;
		if (!checked.isEmpty()) {
			try {
				Instant instant = Instant.parse(validUntil);
				if (!instant.isAfter(clock.instant()))
					fail("INVALID_REQUEST");
				expiry = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
			}
			catch (RuntimeException ex) {
				fail("INVALID_REQUEST");
			}
		}
		final LocalDateTime until = expiry;
		return tx.execute(status -> {
			source(actor, dataId, true);
			requireBound(actor, dataId, true);
			var access = new DataQueryAccessRepository(jdbc, json, clock);
			var before = access.read(tenant(actor), id(dataId));
			var after = access.save(tenant(actor), id(dataId), user(actor), expectedRevision, checked, until);
			audit(actor, dataId, "DATASET_ACCESS", before, after, reason);
			return after;
		});
	}

	public Object retiredGrants(ManagementActor actor, String dataId) {
		requireConfigure(actor);
		source(actor, dataId, false);
		throw new DataQueryException("AUTHORIZATION_MODEL_CHANGED");
	}

	private Source source(ManagementActor actor, String dataId, boolean lock) {
		var data = jdbc
			.queryForList("SELECT dataset_name,ds_name FROM ai_data WHERE data_id=? AND tenant_id=? AND del_flag='0'"
					+ (lock ? " FOR UPDATE" : ""), id(dataId), tenant(actor));
		if (data.size() != 1)
			fail("SETUP_DATASET_NOT_FOUND");
		var dataset = data.get(0);
		String name = (String) dataset.get("dataset_name"), dsName = (String) dataset.get("ds_name");
		var sources = jdbc.queryForList(
				"SELECT id,url,username,ds_type FROM ai_datasource WHERE tenant_id=? AND name=? AND del_flag='0'"
						+ (lock ? " FOR UPDATE" : ""),
				tenant(actor), dsName);
		if (sources.size() != 1)
			return new Source(dataId, name, null, dsName, null, null, "SETUP_SOURCE_AMBIGUOUS");
		var row = sources.get(0);
		String sourceId = String.valueOf(row.get("id")), url = (String) row.get("url"),
				type = (String) row.get("ds_type");
		String endpoint;
		try {
			if (!"mysql".equalsIgnoreCase(type))
				fail("DATASOURCE_CONFIGURATION_UNSAFE");
			endpoint = ApprovedDatasourceQueryExecutor.canonicalReaderUrl(url);
		}
		catch (RuntimeException ex) {
			return new Source(dataId, name, sourceId, dsName, null, null, "DATASOURCE_CONFIGURATION_UNSAFE");
		}
		String fingerprint = DataQueryMetadataRepository.connectionFingerprint(id(dataId), id(sourceId), url,
				(String) row.get("username"), type);
		return new Source(dataId, name, sourceId, dsName, fingerprint, endpoint, null);
	}

	private Binding binding(ManagementActor actor, boolean lock) {
		var rows = jdbc.query(
				"SELECT data_id,datasource_id,binding_revision,connection_fingerprint,enabled FROM ai_dataquery_binding WHERE tenant_id=? AND dataset_key='top.patrol'"
						+ (lock ? " FOR UPDATE" : ""),
				(rs, n) -> new Binding(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getInt(5) == 1),
				tenant(actor));
		return rows.isEmpty() ? null : rows.get(0);
	}

	private void requireBound(ManagementActor actor, String dataId, boolean lock) {
		var binding = binding(actor, lock);
		if (binding == null || !binding.dataId().equals(dataId))
			fail("DATASOURCE_BINDING_REQUIRED");
	}

	private void audit(ManagementActor actor, String dataId, String action, Object before, Object after,
			String reason) {
		try {
			jdbc.update(
					"INSERT INTO ai_dataquery_config_event (event_id,tenant_id,dataset_key,data_id,actor_id,action,before_json,after_json,reason,created_at) VALUES (?,?,'top.patrol',?,?,?,?,?,?,?)",
					UUID.randomUUID().toString(), tenant(actor), id(dataId), user(actor), action,
					json.writeValueAsString(before), json.writeValueAsString(after), reason, now());
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
	}

	private boolean schemaReady() {
		try {
			jdbc.queryForList("SELECT event_id FROM ai_dataquery_config_event WHERE 1=0");
			jdbc.queryForList("SELECT binding_revision FROM ai_dataquery_binding WHERE 1=0");
			jdbc.queryForList("SELECT access_revision,targets_json FROM ai_dataquery_access WHERE 1=0");
			return true;
		}
		catch (org.springframework.dao.DataAccessException ex) {
			return false;
		}
	}

	private void requireStorage() {
		if (!persistenceEnabled)
			fail("SETUP_STORAGE_DISABLED");
		if (!schemaReady())
			fail("SETUP_SCHEMA_REQUIRED");
	}

	private static boolean canConfigure(ManagementActor actor) {
		return actor.permissions().contains("knowledge_dataquery_configure")
				&& actor.permissions().contains("knowledge_dataquery_human_review");
	}

	private static void requireView(ManagementActor actor) {
		if (!actor.permissions().contains("knowledge_aiData_view"))
			fail("MANAGEMENT_FORBIDDEN");
	}

	private static void requireConfigure(ManagementActor actor) {
		requireView(actor);
		actor.require("configure", true);
	}

	private static long tenant(ManagementActor actor) {
		return id(actor.identity().tenantId());
	}

	private static long user(ManagementActor actor) {
		return id(actor.identity().userId());
	}

	static long id(String value) {
		try {
			if (value == null || !value.matches("[1-9][0-9]{0,18}"))
				fail("INVALID_REQUEST");
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new DataQueryException("INVALID_REQUEST");
		}
	}

	private static void reason(String reason) {
		if (reason == null || reason.isBlank() || reason.length() > 500)
			fail("INVALID_REQUEST");
	}

	static String utcExpiry(Object value) {
		if (value == null)
			return null;
		LocalDateTime local;
		if (value instanceof java.sql.Timestamp timestamp)
			local = timestamp.toLocalDateTime();
		else if (value instanceof LocalDateTime dateTime)
			local = dateTime;
		else
			throw new DataQueryException("SETUP_METADATA_INVALID");
		return local.toInstant(ZoneOffset.UTC).toString();
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private static void fail(String code) {
		throw new DataQueryException(code);
	}

}
