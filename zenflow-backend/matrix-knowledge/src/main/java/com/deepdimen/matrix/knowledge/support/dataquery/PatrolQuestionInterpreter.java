package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.YearMonth;
import java.time.DateTimeException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Narrow deterministic first slice; no SQL generation or untrusted regex. */
public final class PatrolQuestionInterpreter {

	private static final Set<String> SUPPORTED_TERMS = Set.of("巡视", "巡检", "巡查");

	public static List<String> normalizedTerms(List<String> terms) {
		if (terms == null || terms.isEmpty() || terms.size() > 3 || !terms.contains("巡视")
				|| terms.stream().anyMatch(term -> !SUPPORTED_TERMS.contains(term)))
			throw new DataQueryException("UNSUPPORTED_CORRECTION");
		return terms.stream().distinct().sorted().toList();
	}

	private java.util.regex.Matcher match(String question, List<String> terms) {
		if (question == null || question.isBlank() || question.length() > 2000)
			throw new DataQueryException("INTENT_NOT_SUPPORTED");
		String vocabulary = String.join("|", normalizedTerms(terms).stream().map(Pattern::quote).toList());
		// Anchored, finite grammar: equivalent count wording, not arbitrary model SQL.
		var matcher = Pattern
			.compile("^\\s*(?:请问\\s*)?(?:([0-9]{4})年)?([0-9]{1,2})月(?:份)?\\s*" + "(?:各部门|各个部门|每个部门)(?:有效)?("
					+ vocabulary + ")" + "(?:记录(?:有多少(?:条|次)?|次数|数量|数)|(?:了)?多少次|次数|数量)"
					+ "[？?，,。 ]*(?:(?:按(?:次数|数量)?从高到低|从高到低|降序)(?:排列|排序)?)?[？?。 ]*\\s*$")
			.matcher(question);
		if (!matcher.matches())
			throw new DataQueryException("INTENT_NOT_SUPPORTED");
		return matcher;
	}

	public PatrolQueryCompiler.Candidate interpret(String question, List<String> terms) {
		var matched = match(question, terms);
		try {
			int month = Integer.parseInt(matched.group(2));
			if (month < 1 || month > 12)
				throw new DateTimeException("month");
			if (matched.group(1) == null)
				throw new DataQueryException("QUERY_YEAR_REQUIRED");
			int year = Integer.parseInt(matched.group(1));
			if (year < 1 || year > 9998)
				throw new DateTimeException("year");
			YearMonth date = YearMonth.of(year, month);
			return new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count", "department", date.atDay(1),
					date.plusMonths(1).atDay(1), "Asia/Shanghai", "DESC");
		}
		catch (DateTimeException e) {
			throw new DataQueryException("QUERY_DATE_INVALID");
		}
	}

	/** Safe completion for an already-audited missing-year result; never executes. */
	public java.util.Map<String, Object> yearPrompt(String question) {
		var matched = match(question, List.of("巡视", "巡检", "巡查"));
		int month = Integer.parseInt(matched.group(2));
		if (matched.group(1) != null || month < 1 || month > 12)
			throw new DataQueryException("INVALID_REQUEST");
		return java.util.Map.of("type", "YEAR_REQUIRED", "month", month, "intentTerm", matched.group(3));
	}

}
