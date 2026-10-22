package com.deepdimen.matrix.assistant.orchestrator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteTriggerType;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;

/**
 * Retrieves only from a caller-supplied, already authorized frozen catalog. Never
 * authorizes execution.
 */
public final class AssistantCapabilityCandidateRetriever {

	private static final Pattern WORDS = Pattern.compile("[a-z0-9_-]{2,}|[\\p{IsHan}]{2,}");

	private static final Set<String> GENERIC = Set.of("一下", "什么", "情况", "分析", "智能", "能体", "数据", "查询", "今天");

	public List<Candidate> retrieve(String question, FrozenCapabilityCatalog catalog, int limit) {
		if (limit < 1 || limit > 32) {
			throw new IllegalArgumentException("Candidate limit must be between 1 and 32");
		}
		String query = normalize(question);
		if (query.isBlank()) {
			return List.of();
		}
		Set<String> queryTerms = terms(query);
		List<Candidate> candidates = new ArrayList<>();
		catalog.providers()
			.forEach(provider -> add(candidates, query, queryTerms, provider.providerCode(), false,
					provider.displayName(), provider.description(), List.of()));
		catalog.workflows()
			.forEach(workflow -> add(candidates, query, queryTerms, workflow.capabilityId(), true,
					workflow.displayName(), String.join(" ", normalize(workflow.description()),
							String.join(" ", workflow.intentExamples()), String.join(" ", workflow.operationRules())),
					workflow.aliases()));
		return candidates.stream()
			.sorted(Comparator.comparingDouble(Candidate::score)
				.reversed()
				.thenComparing(Candidate::workflow)
				.thenComparing(Candidate::id))
			.limit(limit)
			.toList();
	}

	/**
	 * Reuses the original catalog identity; validation must still use the full frozen
	 * catalog.
	 */
	public FrozenCapabilityCatalog shortlist(String question, FrozenCapabilityCatalog catalog, int limit,
			java.util.Collection<String> requiredProviderIds) {
		List<Candidate> candidates = retrieve(question, catalog, limit);
		Set<String> workflows = new LinkedHashSet<>();
		Set<String> providers = new LinkedHashSet<>(requiredProviderIds == null ? List.of() : requiredProviderIds);
		for (Candidate candidate : candidates) {
			(candidate.workflow() ? workflows : providers).add(candidate.id());
		}
		catalog.workflows()
			.stream()
			.filter(item -> workflows.contains(item.capabilityId()))
			.forEach(item -> providers.addAll(item.requiredProviderCodes()));
		return new FrozenCapabilityCatalog(
				catalog.providers().stream().filter(item -> providers.contains(item.providerCode())).toList(),
				catalog.workflows().stream().filter(item -> workflows.contains(item.capabilityId())).toList(),
				catalog.digest(), catalog.frozenAt());
	}

	private void add(List<Candidate> result, String query, Set<String> queryTerms, String id, boolean workflow,
			String name, String evidence, List<String> aliases) {
		String normalizedName = normalize(name);
		boolean exact = !normalizedName.isBlank() && containsName(query, normalizedName);
		boolean alias = aliases.stream()
			.map(this::normalize)
			.filter(this::specificAlias)
			.anyMatch(value -> containsName(query, value));
		Set<String> evidenceTerms = terms(
				normalize(name) + " " + normalize(evidence) + " " + String.join(" ", aliases));
		long overlap = queryTerms.stream().filter(evidenceTerms::contains).count();
		if (exact || alias || overlap > 0) {
			// Ranking scores are evidence weights, not calibrated probabilities.
			double score = exact ? 100 : alias ? 90 : (double) overlap / Math.max(1, queryTerms.size());
			result.add(new Candidate(id, workflow, score, exact && workflow ? RouteTriggerType.EXACT_AGENT_NAME
					: alias && workflow ? RouteTriggerType.EXACT_AGENT_ALIAS : RouteTriggerType.DESCRIPTION));
		}
	}

	private boolean specificAlias(String alias) {
		return alias.codePointCount(0, alias.length()) >= 3 && !GENERIC.contains(alias)
				&& !Set.of("智能体", "工作流", "助手", "知识库").contains(alias);
	}

	/**
	 * Checks all authorized names and aliases before truncation; never chooses a winner
	 * from a tie.
	 */
	public List<String> exactWorkflowMatches(String question, FrozenCapabilityCatalog catalog) {
		String query = normalize(question);
		return catalog.workflows()
			.stream()
			.filter(workflow -> (!normalize(workflow.displayName()).isBlank()
					&& containsName(query, normalize(workflow.displayName())))
					|| workflow.aliases()
						.stream()
						.map(this::normalize)
						.filter(this::specificAlias)
						.anyMatch(alias -> containsName(query, alias)))
			.map(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot::capabilityId)
			.distinct()
			.toList();
	}

	private boolean containsName(String query, String name) {
		if (name.codePoints().anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN)) {
			return query.contains(name);
		}
		return Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(name) + "(?![a-z0-9_])").matcher(query).find();
	}

	private Set<String> terms(String value) {
		Set<String> result = new LinkedHashSet<>();
		var matcher = WORDS.matcher(value);
		while (matcher.find()) {
			String token = matcher.group();
			if (token.codePoints()
				.allMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN)) {
				int[] points = token.codePoints().toArray();
				for (int i = 0; i + 1 < points.length; i++) {
					String pair = new String(points, i, 2);
					if (!GENERIC.contains(pair))
						result.add(pair);
				}
			}
			else
				result.add(token);
		}
		return result;
	}

	private String normalize(String value) {
		return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
	}

	public record Candidate(String id, boolean workflow, double score, RouteTriggerType triggerType) {
	}

}
