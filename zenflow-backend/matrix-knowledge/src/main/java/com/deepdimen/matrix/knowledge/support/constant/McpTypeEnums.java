package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * MCP类型枚举类 定义了MCP（Model Context Protocol）的通信类型
 *
 * @author caoyuan
 * @date 2024/7/4
 * <p>
 * "0" 答案 "1" 问题
 */
@Getter
@RequiredArgsConstructor
@FieldNameConstants
public enum McpTypeEnums {

	/**
	 * SSE类型 - Server-Sent Events，用于服务器推送事件流
	 */
	SSE("SSE", "SSE"),

	/** 可流式处理的标识，streamable http */
	STEAMABLE("STEAMABLE", "streamable http"),

	/**
	 * STDIO类型 - 标准输入输出，通过子进程的方式进行通信
	 */
	STDIO("STDIO", "子进程的方式");

	/**
	 * 类型标识
	 */
	private final String type;

	/**
	 * 类型描述
	 */
	private final String desc;

}
