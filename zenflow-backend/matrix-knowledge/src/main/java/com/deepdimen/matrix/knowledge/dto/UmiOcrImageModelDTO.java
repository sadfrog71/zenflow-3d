package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * Umi-OCR图像模型数据传输对象 用于封装Umi-OCR图像识别服务的请求和响应数据
 *
 * @author caoyuan
 * @date 2024/10/1
 *
 * 示例请求参数： { "base64": "iVBORw0KGgoAAAAN……", "options": { "ocr.language":
 * "models/config_chinese.txt", "ocr.cls": true, "ocr.limit_side_len": 4320,
 * "tbpu.parser": "multi_none", "data.format": "text" } }
 */
@Data
public class UmiOcrImageModelDTO {

	/**
	 * Umi-OCR图像模型请求内部类 封装图像OCR识别的请求参数
	 */
	@Data
	public static class UmiOcrImageModelRequest {

		/**
		 * 图像文件路径 指定要识别的图像文件位置
		 */
		private String images;

		/**
		 * Base64编码的图像数据 图像以Base64格式编码的字符串
		 */
		private String base64;

		/**
		 * OCR识别选项配置 包含OCR识别的各种参数设置
		 */
		private UmiOcrImageModelOptions options = new UmiOcrImageModelOptions();

		/**
		 * OCR选项配置内部类 封装OCR识别的详细参数
		 */
		@Data
		public static class UmiOcrImageModelOptions {

			/**
			 * OCR语言模型配置 默认使用中文配置文件
			 */
			private String ocrLanguage = "models/config_chinese.txt";

			/**
			 * 是否启用文本方向分类 true表示自动识别文本方向
			 */
			private boolean ocrCls = true;

			/**
			 * OCR识别的最大边长限制 用于控制图像处理的分辨率
			 */
			private int ocrLimitSideLen = 4320;

			/**
			 * 表格解析器类型 "multi_none"表示多行文本识别模式
			 */
			private String tbpuParser = "multi_none";

			/**
			 * 数据输出格式 "text"表示以纯文本格式输出
			 */
			private String dataFormat = "text";

		}

	}

	/**
	 * Umi-OCR图像模型响应内部类 封装图像OCR识别的响应数据
	 */
	@Data
	public static class UmiOcrImageModelResponse {

		/**
		 * 响应状态码 0表示成功，其他值表示错误
		 */
		private int code;

		/**
		 * 识别结果数据列表 包含所有识别出的文本数据
		 */
		private List<TextData> data;

		/**
		 * 文本数据内部类 封装单个文本识别结果
		 */
		@Data
		public static class TextData {

			/**
			 * 识别出的文本内容
			 */
			private String text;

			/**
			 * 文本结束标记 用于标识文本段落的结束
			 */
			private String end;

		}

	}

}
