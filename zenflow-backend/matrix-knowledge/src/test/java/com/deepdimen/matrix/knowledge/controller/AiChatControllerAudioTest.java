package com.deepdimen.matrix.knowledge.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.service.AiChartGenerateService;
import com.deepdimen.matrix.knowledge.service.AiChatRecordService;
import com.deepdimen.matrix.knowledge.service.AiGenerateService;
import com.deepdimen.matrix.knowledge.service.ChatService;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionException;

class AiChatControllerAudioTest {

	private final AiGenerateService generateService = mock(AiGenerateService.class);

	private final AiChatController controller = new AiChatController(mock(ChatService.class),
			mock(AiChatRecordService.class), generateService, mock(AiChartGenerateService.class));

	@Test
	void forwardsTheSafeCorrelationIdAndKeepsTheLegacyStringEnvelope() {
		byte[] audio = new byte[] { 1 };
		String encoded = "{\"text\":\"完成\",\"traceId\":\"mobile-123\"}";
		when(generateService.generateTextByAudio(audio, "mobile-123")).thenReturn(encoded);

		R<String> response = controller.generateTextByAudio(audio, "mobile-123");

		assertEquals(0, response.getCode());
		assertEquals(encoded, response.getData());
		verify(generateService).generateTextByAudio(audio, "mobile-123");
	}

	@Test
	void keepsTheStableSpeechErrorCodeInRStringData() {
		byte[] audio = new byte[] { 1 };
		when(generateService.generateTextByAudio(audio, "mobile-123"))
			.thenThrow(new SpeechRecognitionException("AUDIO_FORMAT_INVALID", "格式错误"));

		R<String> response = controller.generateTextByAudio(audio, "mobile-123");

		assertEquals(1, response.getCode());
		assertEquals("AUDIO_FORMAT_INVALID", response.getData());
		assertEquals("格式错误", response.getMsg());
	}

}
