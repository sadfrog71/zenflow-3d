package com.deepdimen.matrix.knowledge.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

/**
 * AI数据库字段注释建议结果数据传输对象
 * <p>
 * 用于封装AI对数据库表和字段注释的优化建议，帮助改进数据库文档质量
 *
 * @author caoyuan
 * @date 2025/3/27
 */
@Data
@Description("数据库注释建议结果")
public class AiSuggestionFieldResultDTO {

	/**
	 * AI建议的表注释
	 * <p>
	 * AI分析后建议的更准确、更规范的表注释内容
	 */
	@Description("AI建议更准确表注释")
	String tableSuggestionComment;

	/**
	 * 字段注释建议列表
	 * <p>
	 * 包含所有字段的原始注释和AI建议的优化注释
	 */
	@Description("建议字段注释列表")
	List<AiSuggestionFieldDTO> suggestions;

	/**
	 * AI字段注释建议内部类
	 * <p>
	 * 封装单个字段的注释建议信息
	 */
	@Data
	@Description("字段建议")
	public static class AiSuggestionFieldDTO {

		/**
		 * 字段名称
		 * <p>
		 * 数据库表中的字段名，如：id、user_name等
		 */
		@Description("字段名称，例如 id")
		private String fieldName;

		/**
		 * 原始字段注释
		 * <p>
		 * 数据库中当前的字段注释内容
		 */
		@Description("字段注释，例如 主键")
		private String fieldComment;

		/**
		 * AI建议的字段注释
		 * <p>
		 * AI分析后建议的更准确、更规范的字段注释内容
		 */
		@Description("AI建议更准确字段注释，例如 主键ID")
		private String fieldSuggestionComment;

	}

}
