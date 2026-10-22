package com.deepdimen.matrix.knowledge.support.flow.model;

import lombok.Data;

/**
 * AI参数定义 定义节点输入输出参数的元数据信息 用于描述参数的名称、类型、默认值等属性
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiParamDefinition {

	/**
	 * 参数名称 - 参数的标识符 在节点内必须唯一，用于参数引用和映射
	 */
	private String name;

	/**
	 * 参数类型 - 参数的数据类型 如：string、number、boolean、object、array等 用于参数验证和类型转换
	 */
	private String type;

	/**
	 * 参数值 - 参数的默认值或固定值 可以是具体值，也可以是引用其他节点输出的表达式 如：固定值"hello"，或引用"${node1.output}"
	 */
	private String value;

	/**
	 * 是否必填 - 标识参数是否为必需参数 true表示必须提供该参数，false表示可选 用于参数验证和流程校验
	 */
	private boolean required;

}
