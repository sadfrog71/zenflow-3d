package com.deepdimen.matrix.assistant.generation;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan;

/**
 * Deterministic local model seam. It is opt-in and only reports the citations
 * already present in the read-only Context Pack; it never invents domain facts
 * or performs actions.
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "matrix.assistant.stub", name = "enabled", havingValue = "true")
public class StubAssistantGenerationAdapter implements AssistantGenerationAdapter {

    @Override
    public GenerationResult generate(GenerationRequest request) {
        List<ContextFragment> citedFragments = request.contextPack().fragments().stream()
                .filter(fragment -> fragment.citation() != null)
                .toList();
        boolean hasAvailableProvider = request.contextPack().providerResults().stream()
                .anyMatch(result -> result.status() == ProviderStatus.OK || result.status() == ProviderStatus.PARTIAL);
        if (!hasAvailableProvider || citedFragments.isEmpty()) {
            AssistantExecutionTrace trace = new AssistantExecutionTrace(request.executionRunId(),
                    ExecutionPlan.UNAVAILABLE, "local-stub", null, null, null, null,
                    request.explicitSkillCodes(), "STUB_CONTEXT_UNAVAILABLE");
            return new GenerationResult(false, "本地模型 Stub 未获得可引用的授权上下文，未生成回答。", trace);
        }
        String citations = citedFragments.stream().map(fragment -> "[" + fragment.citation().citationId() + "]")
                .distinct().limit(8).collect(Collectors.joining(" "));
        String safeQuery = request.query() == null ? "" : request.query().trim();
        AssistantExecutionTrace trace = new AssistantExecutionTrace(request.executionRunId(),
                ExecutionPlan.PRIMARY_ONLY, "local-stub", null, null, null, null,
                request.explicitSkillCodes(), null);
        return new GenerationResult(true,
                "本地模型 Stub 已基于授权 Context Pack 生成只读回执。已参考 "
                        + request.conversationHistory().size() + " 条会话上下文。问题：" + safeQuery + "；引用：" + citations,
                trace);
    }

}
