package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI文档解析节点 用于在工作流中执行Office和PDF文档的文本提取操作 fileUrl 从节点的 inputParams 中获取，不在此配置
 *
 * @author caoyuan
 * @date 2025/01/25
 */
@Data
public class AiDocumentParserNode {

	/**
	 * 解析策略 auto-自动选择 standard-仅标准解析 ai-仅AI解析 默认为auto，优先使用标准解析器(Apache
	 * POI/PDFBox)，失败时降级到AI视觉识别
	 */
	private String parseStrategy = "auto";

	/**
	 * 视觉模型名称 当使用AI解析策略时，使用此模型进行文档图像识别
	 */
	private String visionModelName;

}
