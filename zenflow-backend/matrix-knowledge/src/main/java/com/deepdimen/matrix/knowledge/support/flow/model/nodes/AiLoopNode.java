package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 循环节点 用于在工作流中重复执行子流程
 *
 * @author caoyuan
 * @date 2025/01/24
 */
@Data
public class AiLoopNode {

	/**
	 * 最大循环次数 限制循环执行的最大迭代次数，防止无限循环
	 */
	private Integer maxIterations;

	/**
	 * 子节点列表 循环体中要执行的节点序列
	 */
	private List<AiNodeDefinition> childNodes;

	/**
	 * 子节点连接关系 定义子节点之间的连接和执行顺序
	 */
	private List<Map<String, Object>> childConnections;

	/**
	 * 迭代变量名 用于在子流程中访问当前迭代索引，默认为 "index" 可通过 ${loop.index} 访问
	 */
	private String iterationVarName;

}
