package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI 使用统计DTO
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiUsage implements Serializable {

	/**
	 * 提示token数量
	 */
	@JsonProperty("prompt_tokens")
	private Integer promptTokens;

	/**
	 * 完成token数量
	 */
	@JsonProperty("completion_tokens")
	private Integer completionTokens;

	/**
	 * 总token数量
	 */
	@JsonProperty("total_tokens")
	private Integer totalTokens;

	/**
	 * 完成token详情
	 */
	@JsonProperty("completion_tokens_details")
	private CompletionTokensDetails completionTokensDetails;

	/**
	 * 提示token详情
	 */
	@JsonProperty("prompt_tokens_details")
	private PromptTokensDetails promptTokensDetails;

	public OpenAiUsage() {
	}

	public OpenAiUsage(Integer promptTokens, Integer completionTokens) {
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
		this.totalTokens = promptTokens + completionTokens;

		// 设置默认的详情
		this.completionTokensDetails = new CompletionTokensDetails(completionTokens);
		this.promptTokensDetails = new PromptTokensDetails(0);
	}

	@Data
	public static class CompletionTokensDetails implements Serializable {

		@JsonProperty("text_tokens")
		private Integer textTokens;

		public CompletionTokensDetails() {
		}

		public CompletionTokensDetails(Integer textTokens) {
			this.textTokens = textTokens;
		}

	}

	@Data
	public static class PromptTokensDetails implements Serializable {

		@JsonProperty("cached_tokens")
		private Integer cachedTokens;

		public PromptTokensDetails() {
		}

		public PromptTokensDetails(Integer cachedTokens) {
			this.cachedTokens = cachedTokens;
		}

	}

}