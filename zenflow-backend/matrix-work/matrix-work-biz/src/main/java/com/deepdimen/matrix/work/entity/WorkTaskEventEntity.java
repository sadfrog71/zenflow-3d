package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_event")
public class WorkTaskEventEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long taskId;

	private String type;

	private String actorType;

	private Long actorId;

	private Long actorOrgId;

	private Integer taskVersion;

	private LocalDateTime occurredAt;

	private String reason;

	private String payloadJson;

	private String requestKey;

	private String requestHash;

	private Integer eventSeq;

	private String resultJson;

	private String publishStatus;

	private Integer attempts;

	private LocalDateTime nextAttemptAt;

	private String leaseOwner;

	private LocalDateTime leaseUntil;

	private String lastError;

	private String messageRefs;

}
