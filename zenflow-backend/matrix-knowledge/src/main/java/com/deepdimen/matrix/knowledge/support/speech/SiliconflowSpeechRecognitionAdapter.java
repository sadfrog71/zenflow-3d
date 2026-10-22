package com.deepdimen.matrix.knowledge.support.speech;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SiliconflowSpeechRecognitionAdapter implements SpeechRecognitionAdapter {

	private final ModelProvider modelProvider;

	@Override
	public boolean supports(AiModelEntity model) {
		return model != null && StrUtil.equalsIgnoreCase(ModelSupportEnums.SILICONFLOW_SENSE_VOICE_SMALL.getProvider(),
				model.getProvider());
	}

	@Override
	public VoiceTranscriptionDTO transcribe(byte[] audio, AiModelEntity model) {
		String response;
		try {
			response = modelProvider.getSiliconflowAssistant(model.getName())
				.audioToText(SpeechRecognitionSupport.wavFile(audio), model.getModelName());
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_FAILED", "SiliconFlow 语音识别调用失败", exception);
		}
		try {
			JSONObject payload = JSONUtil.parseObj(response);
			String text = payload.getStr("text");
			if (StrUtil.isBlank(text)) {
				throw new SpeechRecognitionException("ASR_EMPTY_RESULT", "语音识别未返回文字");
			}
			return VoiceTranscriptionDTO.builder()
				.text(text)
				.language(payload.getStr("language"))
				.durationMs(SpeechRecognitionSupport.durationMs(payload.getDouble("duration")))
				.confidence(payload.getDouble("confidence"))
				.provider(model.getProvider())
				.model(model.getModelName())
				.build();
		}
		catch (SpeechRecognitionException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new SpeechRecognitionException("ASR_PROVIDER_RESPONSE_INVALID", "SiliconFlow 语音识别响应无效", exception);
		}
	}

}
