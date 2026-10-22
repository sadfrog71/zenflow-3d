package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_item")
public class InformationItem {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long sourcePolicyId;
	private String contentType;
	private String title;
	private String sourceName;
	private String sourceUrl;
	private String canonicalUrl;
	private String summary;
	private String content;
	private String tags;
	private LocalDateTime publishedAt;
	private LocalDateTime collectedAt;
	private BigDecimal aiConfidence;
	private String reviewStatus;
	private String reviewReason;
	private String publishStatus;
	private String contentHash;
	@TableField(fill = FieldFill.INSERT)
	private String createBy;
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;
	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;
	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;
	@TableLogic
	private String delFlag;
}
