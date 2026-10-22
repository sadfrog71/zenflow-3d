package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task")
@Schema(description = "Pig AI 内部任务")
public class WorkTaskEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private String title;

	private String description;

	private String priority;

	private WorkTaskStatus status;

	private Long creatorId;

	private Long ownerId;

	private Long organizationId;

	private Long primaryAssigneeId;

	private Long parentId;

	private Integer taskContractVersion;

	private String contentFormat;

	private Integer contentVersion;

	private String dispatchMode;

	private String dispatchStatus;

	private Long currentDispatchRunId;

	@TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
	private Long pendingTransferId;

	private Long currentSubmissionId;

	private LocalDateTime submittedAt;

	private LocalDateTime closedAt;

	private String dueTimezone;

	@TableField(exist = false)
	private List<Long> assigneeIds;

	private LocalDateTime dueAt;

	private String sourceType;

	private String sourceRefId;

	@Version
	private Integer version;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

	private Long tenantId;

}
