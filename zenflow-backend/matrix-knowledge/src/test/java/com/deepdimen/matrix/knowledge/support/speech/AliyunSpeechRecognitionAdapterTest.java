package com.deepdimen.matrix.knowledge.support.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

class AliyunSpeechRecognitionAdapterTest {

	private final ModelEndpointPolicy endpointPolicy = mock(ModelEndpointPolicy.class);

	private final AliyunSpeechRecognitionTransport transport = mock(AliyunSpeechRecognitionTransport.class);

	private final AliyunSpeechRecognitionAdapter adapter = new AliyunSpeechRecognitionAdapter(endpointPolicy,
			transport);

	@Test
	void sendsOpenAiCompatibleAudioPayloadAndNormalizesResponse() {
		AiModelEntity model = model();
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any())).thenReturn(new AliyunSpeechRecognitionTransport.Response(200,
				"{\"choices\":[{\"message\":{\"content\":\"已完成水压巡检\"}}]}"));

		VoiceTranscriptionDTO result = adapter.transcribe("wav".getBytes(StandardCharsets.UTF_8), model);

		assertEquals("已完成水压巡检", result.getText());
		assertEquals("zh", result.getLanguage());
		assertEquals("Aliyun", result.getProvider());
		assertEquals("qwen3-asr-flash", result.getModel());

		ArgumentCaptor<URI> endpoint = ArgumentCaptor.forClass(URI.class);
		ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(transport).post(endpoint.capture(), headers.capture(), body.capture());
		assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
				endpoint.getValue().toString());
		assertEquals("Bearer api-key", headers.getValue().get("Authorization"));
		assertEquals("application/json", headers.getValue().get("Content-Type"));

		JSONObject payload = JSONUtil.parseObj(body.getValue());
		assertEquals("qwen3-asr-flash", payload.getStr("model"));
		assertFalse(payload.getBool("stream"));
		JSONObject options = payload.getJSONObject("asr_options");
		assertEquals("zh", options.getStr("language"));
		assertTrue(options.getBool("enable_itn"));
		JSONArray messages = payload.getJSONArray("messages");
		JSONArray content = messages.getJSONObject(0).getJSONArray("content");
		JSONObject inputAudio = content.getJSONObject(0).getJSONObject("input_audio");
		assertEquals("data:audio/wav;base64,d2F2", inputAudio.getStr("data"));
	}

	@Test
	void acceptsFullChatCompletionsEndpointAndSnapshotModel() {
		AiModelEntity model = model();
		model.setModelName("qwen3-asr-flash-2026-04-01");
		model.setBaseUrl("https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions");
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any())).thenReturn(new AliyunSpeechRecognitionTransport.Response(200,
				"{\"choices\":[{\"message\":{\"content\":\"完成\"}}]}"));

		adapter.transcribe(new byte[] { 1 }, model);

		verify(transport).post(org.mockito.ArgumentMatchers.eq(URI.create(model.getBaseUrl())), any(), any());
	}

	@Test
	void supportsOnlyAliyunQwenOpenAiChatProtocol() {
		assertTrue(adapter.supports(model()));
		AiModelEntity other = model();
		other.setProvider("OpenAI");
		assertFalse(adapter.supports(other));

		AiModelEntity missingProtocol = model();
		missingProtocol.setExtData("{\"voiceType\":\"1\"}");
		assertFalse(adapter.supports(missingProtocol));

		AiModelEntity paraformerProtocol = model();
		paraformerProtocol.setExtData("{\"voiceType\":\"1\",\"asrProtocol\":\"aliyun-paraformer-transcription\"}");
		assertFalse(adapter.supports(paraformerProtocol));
	}

	@Test
	void forwardsTheConfiguredAliyunAsrModelWithoutAnApplicationWhitelist() {
		AiModelEntity model = model();
		model.setModelName("configured-aliyun-asr-model");
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any())).thenReturn(new AliyunSpeechRecognitionTransport.Response(200,
				"{\"choices\":[{\"message\":{\"content\":\"完成\"}}]}"));

		adapter.transcribe(new byte[] { 1 }, model);

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(transport).post(any(), any(), body.capture());
		assertEquals("configured-aliyun-asr-model", JSONUtil.parseObj(body.getValue()).getStr("model"));
	}

	@Test
	void rejectsIncompleteCredentialsBeforeNetworkCall() {
		AiModelEntity model = model();
		model.setApiKey(null);

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_CONFIG_INVALID", error.getCode());
		verify(transport, never()).post(any(), any(), any());
	}

	@Test
	void rejectsProtocolMismatchBeforeNetworkCall() {
		AiModelEntity model = model();
		model.setExtData("{\"voiceType\":\"1\",\"asrProtocol\":\"aliyun-paraformer-transcription\"}");

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_CONFIG_INVALID", error.getCode());
		verify(transport, never()).post(any(), any(), any());
	}

	@Test
	void rejectsNonHttpsEndpointsBeforeSendingCredentials() {
		AiModelEntity model = model();
		when(endpointPolicy.validate(model.getBaseUrl()))
			.thenReturn(URI.create("http://dashscope.aliyuncs.com/compatible-mode/v1"));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_CONFIG_INVALID", error.getCode());
		verify(transport, never()).post(any(), any(), any());
	}

	@Test
	void mapsProviderFailureWithoutExposingResponseBody() {
		AiModelEntity model = model();
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any()))
			.thenReturn(new AliyunSpeechRecognitionTransport.Response(401, "{\"secret\":\"must-not-leak\"}"));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_REJECTED", error.getCode());
		assertFalse(error.getMessage().contains("must-not-leak"));
	}

	@Test
	void mapsTransportFailureToStableProviderError() {
		AiModelEntity model = model();
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any())).thenThrow(new IllegalStateException("network detail"));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_FAILED", error.getCode());
		assertFalse(error.getMessage().contains("network detail"));
	}

	@Test
	void mapsMalformedAndEmptyResponses() {
		AiModelEntity model = model();
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any()))
			.thenReturn(new AliyunSpeechRecognitionTransport.Response(200, "not-json"))
			.thenReturn(new AliyunSpeechRecognitionTransport.Response(200,
					"{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));

		SpeechRecognitionException malformed = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));
		SpeechRecognitionException empty = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_RESPONSE_INVALID", malformed.getCode());
		assertEquals("ASR_EMPTY_RESULT", empty.getCode());
	}

	private static AiModelEntity model() {
		AiModelEntity model = new AiModelEntity();
		model.setName("aliyun-qwen3-asr");
		model.setProvider("Aliyun");
		model.setModelName("qwen3-asr-flash");
		model.setApiKey("api-key");
		model.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
		model.setExtData("{\"voiceType\":\"1\",\"asrProtocol\":\"aliyun-qwen-openai-chat-audio\","
				+ "\"language\":\"zh\",\"enableItn\":true}");
		return model;
	}

}
