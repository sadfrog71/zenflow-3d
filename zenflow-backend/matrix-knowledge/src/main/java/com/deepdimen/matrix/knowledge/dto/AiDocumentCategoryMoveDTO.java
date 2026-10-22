package com.deepdimen.matrix.knowledge.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiDocumentCategoryMoveDTO {

	@NotNull
	@Positive
	private Long datasetId;

	@Positive
	private Long categoryId;

	@NotNull
	@Size(min = 1, max = 500)
	private List<@NotNull @Positive Long> documentIds;

}
