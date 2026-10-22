package com.deepdimen.matrix.knowledge.service;

/**
 * AI定时任务服务接口 提供各类AI处理任务的定时执行功能，包括文档处理、OCR识别等
 *
 * @author caoyuan
 * @date 2024/6/16
 */
public interface AiTaskService {

	/**
	 * 更新文档摘要 定时扫描未生成摘要的文档，使用AI生成文档摘要信息
	 */
	void updateDocumentSummary();

	/**
	 * 更新文档切片 定时处理待切片的文档，将文档内容分割成适合检索的片段
	 */
	void updateDocumentSlice();

	/**
	 * 更新文档OCR识别结果 定时处理包含图片的文档，提取图片中的文字内容
	 */
	void updateDocumentOcrResult();

	/**
	 * 更新Mineru文档解析结果 定时处理使用Mineru引擎解析的复杂文档，提取结构化内容
	 */
	void updateMineruParseResult();

}
