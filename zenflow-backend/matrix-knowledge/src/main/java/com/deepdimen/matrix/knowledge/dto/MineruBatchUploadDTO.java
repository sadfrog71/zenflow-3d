package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Mineru批量文件上传解析请求数据传输对象 用于封装批量文件上传和解析的请求参数
 *
 * @author caoyuan
 * @date 2025/06/01
 */
@Data
public class MineruBatchUploadDTO {

	/**
	 * 是否开启公式识别 默认为true，用于识别文档中的数学公式
	 */
	@JsonProperty("enable_formula")
	private Boolean enableFormula = true;

	/**
	 * 是否开启表格识别 默认为true，用于识别文档中的表格结构
	 */
	@JsonProperty("enable_table")
	private Boolean enableTable = true;

	/**
	 * 指定文档语言 默认为"ch"（中文），可以设置为"auto"自动识别
	 */
	private String language = "ch";

	/**
	 * 解析结果回调通知URL 解析完成后，系统会调用此URL通知结果
	 */
	private String callback;

	/**
	 * 随机字符串 用于回调通知请求中的签名验证
	 */
	private String seed;

	/**
	 * 额外导出格式 支持的格式包括：docx、html、latex
	 */
	@JsonProperty("extra_formats")
	private List<String> extraFormats;

	/**
	 * 文件列表 包含待上传解析的文件信息
	 */
	private List<FileInfo> files;

	/**
	 * 文件信息内部类 封装单个文件的相关信息
	 */
	@Data
	public static class FileInfo {

		/**
		 * 文件名 支持的格式：.pdf、.doc、.docx、.ppt、.pptx、.png、.jpg、.jpeg
		 */
		private String name;

		/**
		 * 是否启用OCR功能 默认为false，对于扫描版PDF或图片文件建议开启
		 */
		@JsonProperty("is_ocr")
		private Boolean isOcr = false;

		/**
		 * 解析对象对应的数据ID 用于关联解析结果与业务数据
		 */
		@JsonProperty("data_id")
		private String dataId;

		/**
		 * 指定页码范围 格式为逗号分隔的字符串，如"1-5,8,10-12"
		 */
		@JsonProperty("page_ranges")
		private String pageRanges;

	}

}