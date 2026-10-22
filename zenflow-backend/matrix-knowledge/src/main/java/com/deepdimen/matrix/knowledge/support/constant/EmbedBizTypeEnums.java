package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * 向量嵌入业务类型枚举
 * <p>
 * 定义了向量嵌入（Embedding）的不同业务场景，用于区分向量数据的用途
 *
 * @author caoyuan
 * @date 2025/03/21
 */
@Getter
@FieldNameConstants
@RequiredArgsConstructor
public enum EmbedBizTypeEnums {

	/**
	 * 文件聊天类型
	 * <p>
	 * 用于基于文件内容的向量检索和问答场景
	 */
	CHAT2FILE("chat2file"),

	/**
	 * SQL聊天类型
	 * <p>
	 * 用于自然语言转SQL的向量匹配场景
	 */
	CHAT2SQL("chat2sql"),

	/**
	 * MCP聊天类型
	 * <p>
	 * 用于模型上下文协议相关的向量存储和检索
	 */
	CHAT2MCP("chat2mcp"),

	/**
	 * 函数类型
	 * <p>
	 * 用于函数描述和参数的向量化存储，支持函数调用匹配
	 */
	FUNCTION("function"),

	/**
	 * 存储文件
	 */
	STORAGE_FILE("storage_file"),

	/**
	 * 知识库
	 */
	DATASET("dataset");

	/**
	 * 业务类型标识
	 */
	private final String type;

}
