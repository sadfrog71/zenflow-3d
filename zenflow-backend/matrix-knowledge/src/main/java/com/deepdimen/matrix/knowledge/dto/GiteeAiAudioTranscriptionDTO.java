package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * Gitee AI音频转文本数据传输对象 用于封装音频识别相关的响应参数
 *
 * @author caoyuan
 * @date 2025/11/04
 */
public class GiteeAiAudioTranscriptionDTO {

	/**
	 * 音频转文本响应对象 包含识别后的文本内容和元数据
	 */
	@Data
	public static class Response {

		/**
		 * 识别后的文本内容
		 */
		private String text;

		/**
		 * 检测到的语言（可选）
		 */
		private String language;

		/**
		 * 音频时长（秒）（可选）
		 */
		private Double duration;

	}

}
