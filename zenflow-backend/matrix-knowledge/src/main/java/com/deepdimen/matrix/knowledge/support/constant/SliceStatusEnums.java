package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档切片状态枚举
 * <p>
 * 定义了文档在切片处理过程中的各种状态，用于跟踪文档处理进度
 *
 * @author caoyuan
 * @date 2024/5/21
 */
@Getter
@RequiredArgsConstructor
public enum SliceStatusEnums {

	/**
	 * 未切片 - 文档已上传但尚未进行切片处理
	 */
	UNSLICED("0", "未切片"),
	/**
	 * 已切片 - 文档已成功完成切片处理
	 */
	SLICED("1", "已切片"),
	/**
	 * OCR解析中 - 正在进行OCR文字识别处理
	 */
	OCR_PARSING("2", "解析中"),

	/**
	 * AI解析中 - 正在使用AI模型进行内容解析和理解
	 */
	AI_PARSING("3", "AI解析中"),
	/**
	 * 处理失败 - 切片或解析过程中发生错误
	 */
	FAILED("9", "失败");

	/**
	 * 切片状态代码
	 */
	private final String status;

	/**
	 * 切片状态描述
	 */
	private final String desc;

}
