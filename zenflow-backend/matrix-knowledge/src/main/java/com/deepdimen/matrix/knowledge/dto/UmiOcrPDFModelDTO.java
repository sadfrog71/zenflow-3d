package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * Umi-OCR PDF模型数据传输对象 用于封装Umi-OCR PDF文档识别服务的请求和响应数据
 *
 * @author caoyuan
 * @date 2024/10/1
 *
 * 示例请求参数： { "base64": "iVBORw0KGgoAAAAN……", "options": { "ocr.language":
 * "models/config_chinese.txt", "ocr.cls": true, "ocr.limit_side_len": 4320,
 * "tbpu.parser": "multi_none", "data.format": "text" } }
 */
@Data
public class UmiOcrPDFModelDTO {

	/**
	 * Umi-OCR PDF模型请求内部类 封装PDF文档OCR识别的请求参数
	 */
	@Data
	public static class UmiOcrPDFModelRequest {

		/**
		 * Base64编码的PDF数据 PDF文件以Base64格式编码的字符串
		 */
		private String base64;

		/**
		 * OCR识别选项配置 包含PDF OCR识别的各种参数设置
		 */
		private UmiOcrPDFModelOptions options = new UmiOcrPDFModelOptions();

		/**
		 * PDF OCR选项配置内部类 封装PDF OCR识别的详细参数
		 */
		@Data
		public static class UmiOcrPDFModelOptions {

			/**
			 * OCR语言模型配置 默认使用中文配置文件
			 */
			private String ocrLanguage = "models/config_chinese.txt";

			/**
			 * 是否启用文本方向分类 true表示自动识别文本方向
			 */
			private boolean ocrCls = true;

			/**
			 * OCR识别的最大边长限制 用于控制PDF页面处理的分辨率
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
	 * PDF OCR结果查询请求内部类 用于查询PDF OCR任务的处理结果
	 */
	@Data
	public static class UmiOcrPDFModelResultRequest {

		/**
		 * 任务ID PDF OCR任务的唯一标识符
		 */
		private String id;

	}

	/**
	 * PDF OCR响应内部类 封装PDF OCR任务提交的响应数据
	 */
	@Data
	public static class UmiOcrPDFModelResponse {

		/**
		 * 响应状态码 0表示成功，其他值表示错误
		 */
		private int code;

		/**
		 * 响应数据 通常包含任务ID等信息
		 */
		private String data;

	}

	/**
	 * PDF OCR结果查询响应内部类 封装PDF OCR任务状态查询的响应数据
	 */
	@Data
	public static class UmiOcrPDFModelResultResponse {

		/**
		 * 响应状态码 0表示成功，其他值表示错误
		 */
		private int code;

		/**
		 * 任务处理状态 如：processing（处理中）、completed（完成）、failed（失败）
		 */
		private String state;

	}

	/**
	 * PDF OCR下载请求内部类 用于下载PDF OCR识别结果
	 */
	@Data
	public static class UmiOcrPDFModelDownRequest {

		/**
		 * 任务ID PDF OCR任务的唯一标识符
		 */
		private String id;

		/**
		 * 文件类型列表 指定需要下载的文件格式
		 */
		private List<String> file_types;

		/**
		 * 是否忽略空白页 true表示在结果中排除空白页
		 */
		private boolean ingore_blank;

	}

	/**
	 * PDF OCR下载响应内部类 封装PDF OCR结果下载的响应数据
	 */
	@Data
	public static class UmiOcrPDFModelDownResponse {

		/**
		 * 响应状态码 0表示成功，其他值表示错误
		 */
		private int code;

		/**
		 * 下载数据 包含识别结果的数据内容
		 */
		private String data;

		/**
		 * 文件名 下载文件的名称
		 */
		private String name;

	}

}
