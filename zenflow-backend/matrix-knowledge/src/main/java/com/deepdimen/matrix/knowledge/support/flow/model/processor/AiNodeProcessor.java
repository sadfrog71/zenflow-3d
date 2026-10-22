package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;

/**
 * 节点处理器接口 定义了所有流程节点处理器必须实现的基本接口
 *
 * @author caoyuan
 * @date 2025/03/03
 */
public interface AiNodeProcessor {

	/**
	 * 执行节点 处理节点的具体业务逻辑并返回执行结果
	 * @param node 节点定义，包含节点配置信息
	 * @param context 流程执行上下文，包含变量、参数等运行时信息
	 * @return 执行结果字典，包含节点输出的数据
	 */
	Dict execute(AiNodeDefinition node, FlowContextHolder context);

}
