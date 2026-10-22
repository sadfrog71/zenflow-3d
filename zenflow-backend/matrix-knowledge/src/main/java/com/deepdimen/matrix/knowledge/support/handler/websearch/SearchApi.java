package com.deepdimen.matrix.knowledge.support.handler.websearch;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * 搜索API接口定义
 * <p>
 * 使用Retrofit框架定义的HTTP API接口 用于与BoCha搜索服务进行交互
 *
 * @author caoyuan
 * @date 2025/06/20
 */
public interface SearchApi {

	/**
	 * 执行网页搜索
	 * <p>
	 * 通过POST请求调用web-search接口 请求体为JSON格式的搜索参数
	 * @param request 搜索请求参数（包含搜索关键词、结果数量等）
	 * @return Retrofit Call对象，包含搜索响应结果
	 */
	@POST("web-search")
	@Headers("Content-Type: application/json")
	Call<SearchResponse> webSearch(@Body SearchRequest request);

}
