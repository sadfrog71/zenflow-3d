package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI存储通用请求数据传输对象
 * <p>
 * 用于存储模块的各类操作请求，包括文件管理、分组管理等
 *
 * @author caoyuan
 * @date 2025/09/30
 */
@Data
@Schema(description = "AI存储通用请求参数")
public class AiStorageDTO {

	/**
	 * 文件ID
	 */
	@Schema(description = "文件ID")
	private Long fileId;

	/**
	 * 文件ID列表
	 */
	@Schema(description = "文件ID列表")
	private List<Long> ids;

	/**
	 * 分组ID
	 */
	@Schema(description = "分组ID")
	private Long groupId;

	/**
	 * 置信度
	 */
	@Schema(description = "置信度分数", example = "0.95")
	private BigDecimal confidence;

	/**
	 * 图片Base64编码
	 */
	@Schema(description = "图片Base64编码")
	private String imageBase64;

	/**
	 * 最大返回结果数
	 */
	@Schema(description = "最大返回结果数", defaultValue = "10")
	private Integer maxResults;

}