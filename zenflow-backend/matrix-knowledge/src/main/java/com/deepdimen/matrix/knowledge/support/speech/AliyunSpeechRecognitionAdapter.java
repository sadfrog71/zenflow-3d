package com.deepdimen.matrix.knowledge.support.speech;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;

/**
 * Alibaba Cloud Model Studio Qwen3 ASR adapter using the OpenAI-compatible
 * chat-completions protocol. Audio is sent as an in-memory WAV data URI, so credentials
 * and recordings stay behind the server-side tenant boundary.
 */
@Component
@RequiredArgsConstructor
public class AliyunSpeechRecognitionAdapter implements SpeechRecognitionAdapter {

	private static final String COMPATIBLE_API_PATH = "/compatible-mode/v1";

	private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

	private final ModelEndpointPolicy endpointPolicy;

	private final AliyunSpeechRecognitionTransport transport;

	@Override
	public boolean supports(AiModelEntity model) {
		return model != null
				&& StrUtil.equalsIgnoreCase(ModelSupportEnums.ALIYUN_QWEN3_ASR_FLASH.getProvider(), model.getProvider())
				&& StrUtil.equalsIgnoreCase(SpeechRecognitionSupport.ALIYUN_QWEN_OPENAI_CHAT_AUDIO,
						SpeechRecognitionSupport.asrProtocol(model));
	}

	@Override
	public VoiceTranscriptionDTO transcribe(byte[] audio, AiModelEntity model) {
		ProviderConfiguration configuration = configuration(model);
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + model.getApiKey());
		headers.put("Content-Type", "application/json");

		JSONObject inputAudio = JSONUtil.createObj()
			.set("data", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(audio));
		JSONArray content = JSONUtil.createArray()
			.put(JSONUtil.createObj().set("type", "input_audio").set("input_audio", inputAudio));
		JSONArray messages = JSONUtil.createArray()
			.put(JSONUtil.createObj().set("role", "user").set("content", content));
		JSONObject options = JSONUtil.createObj().set("enable_itn", configuration.enableItn());
		if (StrUtil.isNotBlank(configuration.language())) {
			options.set("language", configuration.language());
		}
		JSONObject payload = JSONUtil.createObj()
			.set("model", model.getModelName())
			.set("messages", messages)
			.set("stream", false)
			.set("asr_options", options);

		AliyunSpeechRecognitionTransport.Response response;
		try {
			response = transport.post(configuration.endpoint(), headers, payload.toString());
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_FAILED", "阿里云百炼语音识别调用失败", exception);
		}
		if (response == null || response.httpStatus() < 200 || response.httpStatus() >= 300) {
			throw new SpeechRecognitionException("ASR_PROVIDER_REJECTED", "阿里云百炼语音识别请求未成功");
		}

		try {
			JSONObject body = JSONUtil.parseObj(response.body());
			JSONArray choices = body.getJSONArray("choices");
			if (choices == null || choices.isEmpty()) {
				throw new IllegalArgumentException("choices missing");
			}
			JSONObject message = choices.getJSONObject(0).getJSONObject("message");
			if (message == null) {
				throw new IllegalArgumentException("message missing");
			}
			String text = message.getStr("content");
			if (StrUtil.isBlank(text)) {
				throw new SpeechRecognitionException("ASR_EMPTY_RESULT", "语音识别未返回文字");
			}
			return VoiceTranscriptionDTO.builder()
				.text(text)
				.language(configuration.language())
				.provider(model.getProvider())
				.model(model.getModelName())
				.build();
		}
		catch (SpeechRecognitionException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_RESPONSE_INVALID", "阿里云百炼语音识别响应无效", exception);
		}
	}

	private ProviderConfiguration configuration(AiModelEntity model) {
		if (model == null || StrUtil.isBlank(model.getModelName()) || StrUtil.isBlank(model.getApiKey())) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "阿里云百炼语音模型配置不完整");
		}
		if (!supports(model)) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "阿里云百炼语音模型协议配置不匹配");
		}
		final URI endpoint;
		try {
			endpoint = chatCompletionsEndpoint(endpointPolicy.validate(model.getBaseUrl()));
		}
		catch (IllegalArgumentException exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "阿里云百炼语音服务地址无效", exception);
		}
		if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
			throw new SpeechRecognitionException("ASR_PROVIDER_CONFIG_INVALID", "阿里云百炼语音服务必须使用 HTTPS");
		}

		String language = null;
		boolean enableItn = false;
		if (JSONUtil.isTypeJSON(model.getExtData())) {
			JSONObject extData = JSONUtil.parseObj(model.getExtData());
			language = extData.getStr("language");
			enableItn = Boolean.TRUE.equals(extData.getBool("enableItn"));
		}
		return new ProviderConfiguration(endpoint, language, enableItn);
	}

	private URI chatCompletionsEndpoint(URI baseUrl) {
		String path = StrUtil.blankToDefault(baseUrl.getPath(), "").replaceAll("/+$", "");
		if (path.endsWith(COMPATIBLE_API_PATH)) {
			path += CHAT_COMPLETIONS_PATH;
		}
		else if (!path.endsWith(COMPATIBLE_API_PATH + CHAT_COMPLETIONS_PATH)) {
			throw new IllegalArgumentException("仅支持百炼 OpenAI 兼容接口地址");
		}
		try {
			return new URI(baseUrl.getScheme(), null, baseUrl.getHost(), baseUrl.getPort(), path, null, null);
		}
		catch (URISyntaxException exception) {
			throw new IllegalArgumentException("百炼语音服务地址无效", exception);
		}
	}

	private record ProviderConfiguration(URI endpoint, String language, boolean enableItn) {
	}

}
