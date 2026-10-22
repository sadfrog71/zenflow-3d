package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * 重排模型数据传输对象 用于文档重排序相关的请求和响应数据封装
 *
 * @author caoyuan
 * @date 2024/10/1
 */
@Data
public class RerankerModelDTO {

	/**
	 * 重排模型请求内部类 封装重排序请求的参数
	 */
	@Data
	public static class RerankerModelRequest {

		/**
		 * 模型名称 指定使用的重排序模型
		 */
		private String model;

		/**
		 * 查询文本 用于与文档进行相关性比较的查询内容
		 */
		private String query;

		/**
		 * 文档数组 待重排序的文档列表
		 */
		private String[] documents;

		/**
		 * 返回的前N个结果 指定返回最相关的文档数量
		 */
		private Integer topN;

		/**
		 * 是否返回文档内容 true表示在结果中包含原始文档内容
		 */
		private Boolean returnDocuments;

		/**
		 * 每个文档的最大分块数 用于长文档的分块处理
		 */
		private Integer maxChunksPerDoc;

		/**
		 * 重叠的token数量 文档分块时相邻块之间重叠的token数
		 */
		private Integer overlapTokens;

	}

	/**
	 * 重排模型响应内部类 封装重排序操作的响应数据
	 */
	@Data
	public static class RerankerModelResponse {

		/**
		 * 响应ID 唯一标识此次重排序操作
		 */
		private String id;

		/**
		 * 重排序结果数组 按相关性排序的文档结果
		 */
		private Results[] results;

		/**
		 * Token使用情况 记录输入和输出的token数量
		 */
		private Tokens tokens;

		/**
		 * 结果内部类 封装单个文档的重排序结果
		 */
		@Data
		public static class Results {

			/**
			 * 文档对象 包含文档的具体内容
			 */
			private Document document;

			/**
			 * 原始索引 文档在输入数组中的原始位置
			 */
			private Integer index;

			/**
			 * 相关性得分 文档与查询的相关性分数
			 */
			private Integer relevanceScore;

			/**
			 * 文档内部类 封装文档的文本内容
			 */
			@Data
			public static class Document {

				/**
				 * 文档文本 文档的具体内容
				 */
				private String text;

			}

		}

		/**
		 * Token统计内部类 统计token使用情况
		 */
		@Data
		public static class Tokens {

			/**
			 * 输入Token数量 处理请求时消耗的token数
			 */
			private Integer inputTokens;

			/**
			 * 输出Token数量 生成响应时使用的token数
			 */
			private Integer outputTokens;

		}

	}

}
