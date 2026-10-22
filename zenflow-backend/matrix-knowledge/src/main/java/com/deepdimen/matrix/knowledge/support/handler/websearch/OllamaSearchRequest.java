package com.deepdimen.matrix.knowledge.support.handler.websearch;

import lombok.Builder;
import lombok.Data;

/**
 * Ollama搜索请求参数类
 * <p>
 * 封装Ollama Web Search API的请求参数 使用Builder模式构建，支持灵活配置搜索选项
 *
 * @author caoyuan
 * @date 2025/09/26
 */
@Builder
@Data
public class OllamaSearchRequest {

	/**
	 * 搜索关键词
	 */
	private String query;

	/**
	 * 最大结果数
	 */
	private Integer max_results;

}
