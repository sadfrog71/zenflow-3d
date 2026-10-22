package com.deepdimen.matrix.knowledge.support.rerank;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

/**
 * 重排序评分模型
 * <p>
 * 实现LangChain4j的ScoringModel接口，提供文本重排序功能 支持多种重排序服务：Jina和阿里云达摩院 用于对检索结果进行二次排序，提高搜索相关性
 *
 * @author caoyuan
 * @date 2025/05/05
 */
public class RerankerScoringModel implements ScoringModel {

	/**
	 * 默认API基础URL 阿里云达摩院服务地址
	 */
	private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

	/**
	 * 重排序客户端
	 */
	private final RerankerClient client;

	/**
	 * 使用的模型名称
	 */
	private final String modelName;

	/**
	 * API请求失败时的最大重试次数
	 */
	private final Integer maxRetries;

	/**
	 * 构造函数，初始化 RerankerScoringModel
	 * @param baseUrl Jina API 的基础 URL
	 * @param apiKey Jina API 的密钥
	 * @param modelName 要使用的 Jina 模型名称
	 * @param timeout API 请求超时时间
	 * @param maxRetries API 请求失败时的最大重试次数
	 * @param logRequests 是否记录请求日志
	 * @param logResponses 是否记录响应日志
	 */
	public RerankerScoringModel(String baseUrl, String apiKey, String modelName, Duration timeout, Integer maxRetries,
			Boolean logRequests, Boolean logResponses) {
		// 初始化 JinaClient
		this.client = RerankerClient.builder()
			.baseUrl(StrUtil.equals(ModelSupportEnums.ALIYUN_GTE_RERANK_V2.getCode(), modelName) ? DEFAULT_BASE_URL
					: baseUrl) // 设置基础 URL，如果为空则使用默认值
			.apiKey(ensureNotBlank(apiKey, "apiKey")) // 设置 API 密钥，确保不为空
			.timeout(getOrDefault(timeout, ofSeconds(60))) // 设置超时时间，如果为空则使用默认60秒
			.logRequests(getOrDefault(logRequests, false)) // 设置是否记录请求日志
			.logResponses(getOrDefault(logResponses, false)) // 设置是否记录响应日志
			.build();
		this.modelName = ensureNotBlank(modelName, "modelName"); // 设置模型名称，确保不为空
		this.maxRetries = getOrDefault(maxRetries, 2); // 设置最大重试次数，如果为空则使用默认值2
	}

	/**
	 * 获取 RerankerScoringModel 的构建器
	 * @return RerankerScoringModelBuilder 实例
	 */
	public static RerankerScoringModel.RerankerScoringModelBuilder builder() {
		return new RerankerScoringModel.RerankerScoringModelBuilder();
	}

	/**
	 * 对给定的文本段落列表根据查询进行评分
	 * @param segments 待评分的文本段落列表
	 * @param query 查询字符串
	 * @return 包含评分列表和 Token 使用情况的响应对象
	 */
	@Override
	public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

		// 构建 Jina Reranking 请求
		JinaRerankingRequest request = JinaRerankingRequest.builder()
			.model(modelName) // 设置模型名称
			.query(query) // 设置查询
			.documents(segments.stream() // 设置待重排的文档内容
				.map(TextSegment::text) // 提取 TextSegment 中的文本内容
				.collect(toList()))
			.returnDocuments(null) // 设置不返回文档内容，以减小响应大小
			.build();

		JinaRerankingResponse response = withRetryMappingExceptions(() -> client.rerank(request), maxRetries);

		// 提取并排序评分结果
		List<Double> scores = response.results.stream()
			.sorted(comparingInt(result -> result.index)) // 按原始索引排序
			.map(result -> result.relevanceScore) // 提取相关性评分
			.collect(toList());

		// 如果响应中没有 Token 使用信息，则直接返回评分列表
		if (Objects.isNull(response.usage)) {
			return Response.from(scores);
		}

		// 构建 TokenUsage 对象
		TokenUsage tokenUsage = new TokenUsage(response.usage.promptTokens, // prompt
																			// token 数量
				0, // completion token 数量 (rerank API 通常不返回 completion token)
				response.usage.totalTokens // 总 token 数量
		);
		// 返回包含评分列表和 Token 使用信息的响应
		return Response.from(scores, tokenUsage);
	}

	/**
	 * RerankerScoringModel 的构建器类
	 */
	public static class RerankerScoringModelBuilder {

		private String baseUrl; // Jina API 的基础 URL

		private String apiKey; // Jina API 的密钥

		private String modelName; // 要使用的 Jina 模型名称

		private Duration timeout; // API 请求超时时间

		private Integer maxRetries; // API 请求失败时的最大重试次数

		private Boolean logRequests; // 是否记录请求日志

		private Boolean logResponses; // 是否记录响应日志

		RerankerScoringModelBuilder() {
		}

		// 以下为链式调用方法，用于设置构建器属性

		public RerankerScoringModel.RerankerScoringModelBuilder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public RerankerScoringModel.RerankerScoringModelBuilder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public RerankerScoringModel.RerankerScoringModelBuilder modelName(String modelName) {
			this.modelName = modelName;
			return this;
		}

		public RerankerScoringModel.RerankerScoringModelBuilder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public RerankerScoringModel.RerankerScoringModelBuilder maxRetries(Integer maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		public RerankerScoringModel.RerankerScoringModelBuilder logRequests(Boolean logRequests) {
			this.logRequests = logRequests;
			return this;
		}

		public RerankerScoringModel.RerankerScoringModelBuilder logResponses(Boolean logResponses) {
			this.logResponses = logResponses;
			return this;
		}

		/**
		 * 构建 RerankerScoringModel 实例
		 * @return RerankerScoringModel 实例
		 */
		public RerankerScoringModel build() {
			return new RerankerScoringModel(this.baseUrl, this.apiKey, this.modelName, this.timeout, this.maxRetries,
					this.logRequests, this.logResponses);
		}

		/**
		 * 返回构建器状态的字符串表示
		 * @return 构建器状态字符串
		 */
		public String toString() {
			return "RerankerScoringModel.RerankerScoringModelBuilder(baseUrl=" + this.baseUrl + ", apiKey="
					+ this.apiKey + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries="
					+ this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses
					+ ")";
		}

	}

}