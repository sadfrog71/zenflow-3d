package com.deepdimen.matrix.work.api.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WorkBriefItemVO {

	private String refType;

	private Long refId;

	private String title;

	private String detail;

	private String priority;

	private LocalDateTime occurredAt;
}
