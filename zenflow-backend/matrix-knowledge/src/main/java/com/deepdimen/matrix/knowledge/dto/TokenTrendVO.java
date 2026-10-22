package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Token使用趋势数据传输对象
 * <p>
 * 展示promptTokens、completionTokens、totalTokens的时间变化趋势
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "Token使用趋势")
public class TokenTrendVO {

	/**
	 * 日期数组
	 */
	@Schema(description = "日期数组", example = "[\"2025-01-01\", \"2025-01-02\"]")
	private List<String> dates;

	/**
	 * Prompt Token 数组
	 */
	@Schema(description = "Prompt Token数组")
	private List<Long> promptTokens;

	/**
	 * Completion Token 数组
	 */
	@Schema(description = "Completion Token数组")
	private List<Long> completionTokens;

	/**
	 * 总 Token 数组
	 */
	@Schema(description = "总Token数组")
	private List<Long> totalTokens;

}
