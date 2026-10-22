package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * 函数字段数据传输对象
 * <p>
 * 定义函数参数的字段属性，包括名称、类型、注释等元数据信息
 *
 * @author caoyuan
 * @date 2024/08/07
 */
@Data
@FieldNameConstants
public class FunctionFieldDTO {

	/**
	 * 属性名称
	 * <p>
	 * 函数参数的属性名称标识
	 */
	private String attrName;

	/**
	 * 字段注释
	 * <p>
	 * 字段的详细说明或描述信息
	 */
	private String fieldComment;

	/**
	 * 表单类型
	 * <p>
	 * 字段在表单中的展示类型（如：text、number、select等）
	 */
	private String formType;

	/**
	 * 必填标志
	 * <p>
	 * 标识该字段在表单中是否为必填项
	 */
	private boolean formRequired;

}
