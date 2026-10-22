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
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

class DoubaoSpeechRecognitionAdapterTest {

	private final ModelEndpointPolicy endpointPolicy = mock(ModelEndpointPolicy.class);

	private final DoubaoSpeechRecognitionTransport transport = mock(DoubaoSpeechRecognitionTransport.class);

	private final DoubaoSpeechRecognitionAdapter adapter = new DoubaoSpeechRecognitionAdapter(endpointPolicy,
			transport);

	@Test
	void sendsConfiguredModelAndNormalizesFlashRecognitionResponse() {
		AiModelEntity model = model();
		URI endpoint = URI.create(model.getBaseUrl());
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(endpoint);
		when(transport.post(any(), any(), any())).thenReturn(new DoubaoSpeechRecognitionTransport.Response(200,
				"20000000", "OK", "vendor-log", "{\"audio_info\":{\"duration\":2499},\"result\":{\"text\":\"检查完成\"}}"));

		VoiceTranscriptionDTO result = adapter.transcribe("wav".getBytes(StandardCharsets.UTF_8), model);

		assertEquals("检查完成", result.getText());
		assertEquals(2_499L, result.getDurationMs());
		assertEquals(model.getProvider(), result.getProvider());
		assertEquals(model.getModelName(), result.getModel());
		ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(transport).post(org.mockito.ArgumentMatchers.eq(endpoint), headers.capture(), body.capture());
		assertEquals(model.getApiKey(), headers.getValue().get("X-Api-Key"));
		assertFalse(headers.getValue().containsKey("X-Api-App-Key"));
		assertFalse(headers.getValue().containsKey("X-Api-Access-Key"));
		assertEquals("volc.bigasr.custom", headers.getValue().get("X-Api-Resource-Id"));
		JSONObject payload = JSONUtil.parseObj(body.getValue());
		assertEquals(model.getApiKey(), payload.getJSONObject("user").getStr("uid"));
		assertEquals(model.getModelName(), payload.getJSONObject("request").getStr("model_name"));
		assertEquals("d2F2", payload.getJSONObject("audio").getStr("data"));
	}

	@Test
	void supportsLegacyConsoleAppAndAccessKeysWithoutSendingTheNewApiKeyHeader() {
		AiModelEntity model = model();
		model.setSecretKey("legacy-access-key");
		URI endpoint = URI.create(model.getBaseUrl());
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(endpoint);
		when(transport.post(any(), any(), any())).thenReturn(new DoubaoSpeechRecognitionTransport.Response(200,
				"20000000", "OK", "vendor-log", "{\"result\":{\"text\":\"完成\"}}"));

		adapter.transcribe(new byte[] { 1 }, model);

		ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
		verify(transport).post(org.mockito.ArgumentMatchers.eq(endpoint), headers.capture(), any());
		assertEquals(model.getApiKey(), headers.getValue().get("X-Api-App-Key"));
		assertEquals(model.getSecretKey(), headers.getValue().get("X-Api-Access-Key"));
		assertFalse(headers.getValue().containsKey("X-Api-Key"));
	}

	@Test
	void acceptsOnlyTheDedicatedDoubaoProviderAliases() {
		assertTrue(adapter.supports(model()));
		AiModelEntity volcengine = model();
		volcengine.setProvider("Volcengine");
		assertTrue(adapter.supports(volcengine));
		AiModelEntity other = model();
		other.setProvider("Ark");
		assertFalse(adapter.supports(other));
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
	void rejectsNonHttpsEndpointsBeforeSendingCredentials() {
		AiModelEntity model = model();
		URI endpoint = URI.create("http://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash");
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(endpoint);

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_CONFIG_INVALID", error.getCode());
		verify(transport, never()).post(any(), any(), any());
	}

	@Test
	void mapsProviderStatusFailureWithoutExposingTheResponseBody() {
		AiModelEntity model = model();
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));
		when(transport.post(any(), any(), any())).thenReturn(new DoubaoSpeechRecognitionTransport.Response(200,
				"45000001", "invalid", "vendor-log", "{\"secret\":\"must-not-leak\"}"));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_REJECTED", error.getCode());
		assertFalse(error.getMessage().contains("must-not-leak"));
	}

	private static AiModelEntity model() {
		AiModelEntity model = new AiModelEntity();
		model.setName("doubao-asr");
		model.setProvider(ModelSupportEnums.DOUBAO_BIGMODEL_ASR.getProvider());
		model.setModelName(ModelSupportEnums.DOUBAO_BIGMODEL_ASR.getCode());
		model.setApiKey("api-key");
		model.setBaseUrl("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash");
		model.setExtData("{\"voiceType\":\"1\",\"resourceId\":\"volc.bigasr.custom\"}");
		return model;
	}

}
