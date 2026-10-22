package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySetupService.id;

/**
 * Dataset access, not source-row ownership. Reuses live MATRIX organization membership. A
 * policy is one revisioned snapshot; an empty snapshot denies everyone and retains its
 * revision. No legacy TOP grants, implicit tenant/company mapping or public default.
 */
final class DataQueryAccessRepository {

	static final String MODEL = "MATRIX_DATASET_ACL_V1";

	record Target(String type, String targetId) implements DataQueryCorrectionController.StrictRequest {
	}

	record Option(String type, String targetId, String name, boolean active) {
	}

	record Access(String dataId, String authorizationModel, String revision, String validUntil, List<Option> targets) {
	}

	private record Policy(String revision, String targetsJson, LocalDateTime validUntil, Long approvedBy,
			LocalDateTime approvedAt) {
	}

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	DataQueryAccessRepository(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	static List<Target> checked(List<Target> targets) {
		if (targets == null || targets.size() > 500)
			fail("INVALID_REQUEST");
		for (var target : targets) {
			if (target == null || !Set.of("dept", "role", "user").contains(target.type() == null ? "" : target.type()))
				fail("INVALID_REQUEST");
			id(target.targetId());
		}
		if (new HashSet<>(targets).size() != targets.size())
			fail("INVALID_REQUEST");
		return targets.stream().sorted(Comparator.comparing(Target::type).thenComparing(Target::targetId)).toList();
	}

	private List<Target> decode(String value) {
		try {
			return checked(Arrays.asList(json.readValue(value, Target[].class)));
		}
		catch (Exception ex) {
			throw new DataQueryException("SETUP_METADATA_INVALID");
		}
	}

	private Policy policy(long tenant, long dataId) {
		var rows = jdbc.query(
				"SELECT access_revision,targets_json,valid_until,approved_by,approved_at FROM ai_dataquery_access WHERE tenant_id=? AND dataset_key='top.patrol' AND data_id=?",
				(rs, n) -> new Policy(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDateTime.class),
						rs.getObject(4, Long.class), rs.getObject(5, LocalDateTime.class)),
				tenant, dataId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	Access read(long tenant, long dataId) {
		var policy = policy(tenant, dataId);
		if (policy == null)
			return new Access(Long.toString(dataId), MODEL, "unconfigured", null, List.of());
		return new Access(Long.toString(dataId), MODEL, policy.revision(),
				policy.validUntil() == null ? null : policy.validUntil().toInstant(ZoneOffset.UTC).toString(),
				options(tenant, decode(policy.targetsJson())));
	}

	private Option option(long tenant, Target target) {
		return options(tenant, List.of(target)).get(0);
	}

	private List<Option> options(long tenant, List<Target> targets) {
		Map<Target, Option> found = new HashMap<>();
		// At most three bounded inventory queries, including at the 500-target limit.
		// Only placeholders vary; request values never become SQL identifiers.
		for (String type : List.of("dept", "role", "user")) {
			var ids = targets.stream().filter(t -> type.equals(t.type())).map(t -> id(t.targetId())).toList();
			if (ids.isEmpty())
				continue;
			String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
			String sql = switch (type) {
				case "dept" ->
					"SELECT dept_id,name FROM sys_dept WHERE tenant_id=? AND del_flag='0' AND dept_id IN (%s)";
				case "role" ->
					"SELECT role_id,role_name FROM sys_role WHERE tenant_id=? AND del_flag='0' AND role_id IN (%s)";
				case "user" ->
					"SELECT u.user_id,u.username FROM sys_user u WHERE EXISTS (SELECT 1 FROM sys_tenant_user tu WHERE tu.user_id=u.user_id AND tu.tenant_id=?) AND u.del_flag='0' AND u.lock_flag='0' AND u.user_id IN (%s)";
				default -> throw new IllegalStateException();
			};
			List<Object> args = new ArrayList<>();
			args.add(tenant);
			args.addAll(ids);
			var rows = jdbc.query(sql.formatted(placeholders),
					(rs, n) -> new Option(type, rs.getString(1), rs.getString(2), true), args.toArray());
			for (var row : rows)
				found.put(new Target(type, row.targetId()), row);
		}
		return targets.stream()
			.map(t -> found.getOrDefault(t, new Option(t.type(), t.targetId(), "已失效或不在当前租户", false)))
			.toList();
	}

	Access save(long tenant, long dataId, long actor, String expectedRevision, List<Target> requested,
			LocalDateTime until) {
		var targets = checked(requested);
		var before = policy(tenant, dataId);
		if (before != null)
			decode(before.targetsJson());
		String beforeRevision = before == null ? "unconfigured" : before.revision();
		if (!Objects.equals(beforeRevision, expectedRevision))
			fail("REVISION_CONFLICT");
		if (options(tenant, targets).stream().anyMatch(t -> !t.active()))
			fail("SETUP_TARGET_NOT_FOUND");
		String encoded;
		try {
			encoded = json.writeValueAsString(targets);
		}
		catch (Exception ex) {
			throw new DataQueryException("SETUP_METADATA_INVALID");
		}
		String revision = UUID.randomUUID().toString();
		var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		if ("unconfigured".equals(beforeRevision))
			jdbc.update(
					"INSERT INTO ai_dataquery_access (tenant_id,dataset_key,data_id,access_revision,targets_json,valid_until,approved_by,approved_at) VALUES (?,'top.patrol',?,?,?,?,?,?)",
					tenant, dataId, revision, encoded, until, actor, now);
		else if (jdbc.update(
				"UPDATE ai_dataquery_access SET access_revision=?,targets_json=?,valid_until=?,approved_by=?,approved_at=? WHERE tenant_id=? AND dataset_key='top.patrol' AND data_id=? AND access_revision=?",
				revision, encoded, until, actor, now, tenant, dataId, expectedRevision) != 1)
			fail("REVISION_CONFLICT");
		return read(tenant, dataId);
	}

	Authorization resolve(Identity identity, SourceBinding binding) {
		long tenant = id(identity.tenantId()), user = id(identity.userId());
		if (!option(tenant, new Target("user", identity.userId())).active())
			fail("DATASET_NOT_AUTHORIZED");
		var policy = policy(tenant, binding.dataId());
		var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		if (policy == null || policy.revision() == null || policy.revision().isBlank() || policy.approvedBy() == null
				|| policy.approvedBy() <= 0 || policy.approvedAt() == null || policy.approvedAt().isAfter(now)
				|| policy.validUntil() == null || !policy.validUntil().isAfter(now))
			fail("DATASET_NOT_AUTHORIZED");
		var targets = decode(policy.targetsJson());
		var depts = memberships(tenant, user, targets, "dept");
		var roles = memberships(tenant, user, targets, "role");
		var matched = targets.stream().filter(t -> switch (t.type()) {
			case "user" -> t.targetId().equals(identity.userId());
			case "dept" -> depts.contains(id(t.targetId()));
			case "role" -> roles.contains(id(t.targetId()));
			default -> false;
		}).toList();
		if (matched.isEmpty())
			fail("DATASET_NOT_AUTHORIZED");
		// Both policy content and live matching membership enter the plan fingerprint.
		// Changing either invalidates the handle even when another grant still matches.
		String revision = DataQueryMetadataRepository.fingerprint(MODEL, identity.tenantId(), identity.userId(),
				binding.toString(), policy.revision(), policy.targetsJson(), policy.validUntil().toString(),
				policy.approvedBy().toString(), policy.approvedAt().toString(), matched.toString());
		return new Authorization(revision, binding);
	}

	private Set<Long> memberships(long tenant, long user, List<Target> targets, String type) {
		var ids = targets.stream().filter(t -> type.equals(t.type())).map(t -> id(t.targetId())).toList();
		if (ids.isEmpty())
			return Set.of();
		String sql = switch (type) {
			case "dept" ->
				"SELECT ud.dept_id FROM sys_user_dept ud JOIN sys_dept d ON d.dept_id=ud.dept_id AND d.tenant_id=ud.tenant_id WHERE ud.tenant_id=? AND ud.user_id=? AND d.del_flag='0' AND ud.dept_id IN (%s)";
			case "role" ->
				"SELECT ur.role_id FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id AND r.tenant_id=ur.tenant_id WHERE ur.tenant_id=? AND ur.user_id=? AND r.del_flag='0' AND ur.role_id IN (%s)";
			default -> throw new IllegalStateException();
		};
		List<Object> args = new ArrayList<>(List.of(tenant, user));
		args.addAll(ids);
		return new HashSet<>(jdbc.queryForList(sql.formatted(String.join(",", Collections.nCopies(ids.size(), "?"))),
				Long.class, args.toArray()));
	}

	private static void fail(String code) {
		throw new DataQueryException(code);
	}

}
