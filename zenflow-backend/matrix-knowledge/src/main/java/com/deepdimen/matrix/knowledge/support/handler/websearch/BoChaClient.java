package com.deepdimen.matrix.knowledge.support.handler.websearch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.web.search.WebSearchRequest;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * BoCha搜索客户端
 * <p>
 * 封装与BoCha搜索API的交互逻辑，使用Retrofit进行HTTP请求 提供简单的搜索接口，支持自定义超时和认证配置
 *
 * @author caoyuan
 * @date 2025/06/20
 */
public class BoChaClient {

	/**
	 * Jackson对象映射器 配置忽略未知属性，启用格式化输出
	 */
	public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
		.enable(SerializationFeature.INDENT_OUTPUT);

	/**
	 * 搜索API接口
	 */
	private final SearchApi api;

	/**
	 * 构造方法，创建BoChaClient实例
	 * @param baseUrl 基础URL地址
	 * @param apiKey API密钥
	 * @param timeout 超时时间
	 */
	public BoChaClient(String baseUrl, String apiKey, Duration timeout) {
		OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder().callTimeout(timeout)
			.addInterceptor(new AuthorizationHeaderInjector(apiKey))
			.connectTimeout(timeout)
			.readTimeout(timeout)
			.writeTimeout(timeout);

		Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl)
			.client(okHttpClientBuilder.build())
			.addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
			.build();
		this.api = retrofit.create(SearchApi.class);
	}

	/**
	 * 执行搜索操作
	 * @param request 搜索请求参数
	 * @return 搜索结果响应
	 * @throws RuntimeException 当网络请求发生IO异常时抛出
	 */
	SearchResponse search(WebSearchRequest request) {
		try {
			final Response<SearchResponse> response = api
				.webSearch(SearchRequest.builder().query(request.searchTerms()).count(request.maxResults()).build())
				.execute();
			return response.body();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
