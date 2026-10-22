package com.deepdimen.matrix.knowledge.support.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.jina.internal.api.JinaDocument;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * 阿里云达摩院重排序响应实体
 * <p>
 * 封装文本重排序API的响应结果 包含重排序后的文档列表、相关性分数和使用量信息
 *
 * @author caoyuan
 * @date 2025/06/26
 */
@Data
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class DashscopeRerankingResponse {

	/**
	 * 输出结果 包含重排序后的文档列表
	 */
	private Output output;

	/**
	 * API使用量统计 记录本次请求消耗的token数量
	 */
	private Usage usage;

	/**
	 * 请求ID 用于追踪和调试
	 */
	private String requestId;

	/**
	 * 输出结果内部类
	 * <p>
	 * 包装重排序后的结果数组
	 */
	public static class Output {

		/**
		 * 重排序结果数组 按相关性从高到低排序
		 */
		private Result[] results;

		public Result[] getResults() {
			return results;
		}

		public void setResults(Result[] results) {
			this.results = results;
		}

	}

	/**
	 * 重排序结果项
	 * <p>
	 * 表示单个文档的重排序结果 包含文档内容、原始索引和相关性分数
	 */
	@Data
	public static class Result {

		/**
		 * 文档内容 如果请求时设置了return_documents=true则包含原始文档
		 */
		private JinaDocument document;

		/**
		 * 文档在原始列表中的索引位置
		 */
		private int index;

		/**
		 * 相关性分数 分数越高表示与查询越相关
		 */
		@JsonProperty("relevance_score")
		private double relevanceScore;

	}

	/**
	 * 使用量统计内部类
	 * <p>
	 * 记录API调用的资源消耗
	 */
	@Data
	public static class Usage {

		/**
		 * 总token消耗量 包含输入和输出的所有token
		 */
		@JsonProperty("total_tokens")
		private int totalTokens;

	}

}
