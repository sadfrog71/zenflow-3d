package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_evidence")
public class InformationEvidence {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long itemId;
	private Long runId;
	private String sourceUrl;
	private String title;
	private String rawContent;
	private String contentHash;
	private LocalDateTime capturedAt;
	private LocalDateTime createTime;
}
