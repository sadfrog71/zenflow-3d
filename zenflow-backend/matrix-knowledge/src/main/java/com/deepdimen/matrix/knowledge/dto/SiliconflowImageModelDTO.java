package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * 硅流图像模型数据传输对象 用于封装图像生成相关的请求和响应数据
 *
 * @author caoyuan
 * @date 2024/9/30
 *
 * 示例参数： "model": "<string>", "prompt": "<string>", "image_size": "1024x1024", "seed":
 * 4999999999
 */
@Data
public class SiliconflowImageModelDTO {

	/**
	 * 图像模型响应内部类 封装图像生成的响应数据
	 */
	@Data
	public static class ImageModelResponse {

		/**
		 * 生成的图像列表 包含生成的所有图像信息
		 */
		private List<Image> images;

		/**
		 * 随机种子 用于复现图像生成结果的种子值
		 */
		private Long seed;

	}

	/**
	 * 图像内部类 封装单个图像的信息
	 */
	@Data
	public static class Image {

		/**
		 * 图像URL 生成的图像的访问地址
		 */
		private String url;

	}

}
