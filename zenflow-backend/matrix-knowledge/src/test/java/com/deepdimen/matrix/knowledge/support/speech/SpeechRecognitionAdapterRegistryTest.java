package com.deepdimen.matrix.knowledge.support.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

import cn.hutool.json.JSONUtil;

class SpeechRecognitionAdapterRegistryTest {

	@Test
	void delegatesToTheAdapterThatSupportsTheConfiguredModel() {
		SpeechRecognitionAdapter first = mock(SpeechRecognitionAdapter.class);
		SpeechRecognitionAdapter doubao = mock(SpeechRecognitionAdapter.class);
		AiModelEntity model = model("Ark", "doubao-asr");
		byte[] audio = new byte[] { 1 };
		VoiceTranscriptionDTO expected = VoiceTranscriptionDTO.builder().text("完成").build();
		when(first.supports(model)).thenReturn(false);
		when(doubao.supports(model)).thenReturn(true);
		when(doubao.transcribe(audio, model)).thenReturn(expected);

		SpeechRecognitionAdapterRegistry registry = new SpeechRecognitionAdapterRegistry(List.of(first, doubao));

		assertEquals(expected, registry.transcribe(audio, model));
		verify(doubao).transcribe(audio, model);
	}

	@Test
	void rejectsAnUnregisteredProviderWithAStableCode() {
		SpeechRecognitionAdapterRegistry registry = new SpeechRecognitionAdapterRegistry(List.of());

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> registry.transcribe(new byte[] { 1 }, model("Unknown", "future-asr")));

		assertEquals("ASR_PROVIDER_UNSUPPORTED", error.getCode());
	}

	@Test
	void rejectsOverlappingAdaptersInsteadOfDependingOnBeanOrder() {
		SpeechRecognitionAdapter first = mock(SpeechRecognitionAdapter.class);
		SpeechRecognitionAdapter second = mock(SpeechRecognitionAdapter.class);
		AiModelEntity model = model("Ark", "doubao-asr");
		when(first.supports(model)).thenReturn(true);
		when(second.supports(model)).thenReturn(true);
		SpeechRecognitionAdapterRegistry registry = new SpeechRecognitionAdapterRegistry(List.of(first, second));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> registry.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_ADAPTER_CONFLICT", error.getCode());
	}

	@Test
	void rejectsAliyunModelWithoutAnExplicitCompatibleProtocol() {
		AliyunSpeechRecognitionAdapter aliyun = new AliyunSpeechRecognitionAdapter(mock(ModelEndpointPolicy.class),
				mock(AliyunSpeechRecognitionTransport.class));
		SpeechRecognitionAdapterRegistry registry = new SpeechRecognitionAdapterRegistry(List.of(aliyun));
		AiModelEntity model = model("Aliyun", "paraformer-v2");
		model.setExtData(JSONUtil.createObj().set("voiceType", "1").toString());

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> registry.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_UNSUPPORTED", error.getCode());
	}

	private static AiModelEntity model(String provider, String modelName) {
		AiModelEntity model = new AiModelEntity();
		model.setProvider(provider);
		model.setModelName(modelName);
		return model;
	}

}
