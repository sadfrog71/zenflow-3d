package com.deepdimen.matrix.knowledge.support.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 函数参数类型枚举类 定义了函数参数的类型映射关系，包括输入类型、JSON Schema类型和低代码类型的对应关系
 *
 * @author caoyuan
 * @date 2024/8/7
 */
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public enum FunctionTypeEnum {

	/**
	 * 字符串类型
	 */
	String("string", "string", String.class),

	/**
	 * 数字类型
	 */
	Number("number", "number", String.class),

	/**
	 * 日期时间类型 - 要求格式为 yyyy-MM-dd HH:mm:ss
	 */
	DATETIME("date", "string", String.class, "Data Format Requirement [yyyy-MM-dd HH:mm:ss]"),

	/**
	 * 列表/数组类型
	 */
	List("list", "array", String[].class),

	/**
	 * 布尔值类型
	 */
	Boolean("boolean", "boolean", String.class);

	/**
	 * 输入类型标识
	 */
	private final String input;

	/**
	 * 大模型JSON Schema类型
	 */
	private final String jsonSchema;

	/**
	 * 低代码平台数据类型
	 */
	private final Class<?> lowCodeType;

	/**
	 * 扩展信息，用于向大模型提供更准确的字段描述
	 */
	private String ext = "";

	/**
	 * 根据输入类型获取对应的枚举
	 * @param input 输入类型
	 * @return 对应的类型枚举，如果未找到则返回String类型
	 */
	public static FunctionTypeEnum fromInput(String input) {
		for (FunctionTypeEnum value : FunctionTypeEnum.values()) {
			if (value.input.equals(input)) {
				return value;
			}
		}
		return String;
	}

}
