package com.deepdimen.matrix.knowledge.dto;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import lombok.Data;

/**
 * 聊天消息上下文
 * <p>
 * 封装了聊天过程中的所有上下文信息，包括用户信息、模型配置、消息内容等
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Data
public class ChatMessageContext {

	/**
	 * Ollama启用标志
	 * <p>
	 * 标识是否启用Ollama本地模型服务
	 */
	private boolean ollama;

	/**
	 * 模型名称
	 * <p>
	 * 当前聊天使用的AI模型名称
	 */
	private String model;

	/**
	 * 聊天消息DTO
	 * <p>
	 * 包含当前上下文中的聊天消息详细信息
	 */
	private ChatMessageDTO chatMessageDTO;

	/**
	 * 错误消息
	 * <p>
	 * 聊天过程中产生的错误信息（如果有）
	 */
	private String errorMessage;

	/**
	 * 用户名
	 * <p>
	 * 参与聊天的用户名称
	 */
	private String username;

	/**
	 * 用户ID
	 * <p>
	 * 参与聊天的用户唯一标识符
	 */
	private Long userId;

	/**
	 * IP地址
	 * <p>
	 * 用户的IP地址信息
	 */
	private String ip;

	/**
	 * 功能名称
	 * <p>
	 * 当前聊天中使用的功能名称
	 */
	private String funcName;

	/**
	 * 消息结果DTO
	 * <p>
	 * AI返回的消息结果对象
	 */
	private AiMessageResultDTO messageResultDTO;

	/**
	 * 知识库元信息
	 * <p>
	 * 当前使用的知识库实体信息
	 */
	private AiDatasetEntity aiDataset;

}
