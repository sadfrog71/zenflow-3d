package com.deepdimen.matrix.knowledge.support.flow.model;

import lombok.Data;

/**
 * AI流程连接定义 定义了流程中节点之间的连接关系 用于构建流程的执行路径和节点依赖关系
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiFlowConnectionDefinition {

	/**
	 * 源节点ID - 连接的起始节点标识 表示数据或控制流从哪个节点流出
	 */
	private String sourceId;

	/**
	 * 目标节点ID - 连接的目标节点标识 表示数据或控制流流向哪个节点
	 */
	private String targetId;

	/**
	 * 端口索引 - 节点的输出端口序号 用于支持节点有多个输出端口的场景（如分支节点） 索引从0开始，标识使用源节点的第几个输出端口
	 */
	private Integer portIndex;

}
