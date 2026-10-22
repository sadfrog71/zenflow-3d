package com.deepdimen.matrix.knowledge.service.impl;

import java.util.*;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Both management entrances update the existing ai_data.terminology_ids, not a second
 * binding store.
 */
@Service
public class AiTerminologyDatasetBindings {

	public record Dataset(String dataId, String datasetName) {
	}

	private final JdbcTemplate jdbc;

	private final PermissionService permissions;

	public AiTerminologyDatasetBindings(JdbcTemplate jdbc, PermissionService permissions) {
		this.jdbc = jdbc;
		this.permissions = permissions;
	}

	public boolean canRead() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
				&& TenantContextHolder.getTenantId() != null && permissions.hasPermission("knowledge_aiData_view");
	}

	private Long tenant(boolean edit) {
		if (!canRead() || (edit && !permissions.hasPermission("knowledge_aiData_edit")))
			throw new AccessDeniedException("需要数据集查看／编辑权限才能维护适用数据集");
		return TenantContextHolder.getTenantId();
	}

	public List<Dataset> options() {
		return jdbc.query(
				"SELECT data_id,dataset_name FROM ai_data WHERE tenant_id=? AND del_flag='0' ORDER BY data_id",
				(rs, i) -> new Dataset(rs.getString(1), rs.getString(2)), tenant(false));
	}

	public List<Long> selected(Long termId) {
		if (!canRead())
			return null;
		return rows(tenant(false), false).stream()
			.filter(row -> ids((String) row.get("terminology_ids")).contains(termId))
			.map(row -> ((Number) row.get("data_id")).longValue())
			.sorted()
			.toList();
	}

	@Transactional(rollbackFor = Exception.class)
	public void replace(Long termId, List<Long> requested, List<Long> expected) {
		if (requested == null)
			return; // Old clients did not submit associations: preserve them.
		Long tenant = tenant(true);
		var wanted = checked(requested);
		var previous = checked(expected);
		var rows = rows(tenant, true);
		var available = new TreeSet<Long>();
		var current = new TreeSet<Long>();
		for (var row : rows) {
			long id = ((Number) row.get("data_id")).longValue();
			available.add(id);
			if (ids((String) row.get("terminology_ids")).contains(termId))
				current.add(id);
		}
		if (!available.containsAll(wanted))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "适用数据集不存在、已删除或不属于当前租户");
		if (!current.equals(previous))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "术语绑定已被另一入口修改，请重新打开后再保存");
		for (var row : rows) {
			long id = ((Number) row.get("data_id")).longValue();
			var values = ids((String) row.get("terminology_ids"));
			boolean changed = wanted.contains(id) ? values.add(termId) : values.remove(termId);
			if (changed) {
				String encoded = values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
				jdbc.update(
						"UPDATE ai_data SET terminology_ids=?,update_time=CURRENT_TIMESTAMP,update_by=? WHERE tenant_id=? AND data_id=? AND del_flag='0'",
						encoded, SecurityContextHolder.getContext().getAuthentication().getName(), tenant, id);
			}
		}
	}

	public List<Long> termsForDataset(Long dataId) {
		var row = jdbc.queryForList(
				"SELECT terminology_ids FROM ai_data WHERE tenant_id=? AND data_id=? AND del_flag='0'", tenant(false),
				dataId);
		if (row.size() != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在");
		return List.copyOf(ids((String) row.get(0).get("terminology_ids")));
	}

	public void checkDatasetUpdate(com.deepdimen.matrix.knowledge.entity.AiDataEntity data) {
		if (data.getTerminologyIds() == null)
			return;
		var tenant = tenant(false);
		var requested = checked(Arrays.asList(data.getTerminologyIds()));
		var valid = new HashSet<>(jdbc.queryForList(
				"SELECT term_id FROM ai_terminology WHERE tenant_id=? AND del_flag='0' AND pid IS NULL", Long.class,
				tenant));
		if (!valid.containsAll(requested))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能选择当前租户存在的顶级术语");
		if (data.getDataId() != null) {
			var row = jdbc.queryForList(
					"SELECT terminology_ids FROM ai_data WHERE tenant_id=? AND data_id=? AND del_flag='0' FOR UPDATE",
					tenant, data.getDataId());
			if (row.size() != 1)
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在");
			var current = ids((String) row.get(0).get("terminology_ids"));
			if (data.getExpectedTerminologyIds() == null ? !current.equals(requested)
					: !current.equals(checked(data.getExpectedTerminologyIds())))
				throw new ResponseStatusException(HttpStatus.CONFLICT, "术语绑定已变更或缺少快照，请刷新后重新打开数据集再保存");
		}
	}

	private List<Map<String, Object>> rows(Long tenant, boolean lock) {
		return jdbc.queryForList(
				"SELECT data_id,terminology_ids FROM ai_data WHERE tenant_id=? AND del_flag='0' ORDER BY data_id"
						+ (lock ? " FOR UPDATE" : ""),
				tenant);
	}

	private static Set<Long> checked(List<Long> values) {
		if (values == null || values.size() > 2000 || values.stream().anyMatch(v -> v == null || v <= 0)
				|| new HashSet<>(values).size() != values.size())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据集选择无效，请重新加载");
		return new TreeSet<>(values);
	}

	private static Set<Long> ids(String value) {
		var out = new LinkedHashSet<Long>();
		if (value == null || value.isBlank())
			return out;
		for (String item : value.split(",", -1)) {
			try {
				long id = Long.parseLong(item.trim());
				if (id <= 0 || !out.add(id))
					throw new NumberFormatException();
			}
			catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "已有术语绑定格式异常，未覆盖原配置");
			}
		}
		return out;
	}

}
