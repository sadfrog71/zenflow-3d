package com.deepdimen.matrix.knowledge.service;

import java.awt.Dimension;
import java.io.IOException;

/**
 * AI词云生成服务接口
 * <p>
 * 提供基于文档切片内容生成中文词云图的功能 支持矩形词云图生成，返回PNG格式的图片字节数组
 *
 * @author matrix
 * @date 2025-01-23
 */
public interface AiWordCloudService {

	/**
	 * 根据文档ID生成中文词云图
	 * <p>
	 * 查询指定文档的所有切片内容，组合成完整文本后 使用中文分词器进行词频分析，生成矩形中文词云图
	 * @param documentId 文档ID
	 * @return PNG格式的词云图字节数组
	 * @throws IOException 文件处理异常
	 */
	byte[] generateWordCloud(Long documentId) throws IOException;

	/**
	 * 根据文档ID生成自定义尺寸的中文词云图
	 * <p>
	 * 查询指定文档的所有切片内容，组合成完整文本后 使用中文分词器进行词频分析，生成指定尺寸的矩形中文词云图
	 * @param documentId 文档ID
	 * @param dimension 词云图尺寸
	 * @return PNG格式的词云图字节数组
	 * @throws IOException 文件处理异常
	 */
	byte[] generateWordCloud(Long documentId, Dimension dimension) throws IOException;

}