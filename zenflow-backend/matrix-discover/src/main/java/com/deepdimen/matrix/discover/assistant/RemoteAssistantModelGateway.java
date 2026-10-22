package com.deepdimen.matrix.discover.assistant;

import java.time.Duration;
import java.util.regex.Pattern;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.generation.AssistantModelPolicy;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.deepdimen.matrix.discover.assistant.AssistantModelDelegationSigner.SignedModelDelegation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/** Cloud adapter. The target is a logical service ID, never a database URL. */
@Component
@ConditionalOnProperty(name = "matrix.assistant.model.gateway", havingValue = "remote")
public class RemoteAssistantModelGateway implements AssistantModelGateway {

	private static final Logger log = LoggerFactory.getLogger(RemoteAssistantModelGateway.class);

	private static final Pattern SERVICE_ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");

	private static final String CONTRACT_HEADER = "X-Assistant-Contract-Version";

	private static final String DELEGATION_HEADER = "X-Assistant-Delegation";

	private static final String CORRELATION_HEADER = "X-Correlation-ID";

	private final WebClient webClient;

	private final AssistantInternalRequestHasher requestHasher;

	private final AssistantModelDelegationSigner delegationSigner;

	private final String serviceId;

	public RemoteAssistantModelGateway(
			@Qualifier("assistantLoadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
			ObjectMapper objectMapper, AssistantModelDelegationSigner delegationSigner,
			@Value("${matrix.assistant.model.service-id:matrix-knowledge}") String serviceId) {
		if (serviceId == null || !SERVICE_ID.matcher(serviceId).matches()) {
			throw new IllegalArgumentException("MODEL_SERVICE_ID_NOT_TRUSTED");
		}
		this.webClient = webClientBuilder.baseUrl("http://" + serviceId).build();
		this.requestHasher = new AssistantInternalRequestHasher(objectMapper);
		this.delegationSigner = delegationSigner;
		this.serviceId = serviceId;
	}

	@Override
	public ResolvedAssistantModel resolve(ModelResolutionRequest request) {
		requireContractVersion(request.contractVersion());
		String serializedBody = requestHasher.serialize(request);
		String bodyHash = requestHasher.hashSerializedBody(serializedBody);
		SignedModelDelegation credentials = delegationSigner.sign(request.identity(), request.runId(), serviceId,
				bodyHash, Purpose.MODEL_RESOLVE);
		ResolvedAssistantModel resolved = webClient.post()
			.uri("/inner/assistant/models/resolve")
			.headers(headers -> secureHeaders(headers, credentials, request.contractVersion(),
					request.identity().correlationId()))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(serializedBody)
			.retrieve()
			.onStatus(status -> status.isError(),
					response -> response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
						log.warn("Remote model resolution failed: status={}, body={}", response.statusCode(),
								safeBody(body));
						return new AssistantModelException("REMOTE_MODEL_RESOLUTION_FAILED", "原生模型解析服务不可用");
					}))
			.bodyToMono(ResolvedAssistantModel.class)
			.blockOptional(Duration.ofSeconds(5))
			.orElseThrow(() -> new AssistantModelException("REMOTE_MODEL_RESOLUTION_FAILED", "原生模型解析服务未返回结果"));
		requireContractVersion(resolved.contractVersion());
		return resolved;
	}

	@Override
	public Flux<ModelChunk> stream(AssistantModelRequest request) {
		requireContractVersion(request.contractVersion());
		AssistantModelPolicy.validateForStreaming(request);
		String serializedBody = requestHasher.serialize(request);
		String bodyHash = requestHasher.hashSerializedBody(serializedBody);
		SignedModelDelegation credentials = delegationSigner.sign(request.identity(), request.runId(), serviceId,
				bodyHash, Purpose.MODEL_STREAM);
		return webClient.post()
			.uri("/inner/assistant/models/stream")
			.headers(headers -> secureHeaders(headers, credentials, request.contractVersion(), request.correlationId()))
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(serializedBody)
			.retrieve()
			.onStatus(status -> status.isError(),
					response -> response.bodyToMono(String.class)
						.map(ignored -> new AssistantModelException("REMOTE_MODEL_STREAM_FAILED", "原生模型流式服务不可用")))
			.bodyToFlux(ModelChunk.class)
			.handle((chunk, sink) -> {
				if (!request.runId().equals(chunk.runId())) {
					sink.error(new AssistantModelException("REMOTE_MODEL_STREAM_INVALID", "原生模型流响应与当前运行不匹配"));
					return;
				}
				sink.next(chunk);
			});
	}

	private void requireContractVersion(String contractVersion) {
		if (!AssistantModelContract.VERSION.equals(contractVersion)) {
			throw new AssistantModelException("MODEL_CONTRACT_INCOMPATIBLE", "原生模型契约版本不兼容");
		}
	}

	private void secureHeaders(HttpHeaders headers, SignedModelDelegation credentials, String contractVersion,
			String correlationId) {
		headers.set(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER,
				"Bearer " + credentials.serviceBearerToken());
		headers.set(DELEGATION_HEADER, credentials.delegationToken());
		headers.set(CONTRACT_HEADER, contractVersion);
		headers.set(CORRELATION_HEADER, safeCorrelationId(correlationId));
	}

	private String safeCorrelationId(String value) {
		if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
			throw new AssistantModelException("INVALID_CORRELATION_ID", "诊断标识无效");
		}
		return value;
	}

	private String safeBody(String value) {
		if (value == null)
			return "";
		String sanitized = value.replaceAll("[\\r\\n\\t]", " ");
		return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
	}

}
