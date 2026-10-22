package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * OpenAI Chat Completions 请求DTO（路径参数版本） dataset_id 通过路径参数传递，不在请求体中
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiPathChatCompletionsRequest implements Serializable {

	/**
	 * 模型名称
	 */
	@NotBlank(message = "模型名称不能为空")
	private String model;

	/**
	 * 对话消息列表
	 */
	@NotEmpty(message = "消息列表不能为空")
	private List<OpenAiMessage> messages;

	/**
	 * 是否流式输出，默认false
	 */
	private Boolean stream = false;

	/**
	 * 温度参数，控制随机性
	 */
	private Double temperature;

	/**
	 * 最大令牌数
	 */
	@JsonProperty("max_tokens")
	private Integer maxTokens;

	/**
	 * top_p采样参数
	 */
	@JsonProperty("top_p")
	private Double topP;

	/**
	 * 频率惩罚
	 */
	@JsonProperty("frequency_penalty")
	private Double frequencyPenalty;

	/**
	 * 存在惩罚
	 */
	@JsonProperty("presence_penalty")
	private Double presencePenalty;

	/**
	 * 停止词列表
	 */
	private List<String> stop;

	/**
	 * 用户标识
	 */
	private String user;

	/**
	 * 流式选项
	 */
	@JsonProperty("stream_options")
	private OpenAiStreamOptions streamOptions;

	// ========== 扩展字段（兼容原ChatMessageDTO） ==========

	/**
	 * 会话ID（扩展字段）
	 */
	@JsonProperty("conversation_id")
	private String conversationId;

	/**
	 * 是否启用网络搜索（扩展字段）
	 */
	@JsonProperty("web_search")
	private Boolean webSearch = false;

	/**
	 * 扩展详情（扩展字段）
	 */
	@JsonProperty("ext_details")
	private Object extDetails;

}