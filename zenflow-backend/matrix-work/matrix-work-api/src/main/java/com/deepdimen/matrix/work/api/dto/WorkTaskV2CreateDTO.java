package com.deepdimen.matrix.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WorkTaskV2CreateDTO {

	@NotBlank
	@Size(max = 200)
	private String title;

	@NotBlank
	@Size(max = 30000)
	private String content;

	private String priority = "NORMAL";

	@NotNull
	private OffsetDateTime dueAt;

	private String dispatchMode = "MANUAL";

	private Long primaryAssigneeId;

	private Long acceptorId;

	private Map<String, String> source;

	private List<Long> fileIds;

}
