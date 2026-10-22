package com.deepdimen.matrix.admin.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * API Token表
 *
 * @author matrix
 * @date 2025-03-12
 */
@Data
@TableName("ai_api_token")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "API Token表")
@TenantTable
public class AiApiTokenEntity extends Model<AiApiTokenEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 租户ID
	 */
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * Token名称
	 */
	@Schema(description = "Token名称")
	private String tokenName;

	/**
	 * Token值(UUID v4)
	 */
	@Schema(description = "Token值(UUID v4)")
	private String tokenValue;

	/**
	 * 状态: active/disabled
	 */
	@Schema(description = "状态: active/disabled")
	private String status;

	/**
	 * 过期时间，NULL表示永不过期
	 */
	@Schema(description = "过期时间，NULL表示永不过期")
	private LocalDateTime expireTime;

	/**
	 * 最后使用时间
	 */
	@Schema(description = "最后使用时间")
	private LocalDateTime lastUsedTime;

	/**
	 * 创建人
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 修改人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	/**
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标志
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标志")
	private String delFlag;

}
