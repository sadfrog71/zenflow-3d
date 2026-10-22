package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_transfer")
public class WorkTaskTransferEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long taskId;

	private Long fromUserId;

	private Long toUserId;

	private String reason;

	private String status;

	private Integer version;

	private LocalDateTime requestedAt;

	private LocalDateTime respondedAt;

	private String responseReason;

}
