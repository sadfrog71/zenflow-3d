package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI爬虫文档数据传输对象
 * <p>
 * 用于封装爬虫文档的相关数据。
 *
 * @author caoyuan
 * @date 2025/04/30
 */
@Data
public class AiCrawleDocumentDTO {

	/**
	 * 爬取目标URL 需要爬取的网页地址
	 */
	private String url;

	/**
	 * 爬虫配置信息 包含爬取规则、深度、过滤条件等设置的JSON格式字符串
	 */
	private String settings;

}
