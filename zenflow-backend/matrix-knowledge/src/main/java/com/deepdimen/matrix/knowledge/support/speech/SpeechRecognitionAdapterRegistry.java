package com.deepdimen.matrix.knowledge.support.speech;

import java.util.List;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;

@Component
public class SpeechRecognitionAdapterRegistry {

	private final List<SpeechRecognitionAdapter> adapters;

	public SpeechRecognitionAdapterRegistry(List<SpeechRecognitionAdapter> adapters) {
		this.adapters = List.copyOf(adapters);
	}

	public VoiceTranscriptionDTO transcribe(byte[] audio, AiModelEntity model) {
		List<SpeechRecognitionAdapter> matchingAdapters = adapters.stream()
			.filter(candidate -> candidate.supports(model))
			.toList();
		if (matchingAdapters.isEmpty()) {
			throw new SpeechRecognitionException("ASR_PROVIDER_UNSUPPORTED",
					"当前语音模型 Provider 暂不支持语音识别: " + model.getProvider());
		}
		if (matchingAdapters.size() > 1) {
			throw new SpeechRecognitionException("ASR_PROVIDER_ADAPTER_CONFLICT",
					"当前语音模型 Provider 匹配到多个识别适配器: " + model.getProvider());
		}
		return matchingAdapters.get(0).transcribe(audio, model);
	}

}
