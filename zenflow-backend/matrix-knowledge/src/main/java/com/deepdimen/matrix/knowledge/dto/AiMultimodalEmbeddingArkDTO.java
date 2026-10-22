package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * 火山引擎ARK多模态嵌入数据传输对象
 * <p>
 * 用于豆包多模态模型的向量化处理，支持文本和图像内容的嵌入 参考API:
 * https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal
 *
 * @author caoyuan
 * @date 2025/09/27
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiMultimodalEmbeddingArkDTO {

	/**
	 * 多模态嵌入请求类
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Request {

		/**
		 * 模型名称，例如: doubao-embedding-vision-250615
		 */
		private String model;

		/**
		 * 输入内容列表，支持文本和图像
		 */
		private List<InputItem> input;

		/**
		 * 返回向量的维度，可选参数
		 */
		private Integer dimensions;

	}

	/**
	 * 输入项基类
	 */
	@Data
	public static class InputItem {

		/**
		 * 输入类型，text 或 image_url
		 */
		private String type;

	}

	/**
	 * 文本输入项
	 */
	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class TextInput extends InputItem {

		/**
		 * 文本内容
		 */
		private String text;

		public TextInput() {
			setType("text");
		}

		@Builder
		public TextInput(String text) {
			this();
			this.text = text;
		}

	}

	/**
	 * 图像输入项
	 */
	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class ImageInput extends InputItem {

		/**
		 * 图像URL信息
		 */
		@JsonProperty("image_url")
		private ImageUrl imageUrl;

		public ImageInput() {
			setType("image_url");
		}

		public ImageInput(String url) {
			this();
			this.imageUrl = ImageUrl.builder().url(url).build();
		}

		@Builder
		public ImageInput(ImageUrl imageUrl) {
			this();
			this.imageUrl = imageUrl;
		}

	}

	/**
	 * 图像URL信息
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ImageUrl {

		/**
		 * 图像URL地址
		 */
		private String url;

		/**
		 * 图像细节级别，可选值：low, high, auto 默认为 auto
		 */
		@Builder.Default
		private String detail = "auto";

	}

	/**
	 * 多模态嵌入响应类
	 */
	@Data
	public static class Response {

		/**
		 * 请求唯一标识
		 */
		private String id;

		/**
		 * 对象类型，通常为 "list"
		 */
		private String object;

		/**
		 * 创建时间戳
		 */
		private Long created;

		/**
		 * 使用的模型名称
		 */
		private String model;

		/**
		 * 嵌入向量数据
		 */
		private EmbeddingData data;

		/**
		 * 使用统计信息
		 */
		private Usage usage;

	}

	/**
	 * 嵌入向量数据
	 */
	@Data
	public static class EmbeddingData {

		/**
		 * 对象类型，通常为 "embedding"
		 */
		private String object;

		/**
		 * 嵌入向量，浮点数列表
		 */
		private List<Float> embedding;

	}

	/**
	 * 使用统计信息
	 */
	@Data
	public static class Usage {

		/**
		 * 提示词token数量
		 */
		@JsonProperty("prompt_tokens")
		private Integer promptTokens;

		/**
		 * 总token数量
		 */
		@JsonProperty("total_tokens")
		private Integer totalTokens;

		/**
		 * 提示词token详细信息
		 */
		@JsonProperty("prompt_tokens_details")
		private PromptTokensDetails promptTokensDetails;

	}

	/**
	 * 提示词token详细信息
	 */
	@Data
	public static class PromptTokensDetails {

		/**
		 * 图像token数量
		 */
		@JsonProperty("image_tokens")
		private Integer imageTokens;

		/**
		 * 文本token数量
		 */
		@JsonProperty("text_tokens")
		private Integer textTokens;

	}

}