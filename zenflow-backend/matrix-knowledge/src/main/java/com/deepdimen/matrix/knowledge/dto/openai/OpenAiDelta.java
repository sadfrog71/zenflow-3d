package com.deepdimen.matrix.knowledge.dto.openai;

import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI 增量消息DTO（流式响应用）
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiDelta implements Serializable {

	/**
	 * 消息角色（仅在流式响应的第一个chunk中出现）
	 */
	private String role;

	/**
	 * 消息内容片段
	 */
	private String content;

	public OpenAiDelta() {
	}

	public OpenAiDelta(String role, String content) {
		this.role = role;
		this.content = content;
	}

	public OpenAiDelta(String content) {
		this.content = content;
	}

}