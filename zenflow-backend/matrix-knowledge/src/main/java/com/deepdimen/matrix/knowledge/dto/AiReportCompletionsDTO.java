package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * AI报告生成完成数据传输对象
 * <p>
 * 用于传输AI报告生成相关的参数和处理结果，支持报告内容的生成和优化
 *
 * @author caoyuan
 * @date 2025/05/11
 */
@Data
@FieldNameConstants
public class AiReportCompletionsDTO {

	/**
	 * 报告ID
	 * <p>
	 * 报告的唯一标识符，用于跟踪和管理报告生成过程
	 */
	private Long reportId;

	/**
	 * AI模型标识
	 * <p>
	 * 指定用于生成报告的AI模型（如：gpt-4、claude等）
	 */
	private String model;

	/**
	 * 生成提示词
	 * <p>
	 * 用于指导AI生成报告内容的详细提示信息
	 */
	private String prompt;

	/**
	 * AI润色处理结果
	 * <p>
	 * 经过AI优化和润色后的报告内容，提升报告的可读性和专业性
	 */
	private String polish;

}
