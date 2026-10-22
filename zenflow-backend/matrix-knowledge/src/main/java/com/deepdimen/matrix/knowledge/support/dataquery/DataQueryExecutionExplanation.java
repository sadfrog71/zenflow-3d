package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Execution-owned presentation facts. Never interprets model claims as confirmed
 * provenance.
 */
final class DataQueryExecutionExplanation {

	static final String VERSION = "execution-explanation-v1";

	record Fact(String aspect, String summary, String source, String basis, boolean needsConfirmation) {
	}

	record Correction(String status, String title, String version, String reason) {
	}

	record Snapshot(String version, String queryId, String ruleVersion, String executionStatus, String coverage,
			List<Fact> facts, Correction correction, List<String> warnings, String clarification,
			Map<String, Object> relationCorrection) {
		Snapshot(String version, String queryId, String ruleVersion, String executionStatus, String coverage,
				List<Fact> facts, Correction correction, List<String> warnings, String clarification) {
			this(version, queryId, ruleVersion, executionStatus, coverage, facts, correction, warnings, clarification,
					null);
		}

		Snapshot {
			facts = List.copyOf(facts);
			warnings = List.copyOf(warnings);
		}
	}

	static Snapshot withRelations(Snapshot s, Map<String, Object> report) {
		var notice = s.correction();
		if (report.get("applications") instanceof List<?> applications)
			for (var item : applications)
				if (item instanceof Map<?, ?> r) {
					String status = String.valueOf(r.get("status")), version = String.valueOf(r.get("version"));
					if (Set.of("APPLIED", "CHECKED").contains(status)) {
						notice = new Correction("APPLIED", "本次关联修正已按实际执行结构确认", version,
								"关联身份与父记录计数粒度通过后端检查，原筛选保留；不代表其它指标、名称或源数据完整性已验证。");
						break;
					}
					if ("NOT_APPLICABLE".equals(status) && !"APPLIED".equals(notice.status()))
						notice = new Correction("PUBLISHED_NOT_APPLICABLE", "有关联经验，但本次未实际应用", version,
								"本次结构或指标超出该父记录关联定义的已验证范围；仍受普通问数授权、只读和资源校验，不能把提示词检索当作最终SQL修正。");
					if ("DISABLED".equals(status) && !"APPLIED".equals(notice.status()))
						notice = new Correction("NO_CORRECTION", "关联修正已停用，本次未应用", version, "后续查询停止消费该经验；旧聊天保持原有说明。");
				}
		return new Snapshot(s.version(), s.queryId(), s.ruleVersion(), s.executionStatus(), s.coverage(), s.facts(),
				notice, s.warnings(), s.clarification(), report);
	}

	static Snapshot success(DataQueryAiChatService.Context c, DataQuerySqlGuard.Prepared prepared,
			DataQueryDepartmentDimension.Captured captured, boolean applied, List<Map<String, Object>> quality,
			String draftStatus) {
		var scope = captured == null ? DataQueryDepartmentDimension.totalScope(prepared).orElse(null)
				: captured.scope();
		var facts = new ArrayList<Fact>();
		var warnings = new ArrayList<String>();
		if (scope == null) {
			facts.add(fact("object", "当前获准数据集中本次候选查询涉及的业务对象", "MODEL_OR_UNCONFIRMED", "执行范围已校验；具体业务对象尚未可靠归纳。"));
			facts.add(fact("metric", "指标及去重依据待核对", "MODEL_OR_UNCONFIRMED", "当前说明器不能完整解释这种查询，SQL可执行不等于指标正确。"));
			facts.add(fact("identity", "分组身份待核对", "MODEL_OR_UNCONFIRMED", "未将显示名称认定为实体身份。"));
			facts.add(fact("time", "时间范围未可靠提取", "MODEL_OR_UNCONFIRMED", "不是没有时间筛选，也不是默认全时期。"));
			facts.add(fact("filter", "筛选及关联条件未完整解释", "MODEL_OR_UNCONFIRMED", "不表示没有筛选；没有擅自添加或删除候选条件。"));
			facts.add(fact("name", "名称来源及数据质量待核对", "MODEL_OR_UNCONFIRMED", "尚不能确认权威名称或关联关系。"));
			warnings.add("本次说明不完整，不据此自动要求逐项确认；如结果不符，可从原结果报告问题。");
		}
		else {
			facts.add(fact("object", "巡视记录", "SERVER_CAPTURED", "来自后端对实际执行候选的有限结构解析，不是模型自述。"));
			String countBasis = applied ? "ALL_ROWS" : DataQueryDepartmentDimension.countBasis(prepared,
					captured == null ? 1 : captured.visibleColumns());
			String metric = switch (countBasis) {
				case "ALL_ROWS" -> "记录条数（逐行计数，未按设备或其他业务对象去重）";
				case "NON_NULL_RECORD_ID" -> "非空记录标识的条数（未去重；空记录标识不计入）";
				default -> "存在多种记录计数表达式，计数依据需分别核对";
			};
			facts.add(fact("metric", metric, "SERVER_CAPTURED", "来自本次实际计数表达式；不假定记录标识非空，也不是完成率或设备检查项数量。"));
			String grouping = captured == null ? "全部符合条件的记录合计，不按部门分组" : applied ? "实际公司标识＋部门标识；名称不参与合并或拆分"
					: String.join("＋", captured.groupKeys().stream().map(k -> fieldLabel(k.field())).toList());
			facts.add(fact("identity", grouping, applied ? "PUBLISHED_CORRECTION" : "SERVER_CAPTURED",
					applied ? "本轮实际使用已发布的部门身份编译，不是仅加载规则或提示词。" : "这是候选查询实际分组方式；按名称分组可能合并不同身份，或拆分同一身份。"));
			addScope(c, scope, facts, false);
			if (scope.topN() > 0)
				facts.add(fact("output",
						captured == null ? "候选结果最多返回" + scope.topN() + "行（不是记录总数上限）" : "按次数降序取前" + scope.topN() + "组",
						"SERVER_CAPTURED", "来自实际候选SQL的输出限制，不冒称为用户确认或安全上限。"));
			facts.add(fact("name", "巡视记录中的记录时部门名称，不是权威组织维表", "SERVER_CAPTURED",
					applied ? "缺失或冲突保留计数并将展示名称留空；只针对未被原筛选排除的记录。" : "普通候选未保证解决同名、缺失或冲突。"));
			if (applied) {
				long conflicts = quality.stream().filter(q -> "CONFLICT".equals(q.get("nameStatus"))).count();
				long missing = quality.stream()
					.filter(q -> "MISSING".equals(q.get("nameStatus")) || Boolean.TRUE.equals(q.get("missingIdentity")))
					.count();
				warnings.add("仅本次返回分组：名称冲突" + conflicts + "组，名称或身份缺失" + missing + "组；不是源库全量质量检查。");
			}
		}
		addPolicy(facts, null);
		return snapshot(c, "SUCCEEDED", scope == null ? "PARTIAL" : "CAPTURED_PATROL", facts,
				correction(c, applied, draftStatus), warnings, clarification(facts));
	}

	static Snapshot detail(DataQueryAiChatService.Context c, DataQueryDepartmentDimension.Page page,
			com.fasterxml.jackson.databind.JsonNode source) {
		var facts = new ArrayList<Fact>();
		facts.add(fact("object", "上一结果第一名对应的原始巡视记录", "PREVIOUS_EXECUTION", "使用已捕获的真实分组键，不从名称猜键，不加入设备检查明细关联。"));
		facts.add(fact("metric", "原始记录明细，不是再次聚合", "SERVER_CAPTURED", "每轮重新执行，只返回当前页。"));
		facts.add(fact("identity", "沿用上次结果的实际公司及部门分组身份", "PREVIOUS_EXECUTION", "当前登录、租户、数据集、会话、授权、库存和规则重新检查。"));
		addScope(c, page.ranking().captured().scope(), facts, true);
		facts.add(fact("name", "原始巡视记录中的记录时名称", "PREVIOUS_EXECUTION", "没有补齐权威部门名称，也没有修改源记录。"));
		addPolicy(facts, page);
		boolean known = source != null && VERSION.equals(source.path("version").asText())
				&& "SUCCEEDED".equals(source.path("executionStatus").asText()) && Objects.equals(c.ruleVersion(),
						source.path("ruleVersion").isNull() ? null : source.path("ruleVersion").asText());
		boolean inherited = known && "APPLIED".equals(source.path("correction").path("status").asText());
		var correction = inherited
				? new Correction("APPLIED", "沿用已修正的部门身份下钻", c.ruleVersion(), "上次执行记录证明身份修正已应用；本轮按当前数据重新读取明细。")
				: !known ? new Correction("HISTORICAL_UNKNOWN", "上次结果缺少修正应用记录", null,
						"只继承已审计的分组键；不能确认上次是否应用修正，不从当前规则补造。")
						: new Correction("NO_CORRECTION", "本次读取明细，未应用新的部门聚合修正", null, "上次执行记录未标记身份修正已应用；本轮不重新聚合部门。");
		return snapshot(c, "SUCCEEDED", "CAPTURED_PATROL", facts, correction, List.of(), clarification(facts));
	}

	static Snapshot failure(DataQueryAiChatService.Context c, String code) {
		boolean stale = Set
			.of("RULE_CHANGED", "DEPARTMENT_RULE_STALE", "AUTHORIZATION_CHANGED", "DATASET_METADATA_CHANGED",
					"DETAIL_SCOPE_UNAVAILABLE")
			.contains(code);
		var correction = new Correction(stale ? "SCOPE_OR_VERSION_CHANGED" : "NOT_EXECUTED",
				stale ? "缺少可沿用的范围或版本依据" : "本次未完成查询，不能声称修正生效", null,
				stale ? "范围、版本已改变或上次执行依据不可用；未沿用旧修正返回答案。" : "查询未成功完成；技术建议或规则存在都不代表业务修正已应用。");
		return snapshot(c, DataQueryResponses.isClarification(code) ? "NEEDS_CLARIFICATION" : "ERROR", "UNAVAILABLE",
				List.of(), correction, List.of("没有可供确认的成功执行结果。"), null);
	}

	static Snapshot historicalUnknown(String queryId, String version, String status) {
		return new Snapshot(VERSION, queryId, version, status, "HISTORICAL_UNKNOWN", List.of(),
				new Correction("HISTORICAL_UNKNOWN", "历史结果缺少应用记录，无法确认", null, "只显示当时实际保存的证据；未重跑SQL、未根据现在的规则补造历史说明。"),
				List.of(), null);
	}

	private static Correction correction(DataQueryAiChatService.Context c, boolean applied, String draft) {
		if (applied)
			return new Correction("APPLIED", "已应用部门身份粒度修正", c.ruleVersion(),
					"后端已按实际公司＋部门身份编译并成功执行；名称不参与分组身份。不是权威名称或设备关联修复。");
		if (c.rule() != null && c.rule().department() != null)
			return new Correction("PUBLISHED_NOT_APPLICABLE", "有已发布的部门修正，本次不适用", c.ruleVersion(),
					"本次候选不匹配已支持的按月部门巡视计数；未删筛选、未强套模板。");
		if ("UNPUBLISHED".equals(draft))
			return new Correction("UNPUBLISHED", "相关修正尚未发布，本次未应用", null, "当前用户反馈关联的匹配草稿尚未发布；验证通过也不等于已生效。");
		if ("UNAVAILABLE".equals(draft))
			return new Correction("AVAILABILITY_UNKNOWN", "未应用部门修正；草稿状态未能确认", null, "不把状态读取失败当成不存在草稿，也不加载未发布载荷。");
		return new Correction("NO_CORRECTION", "本次未应用部门身份修正", null, "普通查询仍走灵活AI SQL。已加载的意图提示不等于确定性业务修正或脱敏。");
	}

	private static void addScope(DataQueryAiChatService.Context c, DataQueryDepartmentDimension.Scope scope,
			List<Fact> facts, boolean inherited) {
		boolean explicit = false;
		if (!inherited)
			try {
				var intent = new PatrolQuestionInterpreter().interpret(c.question(), List.of("巡视", "巡检", "巡查"));
				explicit = intent.from().toString().equals(scope.from()) && intent.to().toString().equals(scope.to());
			}
			catch (DataQueryException unsupported) {
				/* Unknown origin is not confirmation. */ }
		facts.add(fact("time", scope.from() + "（含）至" + scope.to() + "（不含）；按巡视开始时间",
				inherited ? "PREVIOUS_EXECUTION" : explicit ? "USER_EXPLICIT" : "MODEL_OR_UNCONFIRMED",
				inherited ? "继承上次成功执行范围，不代表用户本轮重新确认了全部条件。"
						: explicit ? "服务端有限解析器与已归属的原问题逐项匹配了月份。" : "这是实际SQL中的时间范围；服务端未可靠确认其来源，未采用模型的来源自述。"));
		boolean testDefinition = !inherited && c.rule() != null && DEFINITION.equals(c.rule().definition());
		facts.add(new Fact("filter", "只包含删除标记为0的巡视记录",
				inherited ? "PREVIOUS_EXECUTION"
						: testDefinition ? "PUBLISHED_TEST_DEFINITION" : "MODEL_OR_UNCONFIRMED",
				testDefinition ? "来自已发布的限定巡视计数测试定义（" + c.ruleVersion() + "），不等于正式有效巡视口径；不得推广到其他指标。"
						: "实际候选或继承范围包含该条件；不是所有问题的默认正式口径。",
				!testDefinition));
		for (String field : scope.nonNullFields())
			facts.add(new Fact("filter", "排除" + fieldLabel(field) + "为NULL的记录", "MODEL_OR_UNCONFIRMED",
					"实际候选" + (inherited ? "继承了" : "包含") + "此筛选。现有部门粒度修正没有批准它；保留筛选不代表用户要求。", true));
		if (scope.nonEmptyNameFilter())
			facts.add(new Fact("filter", "排除空字符串部门名称（沿用源库比较规则）", "MODEL_OR_UNCONFIRMED",
					"这不是名称纠错策略，而是会排除记录的候选筛选；没有确认其业务依据。", true));
	}

	private static void addPolicy(List<Fact> facts, DataQueryDepartmentDimension.Page page) {
		facts
			.add(fact("policy", "仅当前MATRIX身份获准的数据集；只读执行，每轮重新授权", "SERVER_POLICY", "源库公司和部门是统计维度，不是账号授权；资源限制不会静默截断答案。"));
		if (page != null)
			facts.add(fact("output", "第" + page.pageNumber() + "页，每页最多" + page.pageSize() + "条；开始时间、记录ID升序",
					"SERVER_POLICY", "服务端稳定游标分页；默认50条，可指定最早1～100条，不代表全部符合条件的明细数量。"));
	}

	private static String clarification(List<Fact> facts) {
		return facts.stream().anyMatch(Fact::needsConfirmation) ? "请核对这些筛选是否符合您的意图："
				+ String.join("；", facts.stream().filter(Fact::needsConfirmation).map(Fact::summary).toList())
				+ "。它们可能改变统计对象，但当前影响尚未独立对账。当前显示的是保留这些条件的结果，不是无附加筛选的总结果；可针对这项范围追问或报告问题，不必逐项确认其他条件。" : null;
	}

	private static Fact fact(String aspect, String summary, String source, String basis) {
		return new Fact(aspect, summary, source, basis, false);
	}

	private static Snapshot snapshot(DataQueryAiChatService.Context c, String status, String coverage, List<Fact> facts,
			Correction correction, List<String> warnings, String clarification) {
		return new Snapshot(VERSION, c.queryId(), c.ruleVersion(), status, coverage, facts, correction, warnings,
				clarification);
	}

	private static String fieldLabel(String field) {
		return switch (field) {
			case "company_id" -> "公司标识";
			case "patrol_dept_id" -> "部门标识";
			case "patrol_dept_name" -> "记录时部门名称";
			default -> "未确认维度";
		};
	}

}
