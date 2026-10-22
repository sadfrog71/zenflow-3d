package com.deepdimen.matrix.knowledge.config.properties;

import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;
import lombok.Data;

/**
 * AI连接配置属性
 * 
 * @author caoyuan
 * @date 2024/9/26
 */
@Data
public class AiConnectionProperties {

	/**
	 * API 密钥
	 */
	private String apiKey;

	/**
	 * 基础 URL
	 */
	private String baseUrl;

	/**
	 * 模型名称
	 */
	private String modelName = OpenAiChatModelName.GPT_4_O_MINI.name();

	/**
	 * 嵌入模型名称
	 */
	private String embeddingModelName = OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL.name();

	/**
	 * 温度系数（控制输出的随机性，0-1之间）
	 */
	private Double temperature = 0.5;

}
