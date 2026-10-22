package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_assignment")
public class WorkTaskAssignmentEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long taskId;

	private Long userId;

	private String role;

	private String assignmentStatus;

	private LocalDateTime acceptedAt;

	private LocalDateTime completedAt;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

	private Long tenantId;

}
