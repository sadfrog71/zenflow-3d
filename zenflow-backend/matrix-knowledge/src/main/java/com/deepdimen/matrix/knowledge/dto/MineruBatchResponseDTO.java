package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Mineru批量操作响应数据传输对象 用于封装Mineru文档解析服务的批量操作响应数据
 *
 * @author caoyuan
 * @date 2025/06/01
 */
@Data
public class MineruBatchResponseDTO {

	/**
	 * 接口状态码 成功：0，其他值表示失败
	 */
	private Integer code;

	/**
	 * 接口处理信息 成功："ok"，失败时返回错误信息
	 */
	private String msg;

	/**
	 * 请求ID 用于追踪请求的唯一标识符
	 */
	@JsonProperty("trace_id")
	private String traceId;

	/**
	 * 响应数据 包含批量处理的详细信息
	 */
	private BatchData data;

	/**
	 * 批量数据内部类 封装批量处理的详细数据
	 */
	@Data
	public static class BatchData {

		/**
		 * 批量提取任务ID 用于标识和查询批量任务的唯一标识符
		 */
		@JsonProperty("batch_id")
		private String batchId;

		/**
		 * 文件上传链接列表 申请上传链接时返回，用于上传待解析的文件
		 */
		@JsonProperty("file_urls")
		private List<String> fileUrls;

		/**
		 * 提取结果列表 查询结果时返回，包含每个文件的解析结果
		 */
		@JsonProperty("extract_result")
		private List<ExtractResult> extractResult;

	}

	/**
	 * 提取结果内部类 封装单个文件的解析结果信息
	 */
	@Data
	public static class ExtractResult {

		/**
		 * 文件名 被解析文件的名称
		 */
		@JsonProperty("file_name")
		private String fileName;

		/**
		 * 任务处理状态 done-完成，waiting-file-等待文件上传，pending-排队中，
		 * running-正在解析，failed-解析失败，converting-格式转换中
		 */
		private String state;

		/**
		 * 文件解析结果压缩包URL 解析完成后，可通过此URL下载解析结果
		 */
		@JsonProperty("full_zip_url")
		private String fullZipUrl;

		/**
		 * 解析失败原因 当state=failed时有效，描述失败的具体原因
		 */
		@JsonProperty("err_msg")
		private String errMsg;

		/**
		 * 解析对象对应的数据ID 用于关联解析结果与原始请求
		 */
		@JsonProperty("data_id")
		private String dataId;

		/**
		 * 解析进度信息 包含文档解析的进度详情
		 */
		@JsonProperty("extract_progress")
		private ExtractProgress extractProgress;

	}

	/**
	 * 解析进度内部类 封装文档解析的进度信息
	 */
	@Data
	public static class ExtractProgress {

		/**
		 * 文档已解析页数 表示已经完成解析的页面数量
		 */
		@JsonProperty("extracted_pages")
		private Integer extractedPages;

		/**
		 * 文档总页数 待解析文档的总页面数量
		 */
		@JsonProperty("total_pages")
		private Integer totalPages;

		/**
		 * 文档解析开始时间 记录解析任务的开始时间
		 */
		@JsonProperty("start_time")
		private String startTime;

	}

}