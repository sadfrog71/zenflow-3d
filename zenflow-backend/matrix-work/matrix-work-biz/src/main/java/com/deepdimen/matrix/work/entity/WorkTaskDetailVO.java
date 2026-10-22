package com.deepdimen.matrix.work.entity;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkTaskDetailVO extends WorkTaskEntity {

	private Long acceptorId;

	private OffsetDateTime dueAtOffset;

	private Integer contractVersion;

	private List<String> allowedActions;

	private List<String> riskFlags;

	private List<WorkTaskEntity> children;

	private WorkTaskTransferEntity pendingTransfer;

	private WorkTaskSubmissionEntity currentSubmission;

	private OffsetDateTime serverTime;

	private String enterpriseTimeZone;

}
