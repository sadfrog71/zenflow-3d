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
 * A new finite payload type in the existing feedback/version/approval/release lifecycle.
 */
final class DataQueryRelationStore {

	record Payload(DataQueryRelation.Definition definition, String investigationId, String queryId, String ownerId,
			String investigationDigest, String businessBasisType, String businessBasis, String editedBy) {
	}

	record Match(String status, String version, DataQueryRelation.Definition definition) {
	}

	record Execution(DataQuerySqlGuard.Prepared query, Map<String, Object> report) {
	}

	interface Verifier {

		Map<String, Object> verify(Identity owner, DataQueryQueryEvidence.Snapshot snapshot,
				DataQueryRelation.Definition definition, Runnable recheck);

	}

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private final ObjectMapper json;

	private final DataQueryMetadataRepository metadata;

	private final DataQueryQueryEvidence evidence;

	private final Clock clock;

	private final Verifier verifier;

	private final java.util.function.BiConsumer<ManagementActor, String> access;

	DataQueryRelationStore(DataSource source, ObjectMapper json, Clock clock, DataQueryMetadataRepository metadata,
			DataQueryQueryEvidence evidence, Verifier verifier,
			java.util.function.BiConsumer<ManagementActor, String> access) {
		jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(5);
		tx = new TransactionTemplate(new DataSourceTransactionManager(source));
		tx.setTimeout(5);
		this.json = json;
		this.clock = clock;
		this.metadata = metadata;
		this.evidence = evidence;
		this.verifier = verifier;
		this.access = access;
	}

	String create(ManagementActor actor, String feedbackId, Payload payload) {
		actor.require("edit", false);
		access.accept(actor, "edit");
		checked(actor, payload);
		payload.definition().validate();
		return tx.execute(s -> {
			String version = "rel-" + UUID.randomUUID(), key = payload.definition().key(), encoded = encode(payload);
			jdbc.update(
					"INSERT INTO ai_dataquery_rule_version (tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,technical_payload_json,content_digest,feedback_id,base_version,edit_revision,created_by,created_at) VALUES (?,?,?,?,?,'DRAFT',?,?,?,?,1,?,?)",
					actor.identity().tenantId(), key, version, DataQueryRelation.KIND,
					DataQueryMetadataRepository.fingerprint(DataQueryRelation.KIND), encoded,
					DataQueryMetadataRepository.fingerprint(encoded), feedbackId,
					active(actor.identity().tenantId(), key), actor.identity().userId(), now());
			return version;
		});
	}

	void edit(ManagementActor actor, String version, int revision, String type, String basis) {
		actor.require("edit", false);
		access.accept(actor, "edit");
		if (type == null
				|| !Set.of("UNCONFIRMED", "BUSINESS_CODE", "REPORT_SQL", "BUSINESS_CONFIRMATION").contains(type)
				|| basis == null || basis.length() > 1000 || (!"UNCONFIRMED".equals(type) && basis.trim().length() < 8))
			throw new DataQueryException("INVALID_REQUEST");
		tx.executeWithoutResult(s -> {
			var row = locked(actor, version, revision);
			requireState(row, "DRAFT", "VERIFIED", "APPROVED");
			var old = payload(row);
			var p = new Payload(old.definition(), old.investigationId(), old.queryId(), old.ownerId(),
					old.investigationDigest(), type, basis, actor.identity().userId());
			checked(actor, p);
			String encoded = encode(p);
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET technical_payload_json=?,content_digest=?,edit_revision=edit_revision+1,state='DRAFT',regression_report=NULL,regression_digest=NULL,validated_revision=NULL,validated_at=NULL,approved_revision=NULL,approved_by=NULL,approved_at=NULL WHERE tenant_id=? AND rule_version=? AND definition_key=?",
					encoded, DataQueryMetadataRepository.fingerprint(encoded), actor.identity().tenantId(), version,
					DataQueryRelation.KIND);
		});
	}

	String verify(ManagementActor actor, String version, int revision) {
		actor.require("verify", false);
		access.accept(actor, "verify");
		var row = tx.execute(s -> locked(actor, version, revision));
		requireState(row, "DRAFT", "VERIFIED", "APPROVED");
		var p = payload(row);
		var original = checked(actor, p);
		Identity owner = new Identity(actor.identity().tenantId(), p.ownerId());
		var report = verifier.verify(owner, original, p.definition(), () -> {
			access.accept(actor, "verify");
			checked(actor, p);
		});
		String encoded = encode(report);
		return tx.execute(s -> {
			var current = locked(actor, version, revision);
			requireState(current, "DRAFT", "VERIFIED", "APPROVED");
			access.accept(actor, "verify");
			if (!Objects.equals(row.get("state"), current.get("state"))
					|| !Objects.equals(row.get("validated_at"), current.get("validated_at"))
					|| !Objects.equals(row.get("approved_by"), current.get("approved_by")))
				throw new DataQueryException("STATE_CONFLICT");
			if (!row.get("content_digest").equals(current.get("content_digest")))
				throw new DataQueryException("REVISION_CONFLICT");
			checked(actor, p);
			boolean passed = Boolean.TRUE.equals(report.get("passed"));
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET state=?,regression_report=?,regression_digest=?,validated_revision=?,validated_at=?,approved_revision=NULL,approved_by=NULL,approved_at=NULL WHERE tenant_id=? AND rule_version=? AND definition_key=?",
					passed ? "VERIFIED" : "DRAFT", encoded, digest(current, encoded), passed ? revision : null, now(),
					actor.identity().tenantId(), version, DataQueryRelation.KIND);
			return encoded;
		});
	}

	void approve(ManagementActor actor, String version, int revision) {
		actor.require("review", true);
		access.accept(actor, "review");
		tx.executeWithoutResult(s -> {
			var row = locked(actor, version, revision);
			independent(actor, row);
			requireState(row, "VERIFIED");
			checkedReport(actor, row);
			var p = payload(row);
			if ("UNCONFIRMED".equals(p.businessBasisType()) || p.businessBasis() == null
					|| p.businessBasis().trim().length() < 8)
				throw new DataQueryException("RELATION_BUSINESS_CONFIRMATION_REQUIRED");
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET state='APPROVED',approved_revision=?,approved_by=?,approved_at=? WHERE tenant_id=? AND rule_version=? AND definition_key=?",
					revision, actor.identity().userId(), now(), actor.identity().tenantId(), version,
					DataQueryRelation.KIND);
		});
	}

	void publish(ManagementActor actor, String version, int revision, String reason) {
		actor.require("publish", true);
		access.accept(actor, "publish");
		reason(reason);
		tx.executeWithoutResult(s -> {
			var row = locked(actor, version, revision);
			independent(actor, row);
			requireState(row, "APPROVED");
			checkedReport(actor, row);
			if (!Objects.equals(row.get("approved_revision"), revision) || row.get("approved_by") == null)
				throw new DataQueryException("STATE_CONFLICT");
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET state='PUBLISHED',published_by=?,published_at=? WHERE tenant_id=? AND rule_version=? AND definition_key=?",
					actor.identity().userId(), now(), actor.identity().tenantId(), version, DataQueryRelation.KIND);
			switchActive(actor, row.get("dataset_key").toString(), (String) row.get("base_version"), version, "PUBLISH",
					reason);
		});
	}

	void rollback(ManagementActor actor, String expected, String target, String reason) {
		actor.require("publish", true);
		access.accept(actor, "publish");
		reason(reason);
		tx.executeWithoutResult(s -> {
			var from = locked(actor, expected, null);
			requireState(from, "PUBLISHED");
			String key = from.get("dataset_key").toString();
			if (!expected.equals(active(actor.identity().tenantId(), key)))
				throw new DataQueryException("ACTIVE_RULE_CHANGED");
			// Rollback must remain possible after metadata/ACL drift. Actor still needs
			// live dataset access.
			if (metadata.resolve(actor.identity()).source().dataId() != payload(from).definition().dataId())
				throw new DataQueryException("DATASET_NOT_AUTHORIZED");
			if (target == null || target.isBlank()) {
				jdbc.update(
						"UPDATE ai_dataquery_rule_version SET state='DISABLED' WHERE tenant_id=? AND rule_version=? AND definition_key=?",
						actor.identity().tenantId(), expected, DataQueryRelation.KIND);
				event(actor, key, expected, expected, "DISABLE", reason);
			}
			else {
				var to = locked(actor, target, null);
				requireState(to, "PUBLISHED");
				if (!key.equals(to.get("dataset_key")))
					throw new DataQueryException("STATE_CONFLICT");
				var scope = metadata.resolve(actor.identity());
				var d = payload(to).definition();
				if (d.dataId() != scope.source().dataId() || !d.bindingRevision().equals(scope.source().revision())
						|| !d.inventoryDigest().equals(metadata.inventory(actor.identity(), scope.source()).digest()))
					throw new DataQueryException("RELATION_DEPENDENCY_CHANGED");
				switchActive(actor, key, expected, target, "ROLLBACK", reason);
			}
		});
	}

	Map<String, Object> inspect(ManagementActor actor, String version) {
		actor.requireRead();
		access.accept(actor, "read");
		var row = tx.execute(s -> locked(actor, version, null));
		var p = payload(row);
		if (metadata.resolve(actor.identity()).source().dataId() != p.definition().dataId())
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		var out = new LinkedHashMap<String, Object>();
		row.forEach((k, v) -> out.put(k.toLowerCase(Locale.ROOT), v));
		out.remove("technical_payload_json");
		out.put("relation", p);
		out.put("correctionKind", DataQueryRelation.KIND);
		out.put("active", Objects.equals(version, active(actor.identity().tenantId(), p.definition().key()))
				&& "PUBLISHED".equals(row.get("state")));
		return out;
	}

	List<Map<String, Object>> versions(ManagementActor actor) {
		actor.requireRead();
		access.accept(actor, "read");
		return jdbc.queryForList(
				"SELECT rule_version,state,edit_revision,base_version,feedback_id,created_by,created_at,approved_by,published_by,published_at,definition_key FROM ai_dataquery_rule_version WHERE tenant_id=? AND definition_key=? ORDER BY created_at DESC LIMIT 30",
				actor.identity().tenantId(), DataQueryRelation.KIND);
	}

	List<Match> matches(Identity identity, Authorization scope,
			DataQueryMetadataRepository.DatasetInventory inventory) {
		var rows = jdbc.queryForList(
				"SELECT v.* FROM ai_dataquery_rule_active a JOIN ai_dataquery_rule_version v ON v.tenant_id=a.tenant_id AND v.dataset_key=a.dataset_key AND v.rule_version=a.rule_version WHERE a.tenant_id=? AND v.definition_key=? ORDER BY a.dataset_key LIMIT 21",
				identity.tenantId(), DataQueryRelation.KIND);
		if (rows.size() > 20)
			throw new DataQueryException("RELATION_EXPERIENCE_INVALID");
		var matches = new ArrayList<Match>();
		for (var row : rows) {
			var p = payload(row);
			var d = p.definition();
			if (d.dataId() != scope.source().dataId())
				continue;
			String state = String.valueOf(row.get("state")), status;
			if ("DISABLED".equals(state))
				status = "DISABLED";
			else if (!"PUBLISHED".equals(state) || row.get("approved_by") == null
					|| Objects.equals(String.valueOf(row.get("approved_by")), String.valueOf(row.get("created_by")))
					|| Objects.equals(String.valueOf(row.get("approved_by")), p.editedBy())
					|| !Objects.equals(row.get("edit_revision"), row.get("approved_revision"))
					|| !Objects.equals(row.get("edit_revision"), row.get("validated_revision"))
					|| row.get("regression_report") == null
					|| !digest(row, (String) row.get("regression_report")).equals(row.get("regression_digest")))
				throw new DataQueryException("RELATION_EXPERIENCE_INVALID");
			else
				status = d.inventoryDigest().equals(inventory.digest())
						&& d.bindingRevision().equals(scope.source().revision()) ? "PUBLISHED" : "STALE";
			matches.add(new Match(status, row.get("rule_version").toString(), d));
		}
		return List.copyOf(matches);
	}

	Execution apply(DataQueryAiChatService.Context c, DataQuerySqlGuard.Prepared query) {
		DataQuerySqlGuard.Prepared result = query;
		var applications = new ArrayList<Map<String, Object>>();
		for (var match : c.relations()) {
			boolean relevant = result.tables()
				.containsAll(Set.of(match.definition().parentTable(), match.definition().childTable()));
			if (!relevant || !"PUBLISHED".equals(match.status())) {
				applications.add(Map.of("version", match.version(), "status",
						relevant ? match.status() : "NOT_APPLICABLE", "retrieved", true));
				if (relevant && "STALE".equals(match.status()))
					throw new DataQueryException("RELATION_DEPENDENCY_CHANGED");
				continue;
			}
			var applied = DataQueryRelation.apply(result, match.definition());
			boolean withinScope = !"UNSUPPORTED_SHAPE".equals(applied.status());
			if (withinScope)
				try {
					DataQueryRelationProbe.window(result, match.definition());
					var parsed = DataQueryRelation.parse(result, match.definition());
					// COUNT(*) over a join could mean child items. A retrieved
					// parent-grain rule cannot decide the user's metric.
					if ("JOIN_TO_EXISTS".equals(parsed.shape()) && parsed.parent()
						.getSelectList()
						.stream()
						.anyMatch(i -> i.getExpr() instanceof com.alibaba.druid.sql.ast.expr.SQLAggregateExpr a
								&& !DataQueryRelation.column(a.getArguments().get(0), parsed.parentAlias(),
										match.definition().parentKey())))
						withinScope = false;
				}
				catch (DataQueryException unsupported) {
					withinScope = false;
				}
			if (!withinScope) {
				applications.add(Map.of("version", match.version(), "status", "NOT_APPLICABLE", "retrieved", true,
						"unverified", List.of("QUERY_SHAPE_OR_GRAIN_OUTSIDE_PUBLISHED_SCOPE")));
				continue;
			}
			result = applied.query();
			applications.add(Map.of("version", match.version(), "status", applied.status(), "retrieved", true, "checks",
					List.of("DATASET_BINDING", "METADATA_VERSION", "AST_RELATION", "PARENT_ENTITY_GRAIN",
							"PREDICATES_PRESERVED"),
					"unverified",
					List.of("Source business data completeness", "Unrelated joins and authoritative names")));
		}
		return new Execution(result,
				Map.of("status", applications.isEmpty() ? "NONE" : "RECORDED", "applications", applications));
	}

	String prompt(DataQueryAiChatService.Context c) {
		return c.relations().stream().filter(m -> "PUBLISHED".equals(m.status())).map(m -> {
			var d = m.definition();
			return "已发布关联经验 " + m.version() + ": " + d.childTable() + "." + d.childReference() + " → " + d.parentTable()
					+ "." + d.parentKey()
					+ "（字节精确的主键文本匹配；一对多；空引用不匹配）。仅支持按月父记录有/无子项查询，计父记录使用 EXISTS/NOT EXISTS 或明确 COUNT(父记录主键)，不能按展开行数计父记录；JOIN COUNT(*) 的指标身份不明确，后端不会猜测后改成父记录数。其他条件必须保留，禁止据此增加默认状态或删除非空筛选；后端另行检查最终结构。";
		}).reduce("", (a, b) -> a + "\n" + b);
	}

	private DataQueryQueryEvidence.Snapshot checked(ManagementActor actor, Payload p) {
		p.definition().validate();
		var owner = new Identity(actor.identity().tenantId(), p.ownerId());
		var snapshot = evidence.load(owner, p.queryId());
		var query = metadata.findOwnedQuery(owner, p.queryId())
			.orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
		var scope = metadata.resolve(owner);
		if (!scope.equals(snapshot.authorization()) || !query.scopeRevision().equals(scope.revision()))
			throw new DataQueryException("AUTHORIZATION_CHANGED");
		var inv = metadata.inventory(owner, scope.source());
		if (!snapshot.relationVersions().equals(metadata.relationMatches(owner, scope, inv)))
			throw new DataQueryException("RULE_CHANGED");
		if (!inv.digest().equals(snapshot.inventoryDigest()) || !inv.digest().equals(p.definition().inventoryDigest())
				|| !scope.source().revision().equals(p.definition().bindingRevision())
				|| scope.source().dataId() != p.definition().dataId())
			throw new DataQueryException("DATASET_METADATA_CHANGED");
		if (!scope.source().equals(metadata.resolve(actor.identity()).source()))
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		var rule = metadata.optionalCurrent(owner);
		if (!Objects.equals(snapshot.ruleVersion(), rule == null ? null : rule.version()))
			throw new DataQueryException("RULE_CHANGED");
		return snapshot;
	}

	private void checkedReport(ManagementActor actor, Map<String, Object> row) {
		checked(actor, payload(row));
		Object validated = row.get("validated_at");
		LocalDateTime at = validated instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) validated;
		if (at == null || !at.plusMinutes(30).isAfter(now())
				|| !Objects.equals(row.get("edit_revision"), row.get("validated_revision"))
				|| row.get("regression_report") == null
				|| !digest(row, (String) row.get("regression_report")).equals(row.get("regression_digest")))
			throw new DataQueryException("VALIDATION_EXPIRED");
	}

	private void independent(ManagementActor actor, Map<String, Object> row) {
		if (actor.identity().userId().equals(String.valueOf(row.get("created_by")))
				|| actor.identity().userId().equals(payload(row).editedBy()))
			throw new DataQueryException("REVIEWER_MUST_DIFFER");
	}

	private Map<String, Object> locked(ManagementActor actor, String version, Integer revision) {
		var rows = jdbc.queryForList(
				"SELECT * FROM ai_dataquery_rule_version WHERE tenant_id=? AND rule_version=? AND definition_key=? FOR UPDATE",
				actor.identity().tenantId(), version, DataQueryRelation.KIND);
		if (rows.size() != 1)
			throw new DataQueryException("DRAFT_NOT_FOUND");
		var row = rows.get(0);
		if (revision != null && !Objects.equals(revision, row.get("edit_revision")))
			throw new DataQueryException("REVISION_CONFLICT");
		return row;
	}

	private Payload payload(Map<String, Object> row) {
		try {
			String text = (String) row.get("technical_payload_json");
			if (!DataQueryMetadataRepository.fingerprint(text).equals(row.get("content_digest")))
				throw new IllegalArgumentException();
			var p = json.readValue(text, Payload.class);
			p.definition().validate();
			return p;
		}
		catch (Exception e) {
			throw new DataQueryException("RELATION_EXPERIENCE_INVALID");
		}
	}

	private String active(String tenant, String key) {
		var r = jdbc.queryForList(
				"SELECT rule_version FROM ai_dataquery_rule_active WHERE tenant_id=? AND dataset_key=?", String.class,
				tenant, key);
		return r.isEmpty() ? null : r.get(0);
	}

	private void switchActive(ManagementActor actor, String key, String from, String to, String action, String reason) {
		if (Objects.equals(from, to))
			throw new DataQueryException("STATE_CONFLICT");
		if (from == null) {
			try {
				jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES (?,?,?)", actor.identity().tenantId(), key,
						to);
			}
			catch (org.springframework.dao.DuplicateKeyException e) {
				throw new DataQueryException("ACTIVE_RULE_CHANGED");
			}
		}
		else if (jdbc.update(
				"UPDATE ai_dataquery_rule_active SET rule_version=? WHERE tenant_id=? AND dataset_key=? AND rule_version=?",
				to, actor.identity().tenantId(), key, from) != 1)
			throw new DataQueryException("ACTIVE_RULE_CHANGED");
		event(actor, key, from == null ? "" : from, to, action, reason);
	}

	private void event(ManagementActor actor, String key, String from, String to, String action, String reason) {
		jdbc.update("INSERT INTO ai_dataquery_release_event VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(),
				actor.identity().tenantId(), key, actor.identity().userId(), action, from, to, reason, now());
	}

	private static void requireState(Map<String, Object> row, String... states) {
		if (!Arrays.asList(states).contains(row.get("state")))
			throw new DataQueryException("STATE_CONFLICT");
	}

	private static void reason(String s) {
		if (s == null || s.isBlank() || s.length() > 500)
			throw new DataQueryException("INVALID_REQUEST");
	}

	private String digest(Map<String, Object> row, String report) {
		return DataQueryMetadataRepository.fingerprint(String.valueOf(row.get("content_digest")),
				String.valueOf(row.get("edit_revision")), String.valueOf(row.get("base_version")), report);
	}

	private String encode(Object v) {
		try {
			return json.writeValueAsString(v);
		}
		catch (Exception e) {
			throw new DataQueryException("INVALID_REQUEST");
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

}
