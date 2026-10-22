package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.OpenRouterImageModelDTO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface AiAssistantOpenRouterService {

	/**
	 * 生成AI图像
	 * @param requst 图像生成请求对象，包含提示词、图像尺寸、风格等参数
	 * @return 生成的图像信息，包含图像URL和元数据
	 */
	@PostExchange("/chat/completions")
	OpenRouterImageModelDTO.ImageModelResponse generateImage(
			@RequestBody OpenRouterImageModelDTO.ImageModelRequest requst);

}
