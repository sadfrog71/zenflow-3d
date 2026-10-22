package com.deepdimen.matrix.assistant.generation;

import java.util.stream.Collectors;

import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Deterministic native model used by local integration tests. It is unavailable outside
 * the dev profile and still requires an explicit gateway selection.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "matrix.assistant.model.gateway", havingValue = "stub")
public class DevelopmentAssistantModelGateway implements AssistantModelGateway {

	private static final ResolvedAssistantModel MODEL = new ResolvedAssistantModel(AssistantModelContract.VERSION,
			"local-e2e-model", "Local E2E Model", "development-stub", new ModelCapabilities(true, 8192, 2048, true),
			new AssistantEgress(DeploymentDomain.INTERNAL, "local", true, DataClassification.SENSITIVE,
					"local-dev-no-retention"));

	@Override
	public ResolvedAssistantModel resolve(ModelResolutionRequest request) {
		if (!AssistantModelContract.VERSION.equals(request.contractVersion())) {
			throw new AssistantModelException("MODEL_CONTRACT_INCOMPATIBLE", "原生模型契约版本不兼容");
		}
		return MODEL;
	}

	@Override
	public Flux<ModelChunk> stream(AssistantModelRequest request) {
		String citations = request.contextFragments()
			.stream()
			.map(fragment -> "[" + fragment.citationKey() + "]")
			.distinct()
			.collect(Collectors.joining(" "));
		String first = "本地原生统一对话链路已完成。";
		String second = citations.isBlank() ? "当前没有可引用的授权上下文。" : "已使用授权来源：" + citations;
		return Flux.just(new ModelChunk(request.runId(), 0, first, false, ""),
				new ModelChunk(request.runId(), 1, second, false, ""),
				new ModelChunk(request.runId(), 2, "", true, "STOP"));
	}

}
