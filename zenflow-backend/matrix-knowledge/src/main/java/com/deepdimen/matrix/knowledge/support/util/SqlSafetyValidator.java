package com.deepdimen.matrix.knowledge.support.util;

import cn.hutool.core.util.StrUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 安全校验工具，用于 Dify Chat2BI 数据查询场景
 *
 * @author matrix
 * @date 2026/04/27
 */
public final class SqlSafetyValidator {

	private static final int MAX_LIMIT = 501;

	private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
			"\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE|CALL)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern DANGEROUS_SELECT_PATTERN = Pattern.compile(
			"\\b(INTO\\s+OUTFILE|INTO\\s+DUMPFILE|FOR\\s+UPDATE|LOCK\\s+IN\\s+SHARE\\s+MODE)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
			"\\b(?:FROM|JOIN|INNER\\s+JOIN|LEFT\\s+JOIN|RIGHT\\s+JOIN|CROSS\\s+JOIN|FULL\\s+JOIN)\\s+([`\"]?\\w+[`\"]?(?:\\s*,\\s*[`\"]?\\w+[`\"]?)*)",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[`\"]?(\\w+)[`\"]?");

	private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("'[^']*'");

	private static final Pattern PARENTHESIZED_PATTERN = Pattern.compile("\\([^()]*\\)");

	private SqlSafetyValidator() {
	}

	/**
	 * 校验 SQL 是否安全（仅允许 SELECT）
	 * @param sql 待校验的 SQL
	 * @return 错误信息，null 表示通过
	 */
	public static String validate(String sql) {
		if (StrUtil.isBlank(sql)) {
			return "SQL is empty";
		}

		String trimmed = sql.trim();
		if (!trimmed.toUpperCase().startsWith("SELECT")) {
			return "Only SELECT statements are allowed";
		}

		String stripped = STRING_LITERAL_PATTERN.matcher(trimmed).replaceAll("''");

		Matcher dangerousMatcher = DANGEROUS_PATTERN.matcher(stripped);
		if (dangerousMatcher.find()) {
			return "Forbidden keyword: " + dangerousMatcher.group(1);
		}

		Matcher dangerousSelectMatcher = DANGEROUS_SELECT_PATTERN.matcher(stripped);
		if (dangerousSelectMatcher.find()) {
			return "Forbidden clause: " + dangerousSelectMatcher.group(1);
		}

		return null;
	}

	/**
	 * 校验 SQL 中引用的表是否全部在白名单内
	 * @param sql SQL 语句
	 * @param allowedTables 允许的表名集合
	 * @return 错误信息，null 表示通过
	 */
	public static String validateTables(String sql, Set<String> allowedTables) {
		Set<String> referencedTables = extractTableNames(sql);
		Set<String> lowerAllowed = new HashSet<>();
		for (String t : allowedTables) {
			lowerAllowed.add(t.toLowerCase());
		}

		for (String table : referencedTables) {
			if (!lowerAllowed.contains(table.toLowerCase())) {
				return "Table not in dataset: " + table;
			}
		}
		return null;
	}

	/**
	 * 从 SQL 中提取引用的表名（支持逗号分隔的多表）
	 */
	public static Set<String> extractTableNames(String sql) {
		Set<String> tables = new HashSet<>();
		Matcher matcher = TABLE_REF_PATTERN.matcher(sql);
		while (matcher.find()) {
			String tableList = matcher.group(1);
			Matcher nameMatcher = TABLE_NAME_PATTERN.matcher(tableList);
			while (nameMatcher.find()) {
				tables.add(nameMatcher.group(1));
			}
		}
		return tables;
	}

	/**
	 * 强制 LIMIT，如果没有 LIMIT 或 LIMIT 超过上限则替换。 对最外层 SQL 生效（剥离子查询括号后匹配）。
	 * @param sql 原始 SQL
	 * @return 处理后的 SQL
	 */
	public static String enforceLimit(String sql) {
		String outerSql = sql;
		String prev;
		do {
			prev = outerSql;
			outerSql = PARENTHESIZED_PATTERN.matcher(outerSql).replaceAll("_SUB_");
		}
		while (!outerSql.equals(prev));

		Matcher outerMatcher = LIMIT_PATTERN.matcher(outerSql);
		int lastStart = -1;
		int lastLimit = 0;
		while (outerMatcher.find()) {
			lastStart = outerMatcher.start();
			lastLimit = Integer.parseInt(outerMatcher.group(1));
		}

		if (lastStart >= 0) {
			if (lastLimit > MAX_LIMIT) {
				Matcher originalMatcher = LIMIT_PATTERN.matcher(sql);
				int idx = 0;
				StringBuilder sb = new StringBuilder();
				while (originalMatcher.find()) {
					idx++;
				}
				originalMatcher.reset();
				int count = 0;
				while (originalMatcher.find()) {
					count++;
					if (count == idx) {
						originalMatcher.appendReplacement(sb, "LIMIT " + MAX_LIMIT);
					}
				}
				originalMatcher.appendTail(sb);
				return sb.toString();
			}
			return sql;
		}
		return sql.replaceAll(";\\s*$", "") + " LIMIT " + MAX_LIMIT;
	}

}
