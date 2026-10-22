package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Dify 账单实体，映射 ai_bill 表（仅 Dify 透传场景使用）
 */
@Data
@TableName("ai_bill")
@TenantTable
@EqualsAndHashCode(callSuper = true)
public class DifyBillEntity extends Model<DifyBillEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long messageKey;

	private Long promptTokens;

	private Long completionTokens;

	private Long tokens;

	private String model;

	private String ip;

	private String tokenType;

	private Long useTime;

	private String tenantId;

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

}
