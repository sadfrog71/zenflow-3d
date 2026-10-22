package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.openai.internal.chat.Content;
import dev.langchain4j.model.openai.internal.chat.ImageUrl;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * OpenRouter图像模型数据传输对象 用于封装与OpenRouter API交互时的图像生成相关的请求和响应数据
 *
 * 该DTO类主要用于： 1. 构建向OpenRouter发送的图像生成请求 2. 解析从OpenRouter返回的图像生成响应 3.
 * 提供统一的数据结构来处理图像相关的AI模型交互
 *
 * @author caoyuan
 * @date 2024/10/8
 */
@Data
public class OpenRouterImageModelDTO {

	/**
	 * 图像模型请求类 用于构建发送给OpenRouter的图像生成请求数据
	 *
	 * 包含模型配置、用户消息和处理模式等信息
	 *
	 * @author caoyuan
	 * @date 2025/09/03
	 */
	@Data
	public static class ImageModelRequest {

		/**
		 * 指定使用的AI模型名称
		 */
		String model;

		/**
		 * 用户消息列表 包含用户的文本描述和可能的图像输入
		 */
		List<ImageUserMessage> messages;

		/**
		 * 处理模式列表 指定模型应该处理的数据类型，如文本、图像等 例如：["text", "image"]
		 */
		private List<String> modalities;

	}

	/**
	 * 图像用户消息类 表示用户发送的消息，可以包含文本和图像内容
	 *
	 * 通常用于多模态对话场景，用户可以同时发送文本描述和图像
	 */
	@Data
	@AllArgsConstructor
	public static class ImageUserMessage {

		/**
		 * 消息角色 通常为 "user"（用户）或 "assistant"（助手） 用于标识消息的发送者类型
		 */
		private String role;

		/**
		 * 消息内容列表 可以包含多种类型的内容：文本、图像URL等 支持多模态输入，一条消息可以同时包含文字描述和图片
		 */
		private List<Content> content;

	}

	/**
	 * 图像模型响应类 用于封装从OpenRouter返回的图像生成响应数据
	 *
	 * 包含模型生成的所有选择项和相关元数据
	 */
	@Data
	public static class ImageModelResponse {

		/**
		 * 模型生成的选择列表 每个Choice代表模型的一个可能输出结果 通常包含生成的图像和相关信息
		 */
		private List<Choice> choices;

	}

	/**
	 * 选择项类 表示模型生成的一个具体选择结果 包含生成的消息内容
	 */
	@Data
	public static class Choice {

		/**
		 * 生成的消息内容 包含模型返回的具体信息，如生成的图像列表
		 */
		private Message message;

	}

	/**
	 * 消息类 封装模型返回的消息内容 主要包含生成的图像列表
	 */
	@Data
	public static class Message {

		/**
		 * 生成的图像列表 包含模型生成的所有图像信息 每个Image对象代表一张生成的图片
		 */
		private List<Image> images;

	}

	/**
	 * 图像类 封装单个图像的详细信息
	 *
	 * 使用蛇形命名策略进行JSON序列化， 确保与API返回的字段名格式一致
	 */
	@Data
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class Image {

		/**
		 * 图像URL对象 包含生成图像的访问地址和相关元数据 通过此URL可以访问和下载生成的图像
		 */
		private ImageUrl imageUrl;

	}

}
