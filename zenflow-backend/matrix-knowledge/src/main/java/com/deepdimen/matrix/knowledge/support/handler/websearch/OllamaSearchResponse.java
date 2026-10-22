package com.deepdimen.matrix.knowledge.support.handler.websearch;

import lombok.Data;

/**
 * Ollama搜索响应结果类
 * <p>
 * 封装Ollama Web Search API的响应数据结构 包含搜索结果的详细信息
 *
 * @author caoyuan
 * @date 2025/09/26
 */
@Data
public class OllamaSearchResponse {

	/**
	 * 搜索结果数组
	 */
	private Result[] results;

	/**
	 * 搜索结果项
	 * <p>
	 * 表示单个搜索结果的详细信息
	 */
	@Data
	public static class Result {

		/**
		 * 网页标题
		 */
		private String title;

		/**
		 * 网页URL
		 */
		private String url;

		/**
		 * 网页内容摘要
		 */
		private String content;

	}

}
