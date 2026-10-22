package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.AiGenerateDTO;
import com.deepdimen.matrix.knowledge.support.speech.SpeechCorrelationId;

import java.io.IOException;

/**
 * AI 生成式服务接口
 * <p>
 * 提供文本生成、语音识别、语音合成等AI生成能力， 支持多模态内容的生成和转换。
 *
 * @author caoyuan
 * @date 2024/6/17
 */
public interface AiGenerateService {

	/**
	 * 生成文本内容
	 * <p>
	 * 根据输入的提示词或上下文，使用AI模型生成相应的文本内容。
	 * @param generateDTO 生成请求参数，包含提示词、温度、最大长度等配置
	 * @return 生成的文本内容
	 */
	String generateText(AiGenerateDTO generateDTO);

	/**
	 * 选择并执行功能
	 * <p>
	 * 根据用户描述自动选择合适的功能或工具并执行， 实现智能化的功能路由。
	 * @param conversationId 会话ID，用于保持上下文连续性
	 * @param description 功能描述，说明需要执行的任务
	 * @return 功能执行结果
	 */
	String selectFunction(String conversationId, String description);

	/**
	 * 语音转文本（语音识别）
	 * <p>
	 * 将原始 PCM WAV 音频转换为文本。仅接受 16 kHz、16 bit、单声道音频，最大 60 秒且不超过 2 MiB。
	 * @param fileData PCM WAV 音频字节数组
	 * @return 识别出的文本内容
	 */
	default String generateTextByAudio(byte[] fileData) {
		return generateTextByAudio(fileData, SpeechCorrelationId.resolve(null));
	}

	/**
	 * 使用调用链 traceId 执行语音识别。
	 * @param fileData 音频文件的字节数组
	 * @param traceId 已校验的调用链标识
	 * @return 统一语音识别 JSON 字符串
	 */
	String generateTextByAudio(byte[] fileData, String traceId);

	/**
	 * 文本转语音（语音合成）
	 * <p>
	 * 将文本内容转换为语音音频，支持多种语音风格和音色。
	 * @param text 需要转换的文本内容
	 * @return 生成的音频文件URL或Base64编码
	 * @throws IOException 当音频生成或保存过程中发生IO错误时抛出
	 */
	String generateAudioByText(String text) throws IOException;

}
