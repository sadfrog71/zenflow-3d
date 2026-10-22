package com.deepdimen.matrix.work.api.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WorkBriefVO {

	private Long id;

	private Long runId;

	private String executionStatus;

	private Long skillRunId;

	private LocalDate reportDate;

	private String title;

	private String executiveSummary;

	private String continuityNote;

	private Map<String, Integer> metrics;

	private List<WorkBriefItemVO> focusItems;

	private List<WorkBriefItemVO> scheduleItems;

	private List<WorkBriefItemVO> informationItems;

	private List<WorkBriefItemVO> messageItems;

	private List<WorkBriefItemVO> emailItems;

	private List<WorkBriefItemVO> riskItems;

	private List<WorkBriefItemVO> citations;

	private String reviewerStatus;

	private String reviewerReason;

	private String skillCode;

	private String skillVersion;

	private LocalDateTime createTime;

}
