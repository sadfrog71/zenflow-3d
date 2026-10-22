package com.deepdimen.matrix.knowledge.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Tenant-scoped relational fallback hit used when the vector index is unavailable.
 */
@Data
public class AiSliceTextSearchDTO {

	private Long sliceId;

	private Long documentId;

	private String documentName;

	private String content;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Double score;

}
