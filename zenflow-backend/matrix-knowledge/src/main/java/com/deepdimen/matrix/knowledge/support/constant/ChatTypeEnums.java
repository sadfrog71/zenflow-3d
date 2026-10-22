package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * 聊天类型枚举
 * <p>
 * 定义了系统支持的各种聊天模式，每种模式对应不同的处理逻辑和功能特性
 *
 * @author caoyuan
 * @date 2024/4/19
 * @deprecated 注释中的判断逻辑： if (lastUserMessage.getDatasetId() == -1L) { return
 * "functionChat"; } if (lastUserMessage.getDatasetId() == 0L) { return "simpleChat"; } if
 * (lastUserMessage.getDatasetId() == -2L) { return "databaseChat"; } return "vectorChat";
 */
@Getter
@RequiredArgsConstructor
@FieldNameConstants
@Deprecated
public enum ChatTypeEnums {

	/**
	 * 功能聊天模式
	 * <p>
	 * 支持函数调用和工具使用的聊天模式，可以执行特定功能操作
	 */
	FUNCTION_CHAT(-1L, "functionChat"),
	/**
	 * 简单聊天模式
	 * <p>
	 * 基础的问答聊天模式，不涉及知识库和函数调用
	 */
	SIMPLE_CHAT(0L, "simpleChat"),
	/**
	 * 数据库聊天模式
	 * <p>
	 * 支持自然语言转SQL，可以查询和分析数据库数据
	 */
	DATABASE_CHAT(-2L, "databaseChat"),
	/**
	 * 图片生成模式
	 * <p>
	 * 文本转图片功能，根据描述生成对应的图片
	 */
	IMAGE_CHAT(-3L, "text2ImageChat"),

	/**
	 * 脑图生成模式
	 * <p>
	 * 将文本内容转换为思维导图（MarkMap格式）
	 */
	MARKMAP_CHAT(-4L, "text2MarkMapChat"),

	/**
	 * 流程编排模式
	 * <p>
	 * 支持复杂的AI工作流编排，可以组合多个AI能力
	 */
	FLOW_CHAT(-5L, "functionChat"),
	/**
	 * JSON聊天模式
	 * <p>
	 * 强制输出JSON格式的聊天模式，适合结构化数据交互
	 */
	JSON_CHAT(-6L, "jsonChat"),

	/**
	 * 推理聊天模式
	 * <p>
	 * 增强推理能力的聊天模式，适合复杂逻辑分析和推理任务
	 */
	REASON_CHAT(-7L, "reasonChat"),

	/**
	 * MCP聊天模式
	 * <p>
	 * 支持模型上下文协议（Model Context Protocol）的聊天模式
	 */
	MCP_CHAT(-8L, "mcpChat"),
	/**
	 * DBGPT聊天模式
	 * <p>
	 * 基于DBGPT API的数据库查询聊天模式，支持自然语言转SQL查询
	 */
	DBGPT_CHAT(-9L, "dbgptChat"),
	/**
	 * 知识库聊天模式
	 * <p>
	 * 基于向量数据库的知识问答，可以检索相关文档回答问题
	 */
	VECTOR_CHAT(1L, "vectorChat");

	/**
	 * 聊天类型代码
	 * <p>
	 * 用于数据库存储和类型识别
	 */
	private final Long code;

	/**
	 * 聊天类型标识
	 * <p>
	 * 用于前端展示和路由识别
	 */
	private final String type;

	/**
	 * 根据代码获取对应的聊天类型枚举
	 * @param code 聊天类型代码
	 * @return 对应的聊天类型枚举，如果未找到则返回默认的VECTOR_CHAT
	 */
	public static ChatTypeEnums fromCode(Long code) {
		for (ChatTypeEnums value : ChatTypeEnums.values()) {
			if (value.code.equals(code)) {
				return value;
			}
		}
		return VECTOR_CHAT;
	}

}
