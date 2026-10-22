package com.deepdimen.matrix.knowledge.support.constant;

/**
 * 文件解析状态枚举
 * <p>
 * 定义了文件解析过程中的各种状态，用于跟踪文件处理进度
 *
 * @author caoyuan
 * @date 2024/10/2
 */
public enum FileParserStatusEnums {

	/**
	 * 未解析状态
	 * <p>
	 * 文件已上传但尚未开始解析
	 */
	UNPARSE,

	/**
	 * AI解析中状态
	 * <p>
	 * 文件正在使用AI模型进行内容解析
	 */
	AI_PARSING,

	/**
	 * OCR解析中状态
	 * <p>
	 * 文件正在进行光学字符识别（OCR）处理，通常用于图片类文件
	 */
	OCR_PARSING,

	/**
	 * 解析成功状态
	 * <p>
	 * 文件解析完成，内容已成功提取并存储
	 */
	PARSE_SUCCESS,

	/**
	 * 解析失败状态
	 * <p>
	 * 文件解析过程中发生错误，需要检查错误日志
	 */
	PARSE_FAIL

}
