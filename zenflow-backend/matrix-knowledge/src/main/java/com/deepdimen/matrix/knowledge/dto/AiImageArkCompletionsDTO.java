package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 火山引擎AI图像生成数据传输对象
 * <p>
 * 用于AI图像生成和编辑任务的参数传递，支持文生图和图生图功能
 *
 **/
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY) // 仅当字段非 null 且非 "" 时才序列化
public class AiImageArkCompletionsDTO {

	/**
	 * 请求类：用于图像生成任务的请求参数封装
	 *
	 * @author caoyuan
	 * @date 2025/09/10
	 */
	@Data
	public static class Request {

		/**
		 * 模型名称
		 */
		private String model;

		/**
		 * 正向提示词 描述想要生成的图像内容
		 */
		private String prompt;

		/**
		 * 图像URL 用于图生图任务的输入图像地址
		 */
		private String image;

		/**
		 * 图像尺寸 生成图像的分辨率，如：512x512、1024x1024等
		 */
		private String size;

		/**
		 * 连续图像生成模式 是否启用连续图像生成
		 */
		@JsonProperty("sequential_image_generation")
		private String sequentialImageGeneration;

		/**
		 * 流式响应 是否启用流式响应
		 */
		private Boolean stream;

		/**
		 * 响应格式 返回图像的格式，如URL或Base64
		 */
		@JsonProperty("response_format")
		private String responseFormat;

		/**
		 * 水印 是否在生成的图像上添加水印
		 */
		private Boolean watermark;

	}

	/**
	 * 响应类：包含模型生成图像的响应信息
	 *
	 * @author caoyuan
	 * @date 2025/09/10
	 */
	@Data
	public static class Response {

		/**
		 * 使用的模型名称
		 */
		private String model;

		/**
		 * 创建时间 时间戳，表示图像生成的时间
		 */
		private Long created;

		/**
		 * 生成的图像列表 包含生成的所有图像信息
		 */
		private List<Image> data;

	}

	@Data
	public static class Image {

		/**
		 * 图像URL 生成的图像的访问地址
		 */
		private String url;

		/**
		 * 图像尺寸 生成图像的分辨率，如：512x512、1024x1024等
		 */
		private String size;

	}

}
