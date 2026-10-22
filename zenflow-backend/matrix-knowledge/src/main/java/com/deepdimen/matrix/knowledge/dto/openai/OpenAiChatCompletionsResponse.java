package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * OpenAI Chat Completions 响应DTO
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiChatCompletionsResponse implements Serializable {

	/**
	 * 响应ID
	 */
	private String id;

	/**
	 * 对象类型，chat.completion 或 chat.completion.chunk
	 */
	private String object;

	/**
	 * 创建时间戳
	 */
	private Long created;

	/**
	 * 模型名称
	 */
	private String model;

	/**
	 * 选择列表
	 */
	private List<OpenAiChoice> choices;

	/**
	 * 使用统计信息（非流式响应才有）
	 */
	private OpenAiUsage usage;

	/**
	 * 系统指纹（可选）
	 */
	@JsonProperty("system_fingerprint")
	private String systemFingerprint;

}