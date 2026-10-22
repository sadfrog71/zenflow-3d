package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.common.security.component.ApiTokenValidator;
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import com.deepdimen.matrix.knowledge.entity.AiDataFieldEntity;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDataFieldMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDataMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDataTableMapper;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import com.deepdimen.matrix.knowledge.support.util.SqlSafetyValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.anyline.entity.DataRow;
import org.anyline.entity.DataSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dify Chat2BI 数据查询控制器
 * <p>
 * 提供数据集 Schema 查询和 SQL 执行两个 API，供 Dify Agent 通过自定义工具调用。 支持双模式鉴权：有 Bearer Token
 * 时验证并设置租户上下文，无 Token 时跳过租户过滤（Dify 工具免认证场景）。
 *
 * @author matrix
 * @date 2026/04/27
 */
@Slf4j
@RestController
@ConditionalOnBean(ApiTokenValidator.class)
@RequiredArgsConstructor
@RequestMapping("/external-data")
@Tag(description = "external-data", name = "Dify Chat2BI数据查询")
public class DifyDataQueryController {

	private final AiDataMapper aiDataMapper;

	private final AiDataTableMapper aiDataTableMapper;

	private final AiDataFieldMapper aiDataFieldMapper;

	private final AiDatasourceService aiDatasourceService;

	private final ApiTokenValidator apiTokenValidator;

	private final com.deepdimen.matrix.knowledge.support.dataquery.DataQueryPilotGate dataQueryPilotGate;

	@Inner(value = false)
	@Operation(summary = "获取数据集表结构", description = "根据数据集ID返回关联的表名、字段名、字段类型和业务注释")
	@PostMapping("/schema")
	public ResponseEntity<?> schema(@RequestBody(required = false) Map<String, Object> request,
			HttpServletRequest httpRequest) {
		ResponseEntity<?> authError = authenticate(httpRequest);
		if (authError != null) {
			return authError;
		}

		try {
			if (request == null || request.get("dataset_id") == null) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2001, "error_msg", "dataset_id is required"));
			}

			Long datasetId;
			try {
				datasetId = Long.parseLong(request.get("dataset_id").toString());
			}
			catch (NumberFormatException e) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2001, "error_msg", "Invalid dataset_id format"));
			}

			AiDataEntity dataEntity = aiDataMapper.selectById(datasetId);
			if (dataEntity == null) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2002, "error_msg", "Dataset not found: " + datasetId));
			}

			String[] tableNames = dataEntity.getTableName();
			if (tableNames == null || tableNames.length == 0) {
				return ResponseEntity
					.ok(Map.of("dataset_name", StrUtil.nullToEmpty(dataEntity.getDatasetName()), "datasource_name",
							StrUtil.nullToEmpty(dataEntity.getDsName()), "ds_type", "", "tables", List.of()));
			}

			List<AiDataTableEntity> tableEntities = aiDataTableMapper
				.selectList(Wrappers.<AiDataTableEntity>lambdaQuery()
					.eq(AiDataTableEntity::getDsName, dataEntity.getDsName())
					.in(AiDataTableEntity::getTableName, CollUtil.toList(tableNames)));

			List<Long> tableIds = tableEntities.stream()
				.map(AiDataTableEntity::getTableId)
				.collect(Collectors.toList());

			Map<Long, List<AiDataFieldEntity>> fieldsByTableId = Collections.emptyMap();
			if (!tableIds.isEmpty()) {
				fieldsByTableId = aiDataFieldMapper
					.selectList(Wrappers.<AiDataFieldEntity>lambdaQuery().in(AiDataFieldEntity::getTableId, tableIds))
					.stream()
					.collect(Collectors.groupingBy(AiDataFieldEntity::getTableId));
			}

			String dsType = tableEntities.isEmpty() ? "" : StrUtil.nullToEmpty(tableEntities.get(0).getDsType());

			List<Map<String, Object>> tables = new ArrayList<>();
			for (AiDataTableEntity table : tableEntities) {
				List<AiDataFieldEntity> fields = fieldsByTableId.getOrDefault(table.getTableId(), List.of());

				List<Map<String, String>> fieldList = fields.stream()
					.map(f -> Map.of("name", StrUtil.nullToEmpty(f.getFieldName()), "type",
							StrUtil.nullToEmpty(f.getFieldType()), "comment", StrUtil.nullToEmpty(f.getFieldComment()),
							"virtual_comment", StrUtil.nullToEmpty(f.getVirtualComment())))
					.collect(Collectors.toList());

				Map<String, Object> tableMap = new LinkedHashMap<>();
				tableMap.put("table_name", table.getTableName());
				tableMap.put("comment", StrUtil.nullToEmpty(table.getTableComment()));
				tableMap.put("virtual_comment", StrUtil.nullToEmpty(table.getVirtualComment()));
				tableMap.put("fields", fieldList);
				tables.add(tableMap);
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("dataset_name", StrUtil.nullToEmpty(dataEntity.getDatasetName()));
			result.put("datasource_name", StrUtil.nullToEmpty(dataEntity.getDsName()));
			result.put("ds_type", dsType);
			result.put("tables", tables);
			return ResponseEntity.ok(result);
		}
		catch (Exception e) {
			log.error("Chat2BI schema: 查询异常", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error_code", 5000, "error_msg", "Internal error"));
		}
		finally {
			clearAuthContext();
		}
	}

	@Inner(value = false)
	@Operation(summary = "执行SQL查询", description = "在数据集关联的数据源上执行只读SQL查询，仅支持SELECT，最多返回500行")
	@PostMapping("/query")
	public ResponseEntity<?> query(@RequestBody(required = false) Map<String, Object> request,
			HttpServletRequest httpRequest) {
		ResponseEntity<?> authError = authenticate(httpRequest);
		if (authError != null) {
			return authError;
		}

		try {
			if (request == null || request.get("dataset_id") == null || request.get("sql") == null) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2001, "error_msg", "dataset_id and sql are required"));
			}

			Long datasetId;
			try {
				datasetId = Long.parseLong(request.get("dataset_id").toString());
			}
			catch (NumberFormatException e) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2001, "error_msg", "Invalid dataset_id format"));
			}

			String sql = request.get("sql").toString().trim();

			String validateError = SqlSafetyValidator.validate(sql);
			if (validateError != null) {
				return ResponseEntity.badRequest().body(Map.of("error_code", 3001, "error_msg", validateError));
			}

			try {
				dataQueryPilotGate.rejectLegacy(datasetId);
			}
			catch (com.deepdimen.matrix.knowledge.support.dataquery.DataQueryException e) {
				return ResponseEntity.status(403)
					.body(Map.of("error_code", e.getMessage(), "error_msg",
							"Use controlled question path or review pilot inventory"));
			}

			AiDataEntity dataEntity = aiDataMapper.selectById(datasetId);
			if (dataEntity == null) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2002, "error_msg", "Dataset not found: " + datasetId));
			}

			String[] tableNames = dataEntity.getTableName();
			if (tableNames == null || tableNames.length == 0) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 3003, "error_msg", "Dataset has no associated tables"));
			}

			Set<String> allowedTables = new HashSet<>(Arrays.asList(tableNames));
			String tableError = SqlSafetyValidator.validateTables(sql, allowedTables);
			if (tableError != null) {
				return ResponseEntity.badRequest().body(Map.of("error_code", 3002, "error_msg", tableError));
			}

			sql = SqlSafetyValidator.enforceLimit(sql);

			R<DataSet> queryResult = aiDatasourceService.executeQuery(dataEntity.getDsName(), sql);
			if (queryResult.getCode() != 0 || queryResult.getData() == null) {
				String msg = queryResult.getMsg() != null ? queryResult.getMsg() : "Query execution failed";
				return ResponseEntity.ok(Map.of("success", false, "error", msg, "columns", List.of(), "rows", List.of(),
						"row_count", 0, "truncated", false));
			}

			DataSet dataSet = queryResult.getData();
			List<DataRow> rows = dataSet.getRows();
			if (rows == null || rows.isEmpty()) {
				return ResponseEntity.ok(Map.of("success", true, "columns", List.of(), "rows", List.of(), "row_count",
						0, "truncated", false));
			}

			List<String> columns = rows.get(0).keys();
			boolean truncated = rows.size() >= 501;
			int returnSize = truncated ? 500 : rows.size();

			List<List<Object>> rowData = new ArrayList<>();
			for (int i = 0; i < returnSize; i++) {
				DataRow row = rows.get(i);
				List<Object> values = new ArrayList<>();
				for (String col : columns) {
					values.add(row.get(col));
				}
				rowData.add(values);
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("columns", columns);
			result.put("rows", rowData);
			result.put("row_count", returnSize);
			result.put("truncated", truncated);
			return ResponseEntity.ok(result);
		}
		catch (Exception e) {
			log.error("Chat2BI query: 执行异常", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error_code", 5000, "error_msg", "Internal error"));
		}
		finally {
			clearAuthContext();
		}
	}

	/**
	 * 统一鉴权，支持双模式：
	 * <ul>
	 * <li>有 Bearer Token: 验证 Token 并设置租户上下文（多租户隔离）</li>
	 * <li>无 Authorization 头: 跳过租户过滤（dataset_id 为全局唯一主键，适用于 Dify 工具免认证场景）</li>
	 * <li>有 Bearer Token 但无效: 返回 403</li>
	 * </ul>
	 * @return 错误响应，null 表示鉴权通过
	 */
	private ResponseEntity<?> authenticate(HttpServletRequest httpRequest) {
		String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);

		if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
			TenantContextHolder.setTenantSkip();
			return null;
		}

		String tokenValue = authHeader.substring(7).trim();
		ApiTokenValidator.ApiTokenInfo tokenInfo = apiTokenValidator.findValidToken(tokenValue);
		if (tokenInfo == null) {
			log.warn("Chat2BI: API Token 验证失败, token前缀: {}", StrUtil.subPre(tokenValue, 8));
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error_code", 1002, "error_msg", "API Token invalid or expired"));
		}

		apiTokenValidator.setTenantContext(tokenInfo.getTenantId());

		try {
			apiTokenValidator.updateLastUsedTime(tokenInfo.getId());
		}
		catch (Exception e) {
			log.warn("更新 API Token 最后使用时间失败, id: {}", tokenInfo.getId(), e);
		}

		return null;
	}

	private void clearAuthContext() {
		apiTokenValidator.clearTenantContext();
		TenantContextHolder.unsetTenantSkip();
	}

}
