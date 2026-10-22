package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import java.util.regex.Pattern;

/** Recognize bounded detail operations, never infer new business predicates. */
final class DataQueryDrilldownRequest {

	record Request(boolean next, int pageSize, List<String> columns, boolean supported) {
	}

	private static final Pattern FIRST = Pattern.compile(
			"^(?:请|帮我|给我|麻烦)*(?:只|仅)?(?:查看|看看|看一下|看下|看|查询|显示|展示|列出)?(?:刚才|上一轮|上轮|上次|上一个结果|上面)?的?(?:第一名|第1名|第一位|榜首|排名第一)(?:的部门)?的?");

	private static final Pattern DETAIL = Pattern.compile("明细|详情|原始.*记录|(?:巡视|巡查|巡检)记录");

	private static final Pattern REFERENCE = Pattern.compile("第一名|第1名|第一位|榜首|排名第一|刚才|上一轮|上轮|上次|上一个结果|上面");

	private static final Pattern EARLIEST = Pattern.compile("最早的?([0-9]+|[一二两三四五六七八九十]+)条");

	private static final Pattern PROJECTION = Pattern
		.compile("(?:只|仅)(?:返回|显示|展示|列出|要)|列出(?=记录id|记录名称|开始时间|公司|巡视部门|部门)");

	private static final Pattern ORDER = Pattern
		.compile("按(?:巡视|巡查|巡检)?开始时间(?:(?:和|与|及)?(?:记录)?id)?(?:升序排序|正序排序|升序|正序|排序)");

	private static final Pattern OBJECT = Pattern.compile("(?:原始)?(?:(?:巡视|巡查|巡检)记录(?:明细|详情)?|记录(?:明细|详情)?|明细|详情)");

	static Request parse(String question) {
		String q = question.toLowerCase(Locale.ROOT).replaceAll("[\\s，。？！?!,、；;：:]", "");
		if (Set.of("下一页", "看下一页", "明细下一页").contains(q))
			return new Request(true, 0, List.of(), true);
		if (q.matches("^(?:请|帮我|给我)?(?:看|查看|显示|明细)?下一页.*"))
			return clarify();
		var first = FIRST.matcher(q);
		boolean firstReference = first.find();
		boolean shortRequest = EARLIEST.matcher(q.replaceFirst("^(?:只看|查看|看)", "")).lookingAt()
				|| Set.of("完整明细", "看完整明细").contains(q);
		boolean explicitDetail = q.contains("明细") || q.contains("详情") || q.contains("原始") || EARLIEST.matcher(q).find();
		boolean referencedDetail = REFERENCE.matcher(q).find() && explicitDetail;
		boolean firstDetail = firstReference && DETAIL.matcher(q).find() && !q.matches(".*(?:记录数|多少|为什么|解释|分析).*");
		if (!firstDetail && !shortRequest && !referencedDetail)
			return null;
		if (!firstReference && !shortRequest)
			return clarify();
		String rest = firstReference ? q.substring(first.end()) : q.replaceFirst("^(?:只看|查看|看)", "");
		rest = rest.replaceFirst("(?:只|仅)(?:显示|展示|返回|看)(?=最早)", "");
		var projection = PROJECTION.matcher(rest);
		List<String> columns = List.of();
		if (projection.find()) {
			columns = columns(rest.substring(projection.end()));
			if (columns.isEmpty())
				return clarify();
			rest = rest.substring(0, projection.start());
		}
		// Only stable ascending ordering is supported here. Unknown/changed dates,
		// filters, objects or descending order remain unconsumed and require
		// clarification.
		rest = ORDER.matcher(rest).replaceFirst("");
		var earliest = EARLIEST.matcher(rest);
		int pageSize = 50;
		if (earliest.find()) {
			pageSize = count(earliest.group(1));
			if (pageSize < 1 || pageSize > 100 || rest.contains("完整") || rest.contains("全部"))
				return clarify();
			rest = earliest.replaceFirst("");
		}
		rest = rest.replaceFirst("^(?:完整|全部)", "");
		rest = OBJECT.matcher(rest).replaceFirst("");
		if (!rest.isEmpty())
			return clarify();
		return new Request(false, pageSize, columns, true);
	}

	private static int count(String value) {
		if (value.matches("[0-9]+")) {
			try {
				return Integer.parseInt(value);
			}
			catch (NumberFormatException invalid) {
				return -1;
			}
		}
		if ("两".equals(value))
			return 2;
		return value.length() == 1 ? "一二三四五六七八九十".indexOf(value) + 1 : -1;
	}

	private static List<String> columns(String text) {
		var aliases = new LinkedHashMap<String, String>();
		aliases.put("巡视部门标识", "patrol_dept_id");
		aliases.put("巡视部门id", "patrol_dept_id");
		aliases.put("巡视部门名称", "patrol_dept_name");
		aliases.put("巡视开始时间", "start_date");
		aliases.put("部门标识", "patrol_dept_id");
		aliases.put("部门id", "patrol_dept_id");
		aliases.put("部门名称", "patrol_dept_name");
		aliases.put("公司标识", "company_id");
		aliases.put("公司id", "company_id");
		aliases.put("记录id", "record_id");
		aliases.put("记录名称", "record_name");
		aliases.put("开始时间", "start_date");
		DataQueryDepartmentDimension.DETAIL_COLUMNS.forEach(c -> aliases.put(c, c));
		var result = new ArrayList<String>();
		while (!text.isEmpty()) {
			String remaining = text;
			String key = aliases.keySet().stream().filter(remaining::startsWith).findFirst().orElse(null);
			if (key == null || result.contains(aliases.get(key)))
				return List.of();
			result.add(aliases.get(key));
			text = text.substring(key.length());
			if (!text.isEmpty()) {
				text = text.replaceFirst("^(?:和|与|及)", "");
				if (text.isEmpty())
					return List.of();
			}
		}
		return List.copyOf(result);
	}

	private static Request clarify() {
		return new Request(false, 0, List.of(), false);
	}

	private DataQueryDrilldownRequest() {
	}

}
