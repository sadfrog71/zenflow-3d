package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiDashscopeAssistantService;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionAdapterRegistry;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionException;

import cn.hutool.json.JSONUtil;

class AiGenerateServiceImplAudioTest {

	private final AiDashscopeAssistantService dashscopeService = mock(AiDashscopeAssistantService.class);

	private final AiModelMapper modelMapper = mock(AiModelMapper.class);

	private final ModelProvider modelProvider = mock(ModelProvider.class);

	private final SpeechRecognitionAdapterRegistry adapterRegistry = mock(SpeechRecognitionAdapterRegistry.class);

	private final AiGenerateServiceImpl service = new AiGenerateServiceImpl(dashscopeService, modelMapper,
			modelProvider, adapterRegistry);

	@Test
	void selectsOnlyTheDefaultAsrModel() {
		AiModelEntity nonDefault = voiceModel(1L, "Siliconflow", "asr-old", "0", "1");
		AiModelEntity defaultAsr = voiceModel(2L, "Ark", "doubao-asr", "1", "1");
		byte[] audio = validWav();
		VoiceTranscriptionDTO transcription = VoiceTranscriptionDTO.builder()
			.text("测试文字")
			.provider("Ark")
			.model("doubao-asr")
			.build();

		when(modelMapper.selectList(any())).thenReturn(List.of(nonDefault, defaultAsr));
		when(adapterRegistry.transcribe(audio, defaultAsr)).thenReturn(transcription);

		String result = service.generateTextByAudio(audio);

		assertEquals("测试文字", JSONUtil.parseObj(result).getStr("text"));
		assertEquals("Ark", JSONUtil.parseObj(result).getStr("provider"));
		verify(adapterRegistry).transcribe(same(audio), same(defaultAsr));
	}

	@Test
	void includesTheRequestCorrelationIdInTheNormalizedResult() {
		AiModelEntity defaultAsr = voiceModel(2L, "Siliconflow", "asr", "1", "1");
		byte[] audio = validWav();
		when(modelMapper.selectList(any())).thenReturn(List.of(defaultAsr));
		when(adapterRegistry.transcribe(audio, defaultAsr))
			.thenReturn(VoiceTranscriptionDTO.builder().text("完成").provider("Siliconflow").model("asr").build());

		String result = service.generateTextByAudio(audio, "mobile-correlation-123");

		assertEquals("mobile-correlation-123", JSONUtil.parseObj(result).getStr("traceId"));
	}

	@Test
	void rejectsMissingDefaultAsrModel() {
		when(modelMapper.selectList(any()))
			.thenReturn(List.of(voiceModel(1L, "Siliconflow", "asr-old", "0", "1")));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> service.generateTextByAudio(validWav()));

		assertEquals("ASR_MODEL_NOT_CONFIGURED", error.getCode());
	}

	@Test
	void rejectsMultipleDefaultAsrModelsInsteadOfPickingOneArbitrarily() {
		when(modelMapper.selectList(any())).thenReturn(List.of(voiceModel(1L, "Siliconflow", "asr-a", "1", "1"),
				voiceModel(2L, "Gitee", "asr-b", "1", "1")));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> service.generateTextByAudio(validWav()));

		assertEquals("ASR_MODEL_CONFLICT", error.getCode());
	}

	@Test
	void rejectsEmptyAudioBeforeCallingAProvider() {
		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> service.generateTextByAudio(new byte[0]));

		assertEquals("AUDIO_EMPTY", error.getCode());
	}

	@Test
	void rejectsInvalidWavBeforeReadingTenantModels() {
		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> service.generateTextByAudio(new byte[] { 1, 2, 3 }));

		assertEquals("AUDIO_FORMAT_INVALID", error.getCode());
		verifyNoMoreInteractions(modelMapper, adapterRegistry);
	}

	private static AiModelEntity voiceModel(Long id, String provider, String modelName, String defaultModel,
			String voiceType) {
		AiModelEntity model = new AiModelEntity();
		model.setId(id);
		model.setModelType("Voice");
		model.setName(provider + "-" + modelName);
		model.setProvider(provider);
		model.setModelName(modelName);
		model.setDefaultModel(defaultModel);
		model.setExtData("{\"voiceType\":\"" + voiceType + "\"}");
		return model;
	}

	private static byte[] validWav() {
		int dataBytes = 320;
		ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		putAscii(buffer, "RIFF");
		buffer.putInt(36 + dataBytes);
		putAscii(buffer, "WAVE");
		putAscii(buffer, "fmt ");
		buffer.putInt(16);
		buffer.putShort((short) 1);
		buffer.putShort((short) 1);
		buffer.putInt(16_000);
		buffer.putInt(32_000);
		buffer.putShort((short) 2);
		buffer.putShort((short) 16);
		putAscii(buffer, "data");
		buffer.putInt(dataBytes);
		return buffer.array();
	}

	private static void putAscii(ByteBuffer buffer, String value) {
		for (int index = 0; index < value.length(); index++) {
			buffer.put((byte) value.charAt(index));
		}
	}

}
