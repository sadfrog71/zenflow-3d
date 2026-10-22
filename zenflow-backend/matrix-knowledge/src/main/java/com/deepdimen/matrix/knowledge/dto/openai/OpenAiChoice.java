package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI 选择项DTO
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiChoice implements Serializable {

	/**
	 * 选择索引
	 */
	private Integer index;

	/**
	 * 完整消息（非流式响应）
	 */
	private OpenAiMessage message;

	/**
	 * 增量消息（流式响应）
	 */
	private OpenAiDelta delta;

	/**
	 * 结束原因: stop, length, content_filter, null
	 */
	@JsonProperty("finish_reason")
	private String finishReason;

	/**
	 * 日志概率信息（可选）
	 */
	private Object logprobs;

}