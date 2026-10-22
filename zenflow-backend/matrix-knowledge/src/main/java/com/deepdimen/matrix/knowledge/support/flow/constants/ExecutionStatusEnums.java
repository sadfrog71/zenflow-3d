package com.deepdimen.matrix.knowledge.support.flow.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI流程执行状态枚举 定义了流程节点在执行过程中的各种状态
 *
 * @author caoyuan
 * @date 2025/3/3
 */
@RequiredArgsConstructor
public enum ExecutionStatusEnums {

	/**
	 * 等待执行 - 节点已创建但尚未开始执行
	 */
	PENDING("pending", "等待执行"),

	/**
	 * 正在执行 - 节点正在处理中
	 */
	RUNNING("running", "正在执行"),

	/**
	 * 执行成功 - 节点已成功完成所有操作
	 */
	SUCCESS("success", "执行成功"),

	/**
	 * 执行失败 - 节点执行过程中发生错误
	 */
	ERROR("error", "执行失败"),

	/**
	 * 已跳过 - 由于条件判断或分支选择，节点被跳过未执行
	 */
	SKIPPED("skipped", "已跳过");

	/**
	 * 状态值 - 用于存储和传输的状态标识
	 */
	@Getter
	private final String value;

	/**
	 * 状态描述 - 状态的中文说明
	 */
	private final String desc;

}
