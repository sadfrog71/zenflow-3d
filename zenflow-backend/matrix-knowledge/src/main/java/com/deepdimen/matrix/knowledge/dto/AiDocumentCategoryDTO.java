package com.deepdimen.matrix.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiDocumentCategoryDTO {

	private Long id;

	@NotNull
	@Positive
	private Long datasetId;

	@NotNull
	@PositiveOrZero
	private Long parentId = 0L;

	@NotBlank
	@Size(max = 100)
	private String name;

	@NotNull
	@PositiveOrZero
	private Integer sort = 0;

}
