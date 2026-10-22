package com.deepdimen.matrix.knowledge.support.constant;

import lombok.RequiredArgsConstructor;

/**
 * MCP默认命令枚举类 定义了MCP（Model Context Protocol）相关的默认命令
 *
 * @author caoyuan
 * @date 2025/03/24
 */
@RequiredArgsConstructor
public enum McpDefaultCommandEnums {

	/**
	 * NPX命令 - Node包执行器，使用-y参数自动确认
	 */
	NPX("npx", "-y");

	/**
	 * 命令名称
	 */
	public final String name;

	/**
	 * 命令参数
	 */
	public final String command;

}
