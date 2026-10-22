package com.deepdimen.matrix.knowledge.dto.dify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Dify 外部知识库检索响应 DTO
 * <p>
 * 对齐 Dify External Knowledge API 标准响应格式
 *
 * @author matrix
 * @date 2026/04/06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyRetrievalResponseDTO {

	private List<Record> records;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Record {

		private String content;

		private String title;

		private Double score;

		private Map<String, Object> metadata;

	}

}
