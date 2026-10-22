package com.deepdimen.matrix.discover.dify.config;

import com.deepdimen.matrix.common.security.component.ApiTokenValidator;
import com.deepdimen.matrix.discover.dify.DifyAgentBridge;
import com.deepdimen.matrix.discover.dify.DifyAuthService;
import com.deepdimen.matrix.discover.dify.DifyConsoleClient;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Dify API 自动配置
 * <p>
 * 同时注册 Console API（RestClient）和 Service API（WebClient）的客户端
 *
 * @author matrix
 * @date 2026/03/14
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DifyProperties.class)
@ConditionalOnProperty(prefix = "matrix.dify", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DifyAutoConfiguration {

	/**
	 * Console API RestClient（仅在配置了 email/password 时创建）
	 */
	@Bean("difyRestClient")
	@Conditional(DifyConsoleCredentialsCondition.class)
	public RestClient difyRestClient(DifyProperties properties) {
		Assert.hasText(properties.getBaseUrl(), "matrix.dify.base-url 不能为空");

		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
			.build();

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeout()));

		RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);

		if (properties.isShowLog()) {
			builder.requestInterceptor(difyLoggingInterceptor());
		}

		return builder.build();
	}

	@Bean
	@Conditional(DifyConsoleCredentialsCondition.class)
	public DifyAuthService difyAuthService(DifyProperties properties,
			@Qualifier("difyRestClient") RestClient difyRestClient, ObjectMapper objectMapper) {
		return new DifyAuthService(properties, difyRestClient, objectMapper);
	}

	@Bean
	@Conditional(DifyConsoleCredentialsCondition.class)
	public DifyConsoleClient difyConsoleClient(DifyAuthService difyAuthService,
			@Qualifier("difyRestClient") RestClient difyRestClient, ObjectMapper objectMapper,
			DifyProperties properties) {
		return new DifyConsoleClient(difyAuthService, difyRestClient, objectMapper, properties.getBaseUrl());
	}

	@Bean
	@Conditional(DifyConsoleCredentialsCondition.class)
	public DifyAppApi difyAppApi(DifyConsoleClient difyConsoleClient) {
		return new DifyAppApi(difyConsoleClient);
	}

	@Bean
	@Conditional(DifyConsoleCredentialsCondition.class)
	public DifyAgentBridge difyAgentBridge(DifyAppApi difyAppApi, ApiTokenValidator apiTokenValidator,
			DifyProperties difyProperties) {
		return new DifyAgentBridge(difyAppApi, apiTokenValidator, difyProperties);
	}

	/**
	 * Dify Service API WebClient（基于 JDK HttpClient，支持 SSE 流式）
	 */
	@Bean("difyWebClient")
	public WebClient difyWebClient(DifyProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
			// Dify 的长连接 SSE 经反向代理时偶发 HTTP/2 GOAWAY，固定使用 HTTP/1.1
			// 可避免工作流已启动、响应流却被中途关闭，且不引入重复提交重试。
			.version(HttpClient.Version.HTTP_1_1)
			.build();

		JdkClientHttpConnector connector = new JdkClientHttpConnector(httpClient);

		return WebClient.builder()
			.clientConnector(connector)
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
			.build();
	}

	/**
	 * Dify Service API 客户端（API Key 鉴权 + SSE 流式透传）
	 */
	@Bean
	public DifyServiceClient difyServiceClient(@Qualifier("difyWebClient") WebClient difyWebClient,
			ObjectMapper objectMapper, DifyProperties properties) {
		return new DifyServiceClient(difyWebClient, objectMapper, properties.getServiceApiBaseUrl(),
				properties.getStreamReadTimeout());
	}

	/**
	 * Dify HTTP 请求日志拦截器
	 */
	private ClientHttpRequestInterceptor difyLoggingInterceptor() {
		return (request, body, execution) -> {
			log.info("[Dify] {} {}", request.getMethod(), request.getURI());
			var response = execution.execute(request, body);
			log.info("[Dify] Response: {}", response.getStatusCode());
			return response;
		};
	}

}
