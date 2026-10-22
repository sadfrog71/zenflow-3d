package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI MCP（Model Context Protocol）节点 用于在工作流中集成MCP协议，实现与各种外部工具和服务的标准化交互
 * MCP是一种统一的协议，允许AI模型与不同的工具、数据源和服务进行通信
 *
 * @author caoyuan
 * @date 2025/03/22
 */
@Data
public class AiMCPNode {

	/**
	 * MCP配置ID 系统中预定义的MCP配置标识符
	 */
	private String mcpId;

	/**
	 * MCP配置名称 MCP配置的可读名称，用于界面显示
	 */
	private String mcpName;

	/**
	 * 输入提示词 发送给MCP服务的指令或查询内容
	 */
	private String prompt;

}