package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI OCR识别节点 用于在工作流中执行图像文字识别操作 imageUrl 从节点的 inputParams 中获取，不在此配置
 *
 * @author caoyuan
 * @date 2025/01/25
 */
@Data
public class AiOcrNode {

	/**
	 * OCR模板ID 对应系统中配置的OCR识别模板标识
	 */
	private String ocrId;

	/**
	 * OCR模板名称 用于显示和标识OCR模板
	 */
	private String ocrTitle;

	/**
	 * 高阶解析开关 0-关闭 1-开启 开启后使用聊天模型进行高级内容解析
	 */
	private String higherAnalyze;

	/**
	 * 聊天模型名称
	 */
	private String chatModelName;

	/**
	 * 视觉模型名称
	 */
	private String visionModelName;

}
