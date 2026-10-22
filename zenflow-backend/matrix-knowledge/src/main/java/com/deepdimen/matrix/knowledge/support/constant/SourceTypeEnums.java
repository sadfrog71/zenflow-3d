package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档来源类型枚举
 * <p>
 * 定义了知识库文档的不同来源方式，包括文件上传、文本录入、问答对和网页爬取
 *
 * @author caoyuan
 * @date 2024/3/28
 */
@Getter
@RequiredArgsConstructor
public enum SourceTypeEnums {

	/**
	 * 文件上传 - 通过上传文件方式添加知识
	 */
	UPLOAD("1", "上传文件"),

	/**
	 * 文本录入 - 直接输入文本内容作为知识
	 */
	TEXT("2", "文本录入"),

	/**
	 * 问答对 - 以问题和答案的形式添加知识
	 */
	QA("3", "Q&A"),

	/**
	 * 网页爬取 - 通过URL爬取网页内容作为知识
	 */
	URL("4", "URL爬虫");

	/**
	 * 来源类型代码
	 */
	private final String type;

	/**
	 * 来源类型描述
	 */
	private final String description;

}
