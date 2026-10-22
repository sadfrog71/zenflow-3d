package com.deepdimen.matrix.knowledge.dto.dify;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Dify 外部知识库检索请求 DTO
 * <p>
 * 对齐 Dify External Knowledge API 标准请求格式
 *
 * @author matrix
 * @date 2026/04/06
 */
@Data
public class DifyRetrievalRequestDTO {

	private String query;

	@JsonProperty("knowledge_id")
	private String knowledgeId;

	@JsonProperty("retrieval_setting")
	private RetrievalSetting retrievalSetting;

	// 当前未使用, Matrix recall 不支持 metadata 条件过滤
	@JsonProperty("metadata_condition")
	private Object metadataCondition;

	@Data
	public static class RetrievalSetting {

		@JsonProperty("top_k")
		private Integer topK;

		@JsonProperty("score_threshold")
		private Double scoreThreshold;

	}

}
