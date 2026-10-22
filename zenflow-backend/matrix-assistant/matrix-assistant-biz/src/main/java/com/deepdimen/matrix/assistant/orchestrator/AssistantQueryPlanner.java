package com.deepdimen.matrix.assistant.orchestrator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;

/**
 * Turns a conversational request into bounded, domain-neutral search terms.
 *
 * <p>
 * The domain providers intentionally remain read-only and simple. This planner keeps
 * natural-language glue such as "请总结" and "列出" out of their SQL LIKE predicates, while
 * retaining business phrases such as "本地联调样例". Previous user turns are used for follow-up
 * questions, but generated assistant text is never used as a search source.
 * </p>
 */
final class AssistantQueryPlanner {

	private static final int MAX_TERMS = 4;

	private static final Pattern CLAUSE_SPLITTER = Pattern.compile("[，。！？；、,.!?;:：/\\n]+|(?:以及|并且|并|和|与|及|还有)");

	private static final Pattern HAN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");

	private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,}");

	private static final List<String> PREFIXES = List.of("请帮我", "请问", "找一下", "哪一些", "有哪些", "列举", "列出", "总结", "概括",
			"告诉我", "查找", "搜索", "查询", "查看", "分析下", "分析", "梳理", "显示", "给我", "获取", "看看", "找出", "有关", "关于", "当前", "现在",
			"本次", "这些", "一下", "所有", "全部", "最近", "帮我", "麻烦", "请", "相关", "基于");

	private static final List<String> SUFFIXES = List.of("做个分析", "数据分析", "是什么", "有哪些", "有什么", "情况", "信息", "记录", "内容",
			"详情", "列表", "一下", "分析", "吗", "呢", "的");

	private static final Set<String> GENERIC_TERMS = Set.of("待办", "待办任务", "任务", "日程", "资讯", "ai资讯", "ai", "闪念", "知识",
			"记录", "内容", "信息", "项目", "工作", "业务数据", "数据", "智能体", "技能", "当前", "现在");

	private static final List<String> AGGREGATE_BROWSE_TARGETS = List.of("当前所有业务数据", "当前全部业务数据", "所有业务数据", "全部业务数据",
			"这些模块", "这些来源", "所选模块", "所选来源", "选中模块", "选中来源");

	private static final List<String> AGGREGATE_BROWSE_FILLERS = List.of("请帮我", "分析下", "总结下", "梳理下", "做个分析", "做一下",
			"帮我", "基于", "总结", "汇总", "概括", "分析", "梳理", "查看", "列出", "看看", "的内容", "的信息", "内容", "信息", "情况", "一下", "当前", "请",
			"下");

	private AssistantQueryPlanner() {
	}

	static List<String> plan(String query, List<String> previousUserQueries) {
		if (isAggregateBrowseQuery(query)) {
			return List.of();
		}
		LinkedHashSet<String> terms = new LinkedHashSet<>();
		collect(query, terms);
		if (previousUserQueries != null) {
			for (int index = previousUserQueries.size() - 1; index >= 0 && terms.size() < MAX_TERMS; index--) {
				collect(previousUserQueries.get(index), terms);
			}
		}
		return terms.stream().limit(MAX_TERMS).toList();
	}

	static String browseQuery(String providerCode, String originalQuery) {
		return "KNOWLEDGE".equals(providerCode) && originalQuery != null ? originalQuery.trim() : "";
	}

	static ProviderLimits limitsFor(String providerCode) {
		return new ProviderLimits(20, 12000, "KNOWLEDGE".equals(providerCode) ? 10000 : 3000);
	}

	private static boolean isAggregateBrowseQuery(String query) {
		if (query == null) {
			return false;
		}
		String normalized = query.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]", "").toLowerCase(Locale.ROOT);
		String target = AGGREGATE_BROWSE_TARGETS.stream().filter(normalized::contains).findFirst().orElse(null);
		if (target == null) {
			return false;
		}
		String remainder = normalized.replace(target, "");
		for (String filler : AGGREGATE_BROWSE_FILLERS) {
			remainder = remainder.replace(filler, "");
		}
		return remainder.isBlank();
	}

	private static void collect(String source, LinkedHashSet<String> terms) {
		if (source == null || source.isBlank() || terms.size() >= MAX_TERMS) {
			return;
		}
		String normalized = source.trim().toLowerCase(Locale.ROOT);
		for (String clause : CLAUSE_SPLITTER.split(normalized)) {
			String candidate = stripGlue(clause);
			if (candidate.isBlank()) {
				continue;
			}
			addIfSearchable(candidate, terms);
			Matcher han = HAN_PATTERN.matcher(candidate);
			while (han.find() && terms.size() < MAX_TERMS) {
				addIfSearchable(han.group(), terms);
			}
			Matcher latin = LATIN_PATTERN.matcher(candidate);
			while (latin.find() && terms.size() < MAX_TERMS) {
				addIfSearchable(latin.group(), terms);
			}
		}
	}

	private static String stripGlue(String value) {
		String result = value.replaceAll("[\\p{Punct}\\p{IsPunctuation}]", "").replaceAll("\\s+", "");
		boolean changed;
		do {
			changed = false;
			for (String prefix : PREFIXES) {
				if (result.startsWith(prefix) && result.length() > prefix.length()) {
					result = result.substring(prefix.length());
					changed = true;
				}
			}
			for (String suffix : SUFFIXES) {
				if (result.endsWith(suffix) && result.length() > suffix.length()) {
					result = result.substring(0, result.length() - suffix.length());
					changed = true;
				}
			}
		}
		while (changed);
		return result;
	}

	private static void addIfSearchable(String candidate, LinkedHashSet<String> terms) {
		if (candidate == null || candidate.length() < 2 || GENERIC_TERMS.contains(candidate)
				|| candidate.matches("[和及并与以及还有]+")) {
			return;
		}
		terms.add(candidate);
	}

}
