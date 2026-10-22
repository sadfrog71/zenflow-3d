package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * 文档类型枚举
 * <p>
 * 定义了知识库中文档的类型，用于区分问答对中的问题和答案
 *
 * @author caoyuan
 * @date 2024/7/4
 */
@Getter
@RequiredArgsConstructor
@FieldNameConstants
public enum DocumentTypeEnums {

	/**
	 * 答案类型
	 * <p>
	 * 标识文档内容为答案，通常与问题类型的文档配对使用
	 */
	ANSWER("0", "答案"),

	/**
	 * 问题类型
	 * <p>
	 * 标识文档内容为问题，用于知识库的问答匹配
	 */
	QUESTION("1", "问题");

	/**
	 * 文档类型值
	 */
	private final String type;

	/**
	 * 文档类型描述
	 */
	private final String desc;

}
