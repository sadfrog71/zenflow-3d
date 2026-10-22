package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import org.springframework.http.ResponseEntity;

/** Shared transport for direct HTTP and the existing chat stream. */
public final class DataQueryResponses {

	public static Map<String, Object> success(ControlledQueryService.QueryResult result) {
		var response = envelope("SUCCEEDED", "OK", result.rows().isEmpty() ? "授权范围内无记录" : "查询完成");
		response.put("queryId", result.queryId());
		response.put("ruleVersion", result.ruleVersion());
		response.put("columns", List.of(Map.of("key", "departmentName", "label", "部门（记录时名称）", "type", "string"),
				Map.of("key", "validPatrolCount", "label", "有效巡视次数", "type", "integer")));
		List<Map<String, Object>> rows = new ArrayList<>();
		long conflicts = 0;
		long missingNames = 0;
		long missingIds = 0, missingCompanies = 0;
		for (var row : result.rows()) {
			Map<String, Object> mapped = new LinkedHashMap<>();
			// Query-local opaque key: no physical company/department identifiers in the
			// transport.
			mapped.put("departmentKey", UUID.randomUUID().toString());
			mapped.put("departmentName", row.departmentName());
			// Preserve numeric metrics under MATRIX's global Long-as-string ID
			// serializer.
			mapped.put("validPatrolCount", java.math.BigInteger.valueOf(row.count()));
			mapped.put("nameVariantCount", row.nameVariantCount());
			mapped.put("missingNameCount", java.math.BigInteger.valueOf(row.missingNameCount()));
			rows.add(mapped);
			if (row.nameVariantCount() > 1)
				conflicts++;
			missingNames += row.missingNameCount();
			if (missingBusinessId(row.departmentId()))
				missingIds += row.count();
			if (missingBusinessId(row.companyId()))
				missingCompanies += row.count();
		}
		response.put("rows", rows);
		var candidate = result.definition();
		response.put("queryDefinition",
				Map.of("dataset", candidate.dataset(), "metric", candidate.metric(), "dimension", candidate.dimension(),
						"from", candidate.from().toString(), "to", candidate.to().toString(), "timezone",
						candidate.timezone(), "order", candidate.order(), "scope", "authorized_dataset",
						"departmentNamePolicy", "record-time; conflicts remain null"));
		response.put("quality",
				Map.of("nameConflictGroups", java.math.BigInteger.valueOf(conflicts), "missingNameCount",
						java.math.BigInteger.valueOf(missingNames), "missingDepartmentIds",
						java.math.BigInteger.valueOf(missingIds), "missingCompanyIds",
						java.math.BigInteger.valueOf(missingCompanies), "authoritativeCompanyNamesAvailable", false));
		List<String> warnings = new ArrayList<>(List.of("AUTHORITATIVE_COMPANY_NAMES_UNAVAILABLE"));
		response.put("authorizationModel", DataQueryAccessRepository.MODEL);
		if (missingIds > 0)
			warnings.add("DEPARTMENT_ID_MISSING");
		if (missingCompanies > 0)
			warnings.add("COMPANY_ID_MISSING");
		if (conflicts > 0)
			warnings.add("DEPARTMENT_NAME_CONFLICT");
		if (missingNames > 0)
			warnings.add("DEPARTMENT_NAME_MISSING");
		response.put("warnings", warnings);
		return response;
	}

	private static boolean missingBusinessId(String value) {
		return value == null || value.isBlank()
				|| Set.of("null", "none", "undefined").contains(value.trim().toLowerCase(java.util.Locale.ROOT));
	}

	public static boolean isClarification(String code) {
		return Set
			.of("INTENT_NOT_SUPPORTED", "QUERY_YEAR_REQUIRED", "QUERY_DATE_INVALID", "AI_QUERY_NEEDS_CLARIFICATION",
					"ASSISTANCE_CLARIFICATION", "RESULT_CONTEXT_UNAVAILABLE", "FOLLOWUP_CONTEXT_UNAVAILABLE")
			.contains(code == null ? "" : code);
	}

	public static Map<String, Object> clarification(String code, String question) {
		String message = switch (code) {
			case "QUERY_DETAILS_UNAVAILABLE" -> "查询详情证据缺失或不一致，未展示SQL；不会重新执行旧查询。";
			case "SQL_EXAMPLE_EXECUTION_NOT_CONFIGURED" -> "此数据集仅能管理SQL示例草稿，尚未接入受控验证和问数执行，不能审核发布。";
			case "SQL_EXAMPLE_DATASET_LIMIT" -> "可管理数据集超过目录上限，请联系维护人员调整目录分页。";
			case "SQL_EXAMPLE_SELECTION_INVALID" -> "示例候选集合格式无效，请刷新后重新选择。";
			case "SQL_EXAMPLE_NOT_SELECTABLE" -> "只能选择当前数据集已发布且依赖有效的示例；请重新核对选择。";
			case "SQL_EXAMPLE_SCOPE_MISMATCH" -> "该示例设置不属于当前获准的数据集，未切换数据权限。";
			case "SQL_EXAMPLE_CHANGED", "SQL_EXAMPLE_DEPENDENCY_CHANGED" -> "SQL示例或依赖已经变化，本次不再沿用；请重新提问或更新草稿并验证。";
			case "SQL_EXAMPLE_CASES_REQUIRED", "SQL_EXAMPLE_CASES_DISTINCT_REQUIRED",
					"SQL_EXAMPLE_MAIN_CASE_REQUIRED" ->
				"请至少提供两个不同的回归用例，其中一个必须覆盖示例SQL，并填写独立核对的预期列和结果。";
			case "SQL_EXAMPLE_VERIFICATION_REQUIRED" -> "SQL示例尚未通过结果对账，不能审核发布。";
			case "SQL_EXAMPLE_SEED_UNAVAILABLE", "SQL_EXAMPLE_SOURCE_UNAVAILABLE" ->
				"该查询没有可用的本人成功执行证据；可手工新建草稿，不能把旧回答当成已验证示例。";
			case "SQL_EXAMPLE_INVALID" -> "SQL示例完整性校验失败，未使用该示例。";
			case "SQL_EXAMPLE_TOO_LARGE", "SQL_EXAMPLE_LIMIT" -> "SQL示例超过内容或数量上限，请缩小范围。";
			case "INVESTIGATION_DISABLED" -> "AI业务调查尚未启用；普通问数和已有反馈不受影响。需授权部署、配置证据密钥后启用。";
			case "INVESTIGATION_EVIDENCE_UNAVAILABLE" -> "原查询没有可用的加密执行快照，或证据已过期。请以原范围重新提问并反馈；新查询属于重建，不能冒称复现。";
			case "INVESTIGATION_MODEL_UNAVAILABLE" -> "原会话对应租户模型不可用或调用超时，未降级使用系统模型。";
			case "INVESTIGATION_SCOPE_UNSUPPORTED", "RELATION_SHAPE_UNVERIFIED" ->
				"本次关联结构或时间范围超出已验证范围，未静默改写或放宽筛选。请补充证据或转人工处理。";
			case "RELATION_DEPENDENCY_CHANGED" -> "关联经验的元数据或数据源绑定已变化，本次未沿用，需重新调查验证。";
			case "RELATION_BUSINESS_CONFIRMATION_REQUIRED" -> "试查通过不等于业务关系正确。请补充业务代码、既有报表或业务确认依据，再由独立人工审核。";
			case "INVESTIGATION_NOT_READY", "INVESTIGATION_EVIDENCE_INSUFFICIENT" -> "调查证据不足，尚不能准备可验证的修正，请查看证据与待确认事项。";
			case "INVESTIGATION_BUDGET_EXCEEDED" -> "调查达到时间或查询预算，已停止，没有放宽资源限制。";
			case "VALIDATION_EXPIRED" -> "关联修正验证证据已过期或草稿已改变，请重新验证。";

			case "QUERY_YEAR_REQUIRED" -> "请补充要查询的年份。选择下方年份后继续查询，无需修改或发布规则。";
			case "QUERY_DATE_INVALID" -> "日期不正确，请提供有效的年份和月份，例如：2026年8月各部门巡视了多少次？";
			default -> "暂不能将这句话确认成当前已发布的部门巡视记录数查询。请提供明确的年份、月份和部门统计要求；若业务用词需要修正，可反馈问题。";
		};
		var response = envelope("NEEDS_CLARIFICATION", code, message);
		if ("QUERY_YEAR_REQUIRED".equals(code))
			response.put("clarification", new PatrolQuestionInterpreter().yearPrompt(question));
		return response;
	}

	public static ResponseEntity<Map<String, Object>> failure(DataQueryException exception) {
		String code = exception.getMessage();
		int httpStatus = switch (code) {
			case "UNAUTHENTICATED" -> 401;
			case "SQL_EXAMPLE_SELECTION_INVALID", "SQL_EXAMPLE_NOT_SELECTABLE", "SQL_EXAMPLE_CASES_REQUIRED",
					"SQL_EXAMPLE_CASES_DISTINCT_REQUIRED", "SQL_EXAMPLE_MAIN_CASE_REQUIRED", "SQL_EXAMPLE_TOO_LARGE",
					"INVALID_REQUEST", "SETUP_SCOPE_TOO_LARGE", "RELATION_DEFINITION_INVALID",
					"INVESTIGATION_TOOL_REJECTED" ->
				400;
			case "SQL_EXAMPLE_SCOPE_MISMATCH", "MANAGEMENT_FORBIDDEN", "CHAT_RECORD_NOT_OWNED", "REVIEWER_MUST_DIFFER",
					"DATASET_NOT_AUTHORIZED" ->
				403;
			case "SETUP_TARGET_NOT_FOUND", "QUERY_NOT_FOUND", "DRAFT_NOT_FOUND", "FEEDBACK_NOT_FOUND",
					"SETUP_DATASET_NOT_FOUND", "SETUP_USER_NOT_FOUND" ->
				404;
			case "VISUALIZATION_CHANGED", "SQL_EXAMPLE_EXECUTION_NOT_CONFIGURED", "SQL_EXAMPLE_CHANGED",
					"SQL_EXAMPLE_DEPENDENCY_CHANGED", "SQL_EXAMPLE_VERIFICATION_REQUIRED",
					"SQL_EXAMPLE_SOURCE_UNAVAILABLE", "IDEMPOTENCY_CONFLICT", "REVISION_CONFLICT", "STATE_CONFLICT",
					"ACTIVE_RULE_CHANGED", "TENANT_PILOT_ALREADY_BOUND", "SETUP_IDENTIFIER_COLLISION",
					"SETUP_SOURCE_AMBIGUOUS", "DATASOURCE_BINDING_CHANGED", "DATASOURCE_BINDING_REQUIRED" ->
				409;
			case "AUTHORIZATION_MODEL_CHANGED" -> 410;
			case "SQL_EXAMPLE_DATASET_LIMIT", "SQL_EXAMPLE_LIMIT", "PLAN_CAPACITY_EXCEEDED", "QUERY_BUSY",
					"INVESTIGATION_BUSY" ->
				429;
			case "QUERY_DETAILS_UNAVAILABLE", "SQL_EXAMPLE_SEED_UNAVAILABLE", "SQL_EXAMPLE_INVALID",
					"INVESTIGATION_DISABLED", "INVESTIGATION_MODEL_UNAVAILABLE", "CONTROLLED_SERVICE_UNAVAILABLE",
					"SERVICE_UNAVAILABLE", "QUERY_FAILED", "QUERY_TIMEOUT", "DATASOURCE_RESOURCE_LIMIT",
					"AUDIT_UNAVAILABLE", "DATASOURCE_NOT_CONFIGURED", "DATASOURCE_CONFIGURATION_UNSAFE",
					"SETUP_STORAGE_DISABLED", "SETUP_SCHEMA_REQUIRED", "SETUP_METADATA_INVALID" ->
				503;
			default -> 200;
		};
		String status = httpStatus == 503 ? "ERROR" : "REJECTED";
		if ("RESULT_LIMIT_EXCEEDED".equals(code))
			status = "ERROR";
		if ("METRIC_DEFINITION_UNCONFIRMED".equals(code) || isClarification(code))
			status = "NEEDS_CLARIFICATION";
		String message = switch (code) {
			case "SQL_EXAMPLE_SELECTION_INVALID" -> "示例候选集合格式无效，请刷新后重新选择。";
			case "SQL_EXAMPLE_NOT_SELECTABLE" -> "只能选择当前数据集已发布且依赖有效的示例；请重新核对选择。";
			case "SQL_EXAMPLE_SCOPE_MISMATCH" -> "该示例设置不属于当前获准的数据集，未切换数据权限。";
			case "SQL_EXAMPLE_CHANGED", "SQL_EXAMPLE_DEPENDENCY_CHANGED" -> "SQL示例或依赖已经变化，本次不再沿用；请重新提问或更新草稿并验证。";
			case "SQL_EXAMPLE_CASES_REQUIRED", "SQL_EXAMPLE_CASES_DISTINCT_REQUIRED",
					"SQL_EXAMPLE_MAIN_CASE_REQUIRED" ->
				"请至少提供两个不同的回归用例，其中一个必须覆盖示例SQL，并填写独立核对的预期列和结果。";
			case "SQL_EXAMPLE_VERIFICATION_REQUIRED" -> "SQL示例尚未通过结果对账，不能审核发布。";
			case "SQL_EXAMPLE_SEED_UNAVAILABLE", "SQL_EXAMPLE_SOURCE_UNAVAILABLE" ->
				"该查询没有可用的本人成功执行证据；可手工新建草稿，不能把旧回答当成已验证示例。";
			case "SQL_EXAMPLE_INVALID" -> "SQL示例完整性校验失败，未使用该示例。";
			case "SQL_EXAMPLE_TOO_LARGE", "SQL_EXAMPLE_LIMIT" -> "SQL示例超过内容或数量上限，请缩小范围。";
			case "INVESTIGATION_DISABLED" -> "AI业务调查尚未启用；普通问数和已有反馈不受影响。需授权部署、配置证据密钥后启用。";
			case "INVESTIGATION_EVIDENCE_UNAVAILABLE" -> "原查询没有可用的加密执行快照，或证据已过期。请以原范围重新提问并反馈；新查询属于重建，不能冒称复现。";
			case "INVESTIGATION_MODEL_UNAVAILABLE" -> "原会话对应租户模型不可用或调用超时，未降级使用系统模型。";
			case "INVESTIGATION_SCOPE_UNSUPPORTED", "RELATION_SHAPE_UNVERIFIED" ->
				"本次关联结构或时间范围超出已验证范围，未静默改写或放宽筛选。请补充证据或转人工处理。";
			case "RELATION_DEPENDENCY_CHANGED" -> "关联经验的元数据或数据源绑定已变化，本次未沿用，需重新调查验证。";
			case "RELATION_BUSINESS_CONFIRMATION_REQUIRED" -> "试查通过不等于业务关系正确。请补充业务代码、既有报表或业务确认依据，再由独立人工审核。";
			case "INVESTIGATION_NOT_READY", "INVESTIGATION_EVIDENCE_INSUFFICIENT" -> "调查证据不足，尚不能准备可验证的修正，请查看证据与待确认事项。";
			case "INVESTIGATION_BUDGET_EXCEEDED" -> "调查达到时间或查询预算，已停止，没有放宽资源限制。";
			case "VALIDATION_EXPIRED" -> "关联修正验证证据已过期或草稿已改变，请重新验证。";

			case "DEPARTMENT_SOURCE_REQUIRED" -> "此反馈缺少可复核的部门统计计划，或月份不一致。请先重新查询部门排名，从该结果反馈统计粒度问题。";
			case "DEPARTMENT_RULE_STALE" -> "部门修正的数据集、数据源绑定或元数据已改变。请重新试查验证，不能沿用旧版本依据。";
			case "DEPARTMENT_VERIFICATION_STALE" -> "部门修正的试查证据已过期，请重新验证后再人工审核发布。";
			case "DEPARTMENT_REGRESSION_FAILED" -> "部门粒度试查与独立统计不一致，未通过验证，不能发布。";
			case "DETAIL_SCOPE_UNAVAILABLE" -> "上一结果缺少可验证的明细分组依据，或条件已改变。请先重新查询部门排名，再查看第一名明细；不会猜部门或退回自由SQL。";
			case "DETAIL_GRAIN_AMBIGUOUS" -> "上一结果把多个公司或部门合成了一组，无法把它当成一个部门下钻。请反馈统计粒度问题；不会按名称猜部门。";
			case "DETAIL_REQUEST_NEEDS_CLARIFICATION" ->
				"可沿用上一排名的时间、筛选和部门身份，查看原始巡视记录的明细或最早1～100条；可指定记录ID、开始时间等记录字段。若要改变月份、筛选或查询对象，请先重新查询排名；不会忽略新条件或猜测设备检查关联。";
			case "DETAIL_NO_NEXT_PAGE" -> "当前没有可继续的明细页，或已到末页。";
			case "DATASET_NOT_AUTHORIZED" -> "当前账号未获此数据集访问权限或授权已失效，请由管理员选择MATRIX部门、角色或用户授权。";
			case "AUTHORIZATION_MODEL_CHANGED" -> "旧TOP公司/部门配对授权已停用，请刷新页面，改用MATRIX部门、角色、用户的数据集授权。";
			case "METRIC_DEFINITION_UNCONFIRMED" -> "统计口径尚未审核发布，请先确认有效巡视的定义。";
			case "UNAUTHENTICATED" -> "请通过MATRIX登录后重试。";
			case "SQL_COLUMN_REFERENCE_INVALID" -> "查询的字段引用有误，自动修复未完成或不适用。可报告问题；系统不会猜测字段或修改业务条件。";
			case "REPAIR_EXPERIENCE_CHANGED" -> "技术修复经验在查询期间已变化或停用，本次未返回结果，请重新查询。";
			case "RESULT_LIMIT_EXCEEDED" -> "结果超过本次读取上限，未返回截断结果。请缩小范围或指定分页，不需要更改权限。";
			case "DATASOURCE_RESOURCE_LIMIT" -> "数据源连接数或账号资源配额已达上限，请数据源维护人员核查占用与配额；不会通过改SQL、换账号或放宽权限绕过此限制。";
			case "QUERY_TIMEOUT" -> "本次查询超过执行时限，尚未得到完整结果。没有擅自缩小统计范围或返回不完整计数；可明确公司、部门等业务范围，或报告慢查询问题。";
			case "AI_SQL_SCHEMA_REQUIRED" -> "AI问数审计升级尚未安装，请等待批准的测试迁移；没有退回旧SQL入口。";
			case "DATASET_METADATA_REQUIRED", "DATASET_METADATA_CHANGED" ->
				"当前选中表或字段元数据缺失/已变化，请核对原数据集及字段管理后重试；未自动同步或修改备注。";
			case "AI_RESPONSE_TRUNCATED" -> "模型输出达到额度，尚未生成完整回答，本次未执行数据库查询。这不是查询结果行数超限；没有执行被截断的SQL。可重试，或调整模型的输出额度与思考设置。";
			case "AI_RESPONSE_INVALID" -> "模型返回的回答类型或内容不完整，未执行数据查询，请重新提问。";
			case "FOLLOWUP_CONTEXT_UNAVAILABLE" -> "上一查询的完整范围已不可用或不适合直接调整，请完整说明本次查询条件；不会猜测原筛选或从其他会话取条件。";
			case "RESULT_CONTEXT_UNAVAILABLE" -> "当前没有可安全引用的本会话查询结果。可以先查询数据，或说明希望解释哪一条结果。";
			case "AI_QUERY_NEEDS_CLARIFICATION" -> "当前问题尚不足以确定查询条件，请补充时间范围、统计对象或口径后重新提问。";
			case "SQL_TABLE_NOT_ALLOWED" -> "AI候选查询涉及当前数据集未选中的表或其他数据库，未执行。请调整问题或核对数据集选表。";
			case "SQL_INVALID", "SQL_UNSAFE", "SQL_READ_ONLY_REQUIRED", "SQL_FUNCTION_NOT_ALLOWED",
					"SQL_PARAMETER_UNSUPPORTED" ->
				"AI候选SQL未通过只读执行校验，未执行，也没有退回旧SQL入口。可报告问题，不能以此绕过安全检查。";
			default -> "查询未执行或未完成，请根据错误代码处理后重试。";
		};
		var body = envelope(status, code, message);
		if (exception.queryId() != null) {
			body.put("queryId", exception.queryId());
			body.put("ruleVersion", exception.ruleVersion());
		}
		return ResponseEntity.status(httpStatus).body(body);
	}

	public static Map<String, Object> envelope(String status, String code, String message) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("contractVersion", "0.6.1");
		result.put("mock", false);
		result.put("status", status);
		result.put("code", code);
		result.put("message", message);
		result.put("warnings", List.of());
		return result;
	}

	/** Exact controlled reply used for live SSE and durable chat history. */
	public static String chatText(Map<String, Object> response) {
		StringBuilder text = new StringBuilder(response.get("message").toString());
		if ("SUCCEEDED".equals(response.get("status"))) {
			text.append("\n\n| 部门（记录时名称） | 次数 |\n|---|---:|\n");
			for (Object item : (List<?>) response.get("rows")) {
				var row = (Map<?, ?>) item;
				String name = row.get("departmentName") == null ? "名称缺失或冲突，待确认" : row.get("departmentName").toString();
				text.append("| ")
					.append(name.replace("|", "\\|")
						.replace("\n", " ")
						.replace("\r", " ")
						.replace("<", "&lt;")
						.replace(">", "&gt;"))
					.append(" | ")
					.append(row.get("validPatrolCount"))
					.append(" |\n");
			}
			text.append("仅统计明确授权范围；名称及组织编码质量提示见结构化结果。");
		}
		return text.toString();
	}

}
