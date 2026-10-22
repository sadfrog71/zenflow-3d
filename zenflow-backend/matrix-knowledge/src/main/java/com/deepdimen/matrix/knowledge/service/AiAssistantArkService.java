package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.AiImageArkCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiMultimodalEmbeddingArkDTO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 火山引擎AI服务接口 <a href="https://cloud.volcengine.com/product/ai-ark">火山引擎AI官网</a>
 *
 * @author caoyuan
 * @date 2025/09/10
 */
public interface AiAssistantArkService {

	/**
	 * 生成图片
	 * @param request 图片生成请求参数
	 * @return 图片生成响应结果
	 */
	@PostExchange("/images/generations")
	AiImageArkCompletionsDTO.Response generateImage(@RequestBody AiImageArkCompletionsDTO.Request request);

	/**
	 * 多模态嵌入向量化
	 * <p>
	 * 支持文本和图像的多模态内容向量化，用于语义搜索和相似度计算 API文档: https://volcengine.com/docs/82379/1099455
	 * @param request 多模态嵌入请求参数
	 * @return 多模态嵌入响应结果
	 */
	@PostExchange("/embeddings/multimodal")
	AiMultimodalEmbeddingArkDTO.Response multimodalEmbedding(@RequestBody AiMultimodalEmbeddingArkDTO.Request request);

}
