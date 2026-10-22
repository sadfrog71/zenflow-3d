package com.deepdimen.matrix.work.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class WorkTaskChildrenDTO {

	@NotNull
	private Integer expectedVersion;

	@NotNull
	@Size(min = 1, max = 30)
	private List<@Valid WorkTaskV2CreateDTO> children;

}
