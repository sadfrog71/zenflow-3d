package com.deepdimen.matrix.knowledge.support.speech;

import com.deepdimen.matrix.admin.api.utils.ByteArrayMultipartFile;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;

import cn.hutool.json.JSONUtil;

/**
 * Shared normalization helpers for speech recognition adapters and model selection.
 */
public final class SpeechRecognitionSupport {

	public static final String ALIYUN_QWEN_OPENAI_CHAT_AUDIO = "aliyun-qwen-openai-chat-audio";

	private SpeechRecognitionSupport() {
	}

	public static ByteArrayMultipartFile wavFile(byte[] audio) {
		return new ByteArrayMultipartFile("upload.wav", "upload.wav", "audio/wav", audio);
	}

	public static Long durationMs(Double durationSeconds) {
		return durationSeconds == null ? null : Math.round(durationSeconds * 1000);
	}

	public static String voiceType(AiModelEntity model) {
		if (model == null || !JSONUtil.isTypeJSON(model.getExtData())) {
			return null;
		}
		return JSONUtil.parseObj(model.getExtData()).getStr("voiceType");
	}

	public static String asrProtocol(AiModelEntity model) {
		if (model == null || !JSONUtil.isTypeJSON(model.getExtData())) {
			return null;
		}
		return JSONUtil.parseObj(model.getExtData()).getStr("asrProtocol");
	}

}
