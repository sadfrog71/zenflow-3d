package com.deepdimen.matrix.knowledge.support.handler.websearch;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * Ollama搜索API接口定义
 * <p>
 * 使用Retrofit框架定义的HTTP API接口 用于与Ollama Web Search服务进行交互
 *
 * @author caoyuan
 * @date 2025/09/26
 */
public interface OllamaSearchApi {

	/**
	 * 执行网页搜索
	 * <p>
	 * 通过POST请求调用web_search接口 请求体为JSON格式的搜索参数
	 * @param request 搜索请求参数（包含搜索关键词）
	 * @return Retrofit Call对象，包含搜索响应结果
	 */
	@POST("api/web_search")
	@Headers("Content-Type: application/json")
	Call<OllamaSearchResponse> webSearch(@Body OllamaSearchRequest request);

}
