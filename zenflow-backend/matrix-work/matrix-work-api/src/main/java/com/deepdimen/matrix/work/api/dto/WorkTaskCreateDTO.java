package com.deepdimen.matrix.work.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class WorkTaskCreateDTO {

	@NotBlank(message = "任务标题不能为空")
	private String title;

	private String description;

	private String priority;

	@NotNull(message = "负责人不能为空")
	private Long ownerId;

	@Size(max = 100, message = "任务执行人不能超过100人")
	private List<Long> assigneeIds;

	private LocalDateTime dueAt;

	private String sourceType;

	private String sourceRefId;

}
