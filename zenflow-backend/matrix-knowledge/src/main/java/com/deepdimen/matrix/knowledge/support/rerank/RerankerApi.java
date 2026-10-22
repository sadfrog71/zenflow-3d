package com.deepdimen.matrix.knowledge.support.rerank;

import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * 重排序API接口定义
 * <p>
 * 使用Retrofit框架定义的HTTP API接口 支持多种重排序服务：Jina和阿里云达摩院 用于对检索结果进行二次排序，提高相关性
 *
 * @author caoyuan
 * @date 2025/06/26
 */
public interface RerankerApi {

	/**
	 * Jina重排序接口
	 * <p>
	 * 调用Jina的重排序服务对文档进行相关性排序
	 * @param request 重排序请求参数，包含查询和文档列表
	 * @param authorizationHeader Bearer Token认证头
	 * @return Retrofit Call对象，包含重排序响应结果
	 */
	@POST("rerank")
	@Headers({ "Content-Type: application/json" })
	Call<JinaRerankingResponse> rerank(@Body JinaRerankingRequest request,
			@Header("Authorization") String authorizationHeader);

	/**
	 * 阿里云达摩院重排序接口
	 * <p>
	 * 调用阿里云的gte-rerank模型进行文档重排序 适用于中文场景的高精度排序
	 * @param request 重排序请求参数，包含查询和文档列表
	 * @param authorizationHeader Bearer Token认证头
	 * @return Retrofit Call对象，包含重排序响应结果
	 */
	@POST("/api/v1/services/rerank/text-rerank/text-rerank")
	@Headers({ "Content-Type: application/json" })
	Call<DashscopeRerankingResponse> aliRerank(@Body DashscopeRerankingRequest request,
			@Header("Authorization") String authorizationHeader);

}
