package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI流程节点类型枚举
 * <p>
 * 定义了AI工作流编排中的节点类型，用于构建复杂的AI处理流程
 *
 * @author caoyuan
 * @date 2024/08/06
 */
@Getter
@RequiredArgsConstructor
public enum FlowNodeTypeEnum {

	/**
	 * 开始节点
	 * <p>
	 * 流程的起始节点，每个流程必须有且只有一个开始节点
	 */
	START("start", "开始"),

	/**
	 * 函数描述节点
	 * <p>
	 * 用于描述和定义函数调用的参数和逻辑
	 */
	FUNC_DESC("funcDesc", "函数描述"),

	/**
	 * 执行节点
	 * <p>
	 * 实际执行具体任务或调用服务的节点
	 */
	EXEC("exec", "执行"),

	/**
	 * 结果节点
	 * <p>
	 * 用于处理和展示函数调用的返回结果
	 */
	RESULT("result", "调用结果"),

	/**
	 * 并行结束节点
	 * <p>
	 * 标识并行任务的结束点，等待所有并行分支完成
	 */
	END_PARALLEL("endParallel", "结束");

	/**
	 * 节点类型标识
	 * <p>
	 * 用于流程定义和节点识别
	 */
	private final String type;

	/**
	 * 节点类型描述
	 * <p>
	 * 用于界面展示的中文描述
	 */
	private final String description;

}
