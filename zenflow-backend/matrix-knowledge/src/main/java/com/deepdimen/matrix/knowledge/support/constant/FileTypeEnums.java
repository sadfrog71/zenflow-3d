package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文件类型枚举
 * <p>
 * 定义了系统支持的文件格式类型，用于文件上传和解析时的格式校验
 *
 * @author caoyuan
 * @date 2024/10/2
 */
@Getter
@RequiredArgsConstructor
public enum FileTypeEnums {

	/**
	 * 纯文本文件
	 */
	TXT("txt"),

	/**
	 * Markdown文档
	 */
	MD("md"),

	/**
	 * HTML文档
	 */
	HTML("html"),

	/**
	 * HTML文档
	 */
	HTM("htm"),

	/**
	 * PDF文档
	 */
	PDF("pdf"),

	/**
	 * Word 97-2003文档
	 */
	DOC("doc"),

	/**
	 * Word 2007+文档
	 */
	DOCX("docx"),

	/**
	 * PowerPoint 97-2003演示文稿
	 */
	PPT("ppt"),

	/**
	 * PowerPoint 2007+演示文稿
	 */
	PPTX("pptx"),

	/**
	 * Excel 97-2003工作表
	 */
	XLS("xls"),

	/**
	 * Excel 2007+工作表
	 */
	XLSX("xlsx"),

	/**
	 * JPEG图片格式
	 */
	JPG("jpg"),

	/**
	 * JPEG图片格式（另一种扩展名）
	 */
	JPEG("jpeg"),

	/**
	 * PNG图片格式
	 */
	PNG("png");

	/**
	 * 文件类型扩展名
	 */
	private final String type;

}
