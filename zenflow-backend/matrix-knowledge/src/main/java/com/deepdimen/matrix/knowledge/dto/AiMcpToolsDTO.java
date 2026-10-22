package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI MCP（Model Context Protocol）工具数据传输对象
 * <p>
 * 用于定义和传输AI模型上下文协议工具的基本信息
 *
 * @author caoyuan
 * @date 2025/07/11
 */
@Data
public class AiMcpToolsDTO {

	/**
	 * 工具名称
	 * <p>
	 * MCP工具的唯一标识名称
	 */
	private String name;

	/**
	 * 工具描述
	 * <p>
	 * 详细说明该MCP工具的功能和用途
	 */
	private String description;

}
