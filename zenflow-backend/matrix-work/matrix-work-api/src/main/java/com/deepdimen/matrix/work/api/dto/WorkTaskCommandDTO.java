package com.deepdimen.matrix.work.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class WorkTaskCommandDTO {

	@NotNull
	@PositiveOrZero
	private Integer expectedVersion;

	@Size(max = 30000)
	private String content;

	@Size(max = 2000)
	private String comment;

	@Size(max = 2000)
	private String reason;

	@Size(max = 200)
	private String title;

	private String priority;

	private OffsetDateTime dueAt;

	private Long acceptorId;

	private Long primaryAssigneeId;

	private Long toUserId;

	private Long submissionId;

	private Integer transferVersion;

	private String decision;

	private String mode;

	private List<Long> fileIds;

}
