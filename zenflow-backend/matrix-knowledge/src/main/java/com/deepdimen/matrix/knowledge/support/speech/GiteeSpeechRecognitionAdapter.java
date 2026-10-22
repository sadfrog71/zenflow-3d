package com.deepdimen.matrix.knowledge.support.speech;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.knowledge.dto.GiteeAiAudioTranscriptionDTO;
import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GiteeSpeechRecognitionAdapter implements SpeechRecognitionAdapter {

	private final ModelProvider modelProvider;

	@Override
	public boolean supports(AiModelEntity model) {
		return model != null && StrUtil.equalsIgnoreCase(ModelSupportEnums.GITEE_SENSE_VOICE_SMALL.getProvider(),
				model.getProvider());
	}

	@Override
	public VoiceTranscriptionDTO transcribe(byte[] audio, AiModelEntity model) {
		GiteeAiAudioTranscriptionDTO.Response response;
		try {
			response = modelProvider.getGiteeAssistant(model.getName())
				.audioTranscription(SpeechRecognitionSupport.wavFile(audio), model.getModelName());
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_FAILED", "Gitee 语音识别调用失败", exception);
		}
		if (response == null || StrUtil.isBlank(response.getText())) {
			throw new SpeechRecognitionException("ASR_EMPTY_RESULT", "语音识别未返回文字");
		}
		return VoiceTranscriptionDTO.builder()
			.text(response.getText())
			.language(response.getLanguage())
			.durationMs(SpeechRecognitionSupport.durationMs(response.getDuration()))
			.provider(model.getProvider())
			.model(model.getModelName())
			.build();
	}

}
