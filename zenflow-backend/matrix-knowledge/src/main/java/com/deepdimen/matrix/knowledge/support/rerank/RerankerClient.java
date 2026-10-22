package com.deepdimen.matrix.knowledge.support.rerank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResult;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * 重排序客户端
 * <p>
 * 统一封装多种重排序服务的HTTP客户端 支持Jina和阿里云达摩院的重排序模型 通过适配器模式统一不同服务的接口差异
 *
 * @author caoyuan
 * @date 2025/06/26
 */
public class RerankerClient {

	/**
	 * Jackson对象映射器 用于JSON序列化和反序列化
	 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

	/**
	 * 重排序API接口
	 */
	private final RerankerApi jinaApi;

	/**
	 * 认证头信息 格式：Bearer {apiKey}
	 */
	private final String authorizationHeader;

	/**
	 * 构造函数
	 * <p>
	 * 初始化HTTP客户端和API接口
	 * @param baseUrl API基础URL
	 * @param apiKey API密钥
	 * @param timeout 请求超时时间
	 * @param logRequests 是否记录请求日志
	 * @param logResponses 是否记录响应日志
	 */
	RerankerClient(String baseUrl, String apiKey, Duration timeout, boolean logRequests, boolean logResponses) {

		// 配置OkHttp客户端
		OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder().callTimeout(timeout)
			.connectTimeout(timeout)
			.readTimeout(timeout)
			.writeTimeout(timeout);

		// 创建Retrofit实例
		Retrofit retrofit = new Retrofit.Builder().baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
			.client(okHttpClientBuilder.build())
			.addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
			.build();

		this.jinaApi = retrofit.create(RerankerApi.class);
		this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");
	}

	public static RerankerClient.RerankerClientBuilder builder() {
		return new RerankerClient.RerankerClientBuilder();
	}

	/**
	 * 执行重排序请求
	 * <p>
	 * 根据模型类型自动路由到不同的服务 将响应结果转换为统一格式
	 * @param request 重排序请求参数
	 * @return 统一格式的重排序响应
	 * @throws IOException 网络请求异常
	 */
	public JinaRerankingResponse rerank(JinaRerankingRequest request) throws IOException {
		// 处理Jina模型请求
		if (!ModelSupportEnums.ALIYUN_GTE_RERANK_V2.getCode().equals(request.model)) {
			retrofit2.Response<JinaRerankingResponse> retrofitResponse = jinaApi.rerank(request, authorizationHeader)
				.execute();
			return retrofitResponse.body();
		}

		// 处理阿里云GTE Rerank V2模型请求
		// 转换请求格式
		DashscopeRerankingRequest dashscopeRerankingRequest = new DashscopeRerankingRequest(request.query,
				request.documents);
		Response<DashscopeRerankingResponse> requestResponse = jinaApi
			.aliRerank(dashscopeRerankingRequest, authorizationHeader)
			.execute();

		// 转换响应格式为Jina统一格式
		List<JinaRerankingResult> jinaRerankingResults = Arrays.stream(requestResponse.body().getOutput().getResults())
			.map(result -> {
				JinaRerankingResult jinaRerankingResult = new JinaRerankingResult();
				jinaRerankingResult.index = result.getIndex();
				jinaRerankingResult.relevanceScore = result.getRelevanceScore();
				jinaRerankingResult.document = result.getDocument();
				return jinaRerankingResult;
			})
			.toList();

		// 封装统一响应
		JinaRerankingResponse jinaRerankingResponse = new JinaRerankingResponse();
		jinaRerankingResponse.results = jinaRerankingResults;
		return jinaRerankingResponse;
	}

	/**
	 * 重排序客户端构建器
	 * <p>
	 * 使用构建器模式创建客户端实例 支持链式调用配置各项参数
	 */
	public static class RerankerClientBuilder {

		/**
		 * API基础URL
		 */
		private String baseUrl;

		/**
		 * API密钥
		 */
		private String apiKey;

		/**
		 * 请求超时时间
		 */
		private Duration timeout;

		/**
		 * 是否记录请求日志
		 */
		private boolean logRequests;

		/**
		 * 是否记录响应日志
		 */
		private boolean logResponses;

		RerankerClientBuilder() {
		}

		public RerankerClient.RerankerClientBuilder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public RerankerClient.RerankerClientBuilder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public RerankerClient.RerankerClientBuilder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public RerankerClient.RerankerClientBuilder logRequests(boolean logRequests) {
			this.logRequests = logRequests;
			return this;
		}

		public RerankerClient.RerankerClientBuilder logResponses(boolean logResponses) {
			this.logResponses = logResponses;
			return this;
		}

		public RerankerClient build() {
			return new RerankerClient(this.baseUrl, this.apiKey, this.timeout, this.logRequests, this.logResponses);
		}

		public String toString() {
			return "RerankerClient.RerankerClientBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey
					+ ", timeout=" + this.timeout + ", logRequests=" + this.logRequests + ", logResponses="
					+ this.logResponses + ")";
		}

	}

}
