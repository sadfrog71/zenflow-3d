package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * 硅流音频模型数据传输对象 用于封装音频生成相关的请求参数
 *
 * @author caoyuan
 * @date 2024/10/1
 *
 * 示例参数： "model": "fishaudio/fish-speech-1.5", "input": "The text to generate audio for",
 * "voice": "fishaudio/fish-speech-1.5:alex", "response_format": "mp3", "sample_rate":
 * 32000, "stream": true, "speed": 1, "gain": 0
 */
@Data
public class SiliconflowAudioModelDTO {

	/**
	 * 模型名称 指定使用的音频生成模型，如"fishaudio/fish-speech-1.5"
	 */
	private String model;

	/**
	 * 输入文本 需要转换为音频的文本内容
	 */
	private String input;

	/**
	 * 语音类型 指定生成音频使用的声音，如"fishaudio/fish-speech-1.5:alex"
	 */
	private String voice;

}
