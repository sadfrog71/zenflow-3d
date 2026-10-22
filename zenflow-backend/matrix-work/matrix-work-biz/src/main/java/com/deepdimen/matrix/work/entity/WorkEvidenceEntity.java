package com.deepdimen.matrix.work.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;

import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_evidence")
public class WorkEvidenceEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long sourceId;
	private Long orgId;
	private Long ownerUserId;
	private String evidenceType;
	private String externalIdHash;
	private String title;
	private String excerpt;
	private String senderDisplay;
	private String senderHash;
	private LocalDateTime occurredAt;
	private String contentHash;
	private String metadataJson;
	private LocalDateTime capturedAt;
	private LocalDateTime createTime;
	private Long tenantId;

}
