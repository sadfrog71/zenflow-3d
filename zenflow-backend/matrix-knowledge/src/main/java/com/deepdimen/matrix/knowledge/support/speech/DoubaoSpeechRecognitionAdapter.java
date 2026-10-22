package com.deepdimen.matrix.knowledge.support.speech;

import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;

/**
 * Adapter for Volcengine/Doubao big-model recording-file flash recognition.
 * Credentials remain in the tenant-scoped ai_model row and are never returned
 * to mobile clients.
 */
@Component
@RequiredArgsConstructor
public class DoubaoSpeechRecognitionAdapter implements SpeechRecognitionAdapter {

	static final String DEFAULT_RESOURCE_ID = "volc.bigasr.auc_turbo";
	static final String SUCCESS_STATUS = "20000000";

	private final ModelEndpointPolicy endpointPolicy;

	private final DoubaoSpeechRecognitionTransport transport;

	@Override
	public boolean supports(AiModelEntity model) {
		return model != null && (StrUtil.equalsIgnoreCase(ModelSupportEnums.DOUBAO_BIGMODEL_ASR.getProvider(),
				model.getProvider()) || StrUtil.equalsIgnoreCase("Volcengine", model.getProvider()));
	}

	@Override
	public VoiceTranscriptionDTO transcribe(byte[] audio, AiModelEntity model) {
		ProviderConfiguration configuration = configuration(model);
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		if (StrUtil.isBlank(model.getSecretKey())) {
			headers.put("X-Api-Key", model.getApiKey());
		}
		else {
			headers.put("X-Api-App-Key", model.getApiKey());
			headers.put("X-Api-Access-Key", model.getSecretKey());
		}
		headers.put("X-Api-Resource-Id", configuration.resourceId());
		headers.put("X-Api-Request-Id", UUID.randomUUID().toString());
		headers.put("X-Api-Sequence", "-1");

		JSONObject payload = JSONUtil.createObj()
			.set("user", JSONUtil.createObj().set("uid", model.getApiKey()))
			.set("audio", JSONUtil.createObj().set("data", Base64.getEncoder().encodeToString(audio)))
			.set("request", JSONUtil.createObj().set("model_name", model.getModelName()));

		DoubaoSpeechRecognitionTransport.Response response;
		try {
			response = transport.post(configuration.endpoint(), headers, payload.toString());
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_FAILED", "豆包语音识别调用失败", exception);
		}
		if (response == null || response.httpStatus() < 200 || response.httpStatus() >= 300
				|| !SUCCESS_STATUS.equals(response.providerStatus())) {
			throw new SpeechRecognitionException("ASR_PROVIDER_REJECTED", "豆包语音识别请求未成功");
		}

		try {
			JSONObject body = JSONUtil.parseObj(response.body());
			JSONObject result = body.getJSONObject("result");
			String text = result == null ? null : result.getStr("text");
			if (StrUtil.isBlank(text)) {
				throw new SpeechRecognitionException("ASR_EMPTY_RESULT", "语音识别未返回文字");
			}
			JSONObject audioInfo = body.getJSONObject("audio_info");
			return VoiceTranscriptionDTO.builder()
				.text(text)
				.durationMs(audioInfo == null ? null : audioInfo.getLong("duration"))
				.provider(model.getProvider())
				.model(model.getModelName())
				.build();
		}
		catch (SpeechRecognitionException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_RESPONSE_INVALID", "豆包语音识别响应无效", exception);
		}
	}

	private ProviderConfiguration configuration(AiModelEntity model) {
		if (model == null || StrUtil.isBlank(model.getModelName()) || StrUtil.isBlank(model.getApiKey())) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "豆包语音模型配置不完整");
		}
		final URI endpoint;
		try {
			endpoint = endpointPolicy.validate(model.getBaseUrl());
		}
		catch (IllegalArgumentException exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "豆包语音服务地址无效", exception);
		}
		if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "豆包语音服务必须使用 HTTPS");
		}
		String resourceId = DEFAULT_RESOURCE_ID;
		if (JSONUtil.isTypeJSON(model.getExtData())) {
			resourceId = StrUtil.blankToDefault(JSONUtil.parseObj(model.getExtData()).getStr("resourceId"),
					DEFAULT_RESOURCE_ID);
		}
		return new ProviderConfiguration(endpoint, resourceId);
	}

	private record ProviderConfiguration(URI endpoint, String resourceId) {
	}

}
