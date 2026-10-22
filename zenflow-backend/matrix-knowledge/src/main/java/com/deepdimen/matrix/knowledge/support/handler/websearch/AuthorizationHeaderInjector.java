package com.deepdimen.matrix.knowledge.support.handler.websearch;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;

import java.io.IOException;

/**
 * 授权头部信息注入拦截器
 * <p>
 * 用于在HTTP请求中自动添加Bearer Token认证头 实现OkHttp的拦截器接口，统一处理API认证
 *
 * @author caoyuan
 * @date 2025/06/20
 */
public class AuthorizationHeaderInjector implements Interceptor {

	/**
	 * API密钥
	 */
	private final String apiKey;

	public AuthorizationHeaderInjector(String apiKey) {
		this.apiKey = apiKey;
	}

	/**
	 * 拦截请求并添加授权头部信息
	 * @param chain 请求链
	 * @return 处理后的响应
	 * @throws IOException 当IO操作失败时抛出
	 */
	@NotNull
	@Override
	public Response intercept(@NotNull Chain chain) throws IOException {
		Request.Builder builder = chain.request().newBuilder();

		// 添加Bearer Token认证头
		builder.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
		return chain.proceed(builder.build());
	}

}
