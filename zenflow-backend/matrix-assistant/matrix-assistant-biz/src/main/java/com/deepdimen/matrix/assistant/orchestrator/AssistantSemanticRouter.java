package com.deepdimen.matrix.assistant.orchestrator;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteDecision;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteTriggerType;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;

/** Deterministic fast paths only. The caller must apply action policy before routing. */
public final class AssistantSemanticRouter {

	private static final Pattern CASUAL = Pattern
		.compile("^(你好|您好|嗨|早上好|晚上好|谢谢|谢谢你|好的|好|好的谢谢|好的，谢谢|再见|hello|hi|thanks|thank you)[！!。.,，?？\\s]*$");

	private final AssistantCapabilityCandidateRetriever retriever = new AssistantCapabilityCandidateRetriever();

	public Optional<RouteDecision> fastRoute(String question, FrozenCapabilityCatalog catalog) {
		String text = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
		List<String> matches = retriever.exactWorkflowMatches(text, catalog);
		if (matches.size() == 1 && capabilityQuestion(text, catalog, matches.get(0))) {
			return Optional.of(new RouteDecision(ConversationIntent.CAPABILITY_HELP, ExecutionMode.CAPABILITY_HELP,
					List.of(), matches, RouteTriggerType.EXACT_AGENT_NAME, null, null, false, false,
					"AGENT_DESCRIPTION_REQUESTED"));
		}
		if (matches.size() == 1 && explicitExecution(text)) {
			var workflow = catalog.workflows()
				.stream()
				.filter(item -> item.capabilityId().equals(matches.get(0)))
				.findFirst()
				.orElseThrow();
			var trigger = retriever.retrieve(text, catalog, 32)
				.stream()
				.filter(item -> item.id().equals(matches.get(0)) && item.workflow())
				.findFirst()
				.orElseThrow()
				.triggerType();
			return Optional.of(new RouteDecision(ConversationIntent.BUSINESS_QUERY, ExecutionMode.WORKFLOW_EXECUTION,
					workflow.requiredProviderCodes(), matches, trigger, null, null, false, false,
					"EXPLICIT_AGENT_EXECUTION"));
		}
		if (matches.isEmpty() && CASUAL.matcher(text).matches()) {
			return Optional.of(new RouteDecision(ConversationIntent.CASUAL_CHAT, ExecutionMode.DIRECT_CHAT, List.of(),
					List.of(), RouteTriggerType.CASUAL_RULE, null, null, false, false, "CASUAL_GREETING"));
		}
		return Optional.empty();
	}

	private boolean explicitExecution(String text) {
		if (text.contains("不要") || text.contains("不用") || text.contains("别调用") || text.contains("能做什么")
				|| text.contains("如何调用") || text.contains("怎么调用") || text.contains("介绍") || text.contains("能否")) {
			return false;
		}
		return text.matches("^(?:请|请帮我|帮我)?(?:调用|执行|运行|用|@).+");
	}

	private boolean capabilityQuestion(String text, FrozenCapabilityCatalog catalog, String id) {
		var workflow = catalog.workflows()
			.stream()
			.filter(item -> item.capabilityId().equals(id))
			.findFirst()
			.orElseThrow();
		boolean nameOnly = text.replaceFirst("^@", "").equals(workflow.displayName().toLowerCase(Locale.ROOT))
				|| workflow.aliases()
					.stream()
					.anyMatch(alias -> text.replaceFirst("^@", "").equals(alias.toLowerCase(Locale.ROOT)));
		// Mixed requests must go through the structured planner, never discard an
		// execution clause.
		boolean execution = text.contains("查今天") || text.contains("查询") || text.contains("执行") || text.contains("调用")
				|| text.contains("并分析") || text.contains("再分析");
		return nameOnly || !execution && (text.endsWith("能做什么") || text.endsWith("能做什么？") || text.endsWith("能做什么?")
				|| text.startsWith("介绍一下") || text.startsWith("介绍下"));
	}

}
