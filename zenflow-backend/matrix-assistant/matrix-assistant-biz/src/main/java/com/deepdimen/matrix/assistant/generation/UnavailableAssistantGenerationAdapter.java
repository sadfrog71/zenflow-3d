package com.deepdimen.matrix.assistant.generation;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan;

@Component
@ConditionalOnProperty(prefix = "matrix.assistant.stub", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableAssistantGenerationAdapter implements AssistantGenerationAdapter {

	@Override
	public GenerationResult generate(GenerationRequest request) {
		boolean hasAvailableProvider = request.contextPack()
			.providerResults()
			.stream()
			.anyMatch(result -> result.status() != ProviderStatus.UNAVAILABLE);
		String content = hasAvailableProvider ? "Context Pack 已生成，但模型适配器尚未接线，未生成模拟回答。"
				: "统一会话已建立，但当前数据 Provider 均未接线，无法生成跨域回答。";
		AssistantExecutionTrace trace = new AssistantExecutionTrace(request.executionRunId(), ExecutionPlan.UNAVAILABLE,
				"unavailable", null, null, null, null, request.explicitSkillCodes(), "GENERATION_ADAPTER_UNAVAILABLE");
		return new GenerationResult(false, content, trace);
	}

}
