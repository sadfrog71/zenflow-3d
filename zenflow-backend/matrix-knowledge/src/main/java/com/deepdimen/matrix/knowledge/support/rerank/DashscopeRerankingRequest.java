package com.deepdimen.matrix.knowledge.support.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.Getter;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * 阿里云达摩院重排序请求实体
 * <p>
 * 用于构建文本重排序API请求参数 支持对多个文档根据查询相关性进行重新排序 使用阿里云的gte-rerank模型
 *
 * @author caoyuan
 * @date 2025/06/26
 */
@Getter
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class DashscopeRerankingRequest {

	/**
	 * 重排序模型名称 默认使用gte-rerank-v2模型
	 */
	private String model = "gte-rerank-v2";

	/**
	 * 输入参数 包含查询文本和待排序的文档列表
	 */
	private Input input;

	/**
	 * 模型参数配置 控制返回结果的格式和数量
	 */
	private Parameters parameters;

	public DashscopeRerankingRequest(String query, List<String> documents) {
		this.input = new Input(query, documents);
		this.parameters = new Parameters(true, 5);
	}

	/**
	 * 输入数据内部类
	 * <p>
	 * 封装查询文本和待重排序的文档列表
	 */
	@Data
	public static class Input {

		/**
		 * 查询文本 用于计算与文档的相关性
		 */
		private String query;

		/**
		 * 待排序文档列表 将根据与查询的相关性重新排序
		 */
		private List<String> documents;

		public Input(String query, List<String> documents) {
			this.query = query;
			this.documents = documents;
		}

	}

	/**
	 * 参数配置内部类
	 * <p>
	 * 控制重排序结果的输出格式
	 */
	@Data
	public static class Parameters {

		/**
		 * 是否返回原始文档内容 true: 返回文档内容和分数 false: 仅返回文档索引和分数
		 */
		@JsonProperty("return_documents")
		private boolean returnDocuments;

		/**
		 * 返回结果数量限制 只返回相关性最高的前N个文档
		 */
		@JsonProperty("top_n")
		private int topN;

		public Parameters(boolean returnDocuments, int topN) {
			this.returnDocuments = returnDocuments;
			this.topN = topN;
		}

	}

}
