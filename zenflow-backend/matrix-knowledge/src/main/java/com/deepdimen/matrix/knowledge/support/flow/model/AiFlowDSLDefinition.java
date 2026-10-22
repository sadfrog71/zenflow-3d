package com.deepdimen.matrix.knowledge.support.flow.model;

import cn.hutool.core.lang.Dict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI流程DSL(领域特定语言)定义 用于描述完整的AI工作流结构和配置 包含节点、连接关系和执行参数等核心元素
 *
 * @author caoyuan
 * @date 2025/3/3
 */
@Data
public class AiFlowDSLDefinition {

	/**
	 * 节点列表 - 流程中所有节点的定义集合 每个节点代表流程中的一个处理单元
	 */
	private List<AiNodeDefinition> nodes;

	/**
	 * 连接列表 - 定义节点之间的连接关系 决定了流程的执行路径和数据流向
	 */
	private List<AiFlowConnectionDefinition> connections;

	/**
	 * 执行参数 - 流程级别的参数配置 用于定义流程执行时需要的全局参数 可包含默认值、必填项等配置信息
	 */
	@Schema(description = "执行参数")
	private List<Dict> params;

}
