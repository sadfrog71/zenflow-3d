package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.entity.AiFlowEntity;
import com.deepdimen.matrix.knowledge.support.flow.model.AiFlowDSLDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.AiFlowExecuteResult;

/**
 * AI 流程处理器接口 定义了执行AI工作流的核心接口
 *
 * @author caoyuan
 * @date 2025/3/3
 */
public interface AiFlowProcessor {

	/**
	 * 执行工作流 根据工作流定义和DSL配置，执行整个流程并返回结果
	 * @param flow 工作流定义实体
	 * @param dsl 工作流DSL定义，包含节点和连接信息
	 * @param flowExecuteDTO 执行请求参数，包含输入参数、环境变量等
	 * @return 执行结果，包含执行的节点、变量、耗时等信息
	 */
	AiFlowExecuteResult execute(AiFlowEntity flow, AiFlowDSLDefinition dsl, AiFlowExecuteDTO flowExecuteDTO);

}
