package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_submission")
public class WorkTaskSubmissionEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long taskId;

	private Integer submissionNo;

	private String contentMarkdown;

	private Long submittedBy;

	private LocalDateTime submittedAt;

	private Long acceptorAtSubmit;

	private String decision;

	private Long decidedBy;

	private LocalDateTime decidedAt;

	private String decisionComment;

}
