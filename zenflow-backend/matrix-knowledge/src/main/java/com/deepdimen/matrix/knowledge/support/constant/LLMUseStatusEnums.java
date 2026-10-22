package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 大模型使用状态枚举类 定义了大模型调用的各种状态
 *
 * @author caoyuan
 * @date 2024/5/21
 */
@Getter
@RequiredArgsConstructor
public enum LLMUseStatusEnums {

	/**
	 * 未使用 - 尚未调用大模型
	 */
	UNUSE("0", "未使用"),
	/**
	 * 已使用 - 成功调用大模型
	 */
	USED("1", "已使用"),
	/**
	 * 敏感词 - 触发敏感词拦截
	 */
	SENSITIVE("2", "敏感词"),

	/**
	 * 使用受限
	 */
	USE_LIMIT("3", "用量使用受限"),

	/**
	 * 没有查询到向量 - 知识库中未找到匹配的向量数据
	 */
	NO_VECTOR("4", "没有查询到向量"),

	/**
	 * 失败 - 调用大模型失败
	 */
	FAILED("9", "失败");

	/**
	 * 状态码
	 */
	private final String status;

	/**
	 * 状态描述
	 */
	private final String desc;

}
