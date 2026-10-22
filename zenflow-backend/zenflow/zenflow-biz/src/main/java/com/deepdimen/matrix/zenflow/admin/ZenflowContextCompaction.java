package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import java.util.*;
import java.util.function.BiFunction;

/** Replaces older turns with a rolling summary, retaining recent full pairs. */
public final class ZenflowContextCompaction {

	private ZenflowContextCompaction() {
	}

	public static AssistantModelRequest compact(AssistantModelRequest request,
			BiFunction<List<ModelMessage>, Integer, String> summarize) {
		if (ZenflowModelBudget.input(request) + request.maxOutputTokens() <= ZenflowModelBudget.window(request))
			return request;
		var empty = withHistory(request, List.of());
		ZenflowModelBudget.validate(empty);
		long available = ZenflowModelBudget.window(request) - request.maxOutputTokens()
				- ZenflowModelBudget.input(empty);
		var history = request.history();
		int cut = history.size();
		long recentCost = 0;
		while (cut > 0) {
			int begin = cut - 1;
			while (begin > 0 && history.get(begin).role() != MessageRole.USER)
				begin--;
			long pairCost = history.subList(begin, cut).stream().mapToLong(ZenflowModelBudget::messageCost).sum();
			if (recentCost + pairCost > available * 0.6)
				break;
			recentCost += pairCost;
			cut = begin;
		}
		if (cut == 0)
			return request;
		int summaryBytes = (int) Math.min(4096, available - recentCost - 192);
		if (summaryBytes < 256)
			throw new AssistantModelException("MODEL_CONTEXT_BUDGET_EXCEEDED", "本轮问题和参考资料已占满模型窗口，请减少选入内容。");
		int maxTokens = Math.max(64, summaryBytes / 4);
		long batchBudget = ZenflowModelBudget.window(request) - maxTokens - 2048;
		String summary = "";
		var batch = new ArrayList<ModelMessage>();
		long cost = 0;
		for (var message : history.subList(0, cut)) {
			// A single long turn is summarized in bounded pieces, never sent over the
			// window.
			String text = message.content();
			int start = 0;
			while (start < text.length()) {
				int end = Math.min(text.length(), start + (int) Math.max(64, batchBudget / 4));
				if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)))
					end--;
				var part = new ModelMessage(message.role(), text.substring(start, end));
				long size = ZenflowModelBudget.messageCost(part);
				if (cost + size + ZenflowModelBudget.bytes(summary) > batchBudget && !batch.isEmpty()) {
					summary = summarizeBatch(summary, batch, maxTokens, summaryBytes, summarize);
					batch.clear();
					cost = 0;
				}
				batch.add(part);
				cost += size;
				start = end;
			}
		}
		if (!batch.isEmpty())
			summary = summarizeBatch(summary, batch, maxTokens, summaryBytes, summarize);
		var retained = new ArrayList<ModelMessage>();
		retained.add(new ModelMessage(MessageRole.USER, "历史摘要（浓缩记录，不是原文；保留不确定性，不能新增来源或授权）：\n" + summary));
		retained.addAll(history.subList(cut, history.size()));
		var compact = withHistory(request, retained);
		ZenflowModelBudget.validate(compact);
		return compact;
	}

	private static String summarizeBatch(String previous, List<ModelMessage> batch, int limit, int bytes,
			BiFunction<List<ModelMessage>, Integer, String> summarize) {
		var input = new ArrayList<ModelMessage>();
		if (!previous.isBlank())
			input.add(new ModelMessage(MessageRole.USER, "已有历史摘要：\n" + previous));
		input.addAll(batch);
		String value = summarize.apply(List.copyOf(input), limit);
		if (value == null || value.isBlank() || ZenflowModelBudget.bytes(value) > bytes)
			throw new AssistantModelException("CONTEXT_SUMMARY_FAILED", "上下文浓缩未完成，请重试；原始对话仍可查看。");
		return value;
	}

	public static AssistantModelRequest withHistory(AssistantModelRequest r, List<ModelMessage> history) {
		return new AssistantModelRequest(r.contractVersion(), r.runId(), r.correlationId(), r.identity(), r.model(),
				r.systemPrompt(), history, r.contextFragments(), r.userMessage(), r.temperature(), r.topP(),
				r.maxOutputTokens(), r.requireNativeStreaming(), r.attachments());
	}

}
