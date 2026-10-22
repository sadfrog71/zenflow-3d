package com.deepdimen.matrix.assistant.policy;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantProposalContract.ProposalType;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.IntentDisposition;

@Component
public class AssistantIntentPolicy {

	public IntentDecision classify(String content) {
		String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
		if (containsAny(normalized, "发送邮件", "发送消息", "删除", "付款", "支付", "修改权限", "新增授权", "取消授权",
				"授予权限", "变更权限")) {
			return new IntentDecision(IntentDisposition.ACTION_NOT_SUPPORTED, null);
		}
		if (containsAny(normalized, "创建任务", "新建任务", "加入待办")) {
			return new IntentDecision(IntentDisposition.PROPOSAL_ONLY_DISABLED, ProposalType.TASK_CREATE);
		}
		if (containsAny(normalized, "修改状态", "完成任务")) {
			return new IntentDecision(IntentDisposition.PROPOSAL_ONLY_DISABLED, ProposalType.TASK_STATUS_CHANGE);
		}
		if (containsAny(normalized, "加入知识库", "保存到知识库")) {
			return new IntentDecision(IntentDisposition.PROPOSAL_ONLY_DISABLED, ProposalType.KNOWLEDGE_DRAFT_CREATE);
		}
		return new IntentDecision(IntentDisposition.READ_ONLY, null);
	}

	private boolean containsAny(String content, String... keywords) {
		for (String keyword : keywords) {
			if (content.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	public record IntentDecision(IntentDisposition disposition, ProposalType proposalType) {
	}

}
