package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 前台对话发送请求体
 * <p>
 * 用于 POST /chat/msg/send SSE 接口，合并消息发送与流式响应
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "前台对话发送请求体")
public class ChatSendDTO implements Serializable {

	/**
	 * 会话ID
	 */
	@NotBlank(message = "会话ID不能为空")
	@Schema(description = "会话ID")
	private String conversationId;

	/**
	 * 用户消息内容
	 */
	@NotBlank(message = "消息内容不能为空")
	@Schema(description = "用户消息内容")
	private String message;

	/**
	 * 智能体ID
	 */
	@Schema(description = "智能体ID")
	private Long agentId;

	/**
	 * 知识库ID列表
	 */
	@NotNull(message = "知识库ID不能为空")
	@Size(min = 1, message = "至少选择一个知识库")
	@Schema(description = "知识库ID列表")
	private List<Long> datasetIds;

	/**
	 * 上传文件URL列表
	 */
	@Schema(description = "上传文件URL列表")
	private List<String> fileUrls;

	/**
	 * 模型名称（可选，不传则使用智能体默认配置）
	 */
	@Schema(description = "模型名称")
	private String modelName;

	/**
	 * 是否启用网络搜索
	 */
	@Schema(description = "是否启用网络搜索")
	private boolean websearch;

	@Schema(description = "Dify 应用参数（F-005a，仅 Dify 类型 Agent 使用）")
	private java.util.Map<String, Object> inputs;

}
