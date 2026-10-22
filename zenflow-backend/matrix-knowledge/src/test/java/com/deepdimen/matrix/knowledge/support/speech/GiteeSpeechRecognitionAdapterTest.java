package com.deepdimen.matrix.knowledge.support.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import com.deepdimen.matrix.knowledge.dto.GiteeAiAudioTranscriptionDTO;
import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.service.AiAssistantGiteeService;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;

class GiteeSpeechRecognitionAdapterTest {

	private final ModelProvider modelProvider = mock(ModelProvider.class);

	private final AiAssistantGiteeService assistant = mock(AiAssistantGiteeService.class);

	private final GiteeSpeechRecognitionAdapter adapter = new GiteeSpeechRecognitionAdapter(modelProvider);

	@Test
	void normalizesTheGiteeResponseAndUploadsWav() {
		AiModelEntity model = model();
		GiteeAiAudioTranscriptionDTO.Response response = new GiteeAiAudioTranscriptionDTO.Response();
		response.setText("检查完成");
		response.setLanguage("zh");
		response.setDuration(1.25);
		when(modelProvider.getGiteeAssistant(model.getName())).thenReturn(assistant);
		when(assistant.audioTranscription(any(), eq(model.getModelName()))).thenReturn(response);

		VoiceTranscriptionDTO result = adapter.transcribe(new byte[] { 1, 2, 3 }, model);

		assertEquals("检查完成", result.getText());
		assertEquals("zh", result.getLanguage());
		assertEquals(1_250L, result.getDurationMs());
		assertEquals(model.getProvider(), result.getProvider());
		assertEquals(model.getModelName(), result.getModel());
		ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
		verify(assistant).audioTranscription(file.capture(), eq(model.getModelName()));
		assertEquals("audio/wav", file.getValue().getContentType());
	}

	@Test
	void mapsProviderTransportFailureToAStableCode() {
		AiModelEntity model = model();
		when(modelProvider.getGiteeAssistant(model.getName())).thenReturn(assistant);
		when(assistant.audioTranscription(any(), eq(model.getModelName())))
			.thenThrow(new IllegalStateException("timeout"));

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> adapter.transcribe(new byte[] { 1 }, model));

		assertEquals("ASR_PROVIDER_FAILED", error.getCode());
	}

	private static AiModelEntity model() {
		AiModelEntity model = new AiModelEntity();
		model.setName("gitee-asr");
		model.setProvider(ModelSupportEnums.GITEE_SENSE_VOICE_SMALL.getProvider());
		model.setModelName("SenseVoiceSmall");
		return model;
	}

}
