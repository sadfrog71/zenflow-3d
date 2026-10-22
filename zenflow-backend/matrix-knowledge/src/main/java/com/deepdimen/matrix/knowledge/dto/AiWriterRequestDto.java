package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiWriterRequestDto implements Serializable {

	/**
	 * 标题
	 */
	private String title;

	/**
	 * 摘要
	 */
	private String abstractContent;

	/**
	 * 模型名称
	 */
	private String modelName;

	/**
	 * 选择的模板ID
	 */
	private Long docTemplateId;

	/**
	 * 执行的动作 polish/rewrite/extend/imitate/continue/shorten
	 */
	private String action;

	/**
	 * 待处理文本
	 */
	private String text;

}
