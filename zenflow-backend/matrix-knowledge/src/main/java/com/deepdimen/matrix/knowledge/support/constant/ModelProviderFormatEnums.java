package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型提供商JSON格式枚举类 定义了不同模型提供商支持的JSON响应格式类型
 *
 * @author caoyuan
 * @date 2025/02/13
 */
@RequiredArgsConstructor
public enum ModelProviderFormatEnums {

	/**
	 * Ollama - 本地运行的大模型框架，支持json_schema格式
	 */
	OLLAMA("Ollama", "json_schema"),

	/**
	 * OpenAI - OpenAI公司的模型，支持json_schema格式
	 */
	OPENAI("OpenAI", "json_schema"),

	/**
	 * OpenRouter - 多模型路由服务，支持json_schema格式
	 */
	OPEN_ROUTER("OpenRouter", "json_schema"),

	/**
	 * 其他提供商 - 使用通用的json_object格式
	 */
	OTHER("Other", "json_object");

	/**
	 * 模型提供商名称
	 */
	private final String provider;

	/**
	 * JSON响应格式类型
	 */
	@Getter
	private final String format;

	/**
	 * 根据提供商名称获取对应的JSON格式
	 * @param provider 提供商名称
	 * @return JSON格式类型，如果未找到则返回默认的json_object格式
	 */
	public static String getFormat(String provider) {
		for (ModelProviderFormatEnums providerFormat : ModelProviderFormatEnums.values()) {
			if (providerFormat.provider.equals(provider)) {
				return providerFormat.format;
			}
		}
		return OTHER.format;
	}

}
