package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * 大模型需要OCR的字段信息 用于定义需要通过OCR识别并提取的字段
 *
 * @author caoyuan
 * @date 2024/10/23
 */
@Data
public class LlmOCRFieldDTO {

	/**
	 * 字段标签 用于展示的字段名称
	 */
	private String label;

	/**
	 * 字段名称 字段的唯一标识符
	 */
	private String name;

	/**
	 * 字段描述 对字段内容的详细说明
	 */
	private String description;

}
