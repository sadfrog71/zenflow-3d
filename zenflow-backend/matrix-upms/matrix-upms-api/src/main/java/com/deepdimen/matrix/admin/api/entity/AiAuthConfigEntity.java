package com.deepdimen.matrix.admin.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 认证配置表
 *
 * @author matrix
 * @date 2026-03-12
 */
@Data
@TableName("ai_auth_config")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "认证配置表")
@TenantTable
public class AiAuthConfigEntity extends Model<AiAuthConfigEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 认证类型(wechat_work)
	 */
	@Schema(description = "认证类型")
	private String authType;

	/**
	 * 企业ID
	 */
	@Schema(description = "企业ID")
	private String corpId;

	/**
	 * 应用AgentId
	 */
	@Schema(description = "应用AgentId")
	private String agentId;

	/**
	 * Secret(SM4加密存储)
	 */
	@Schema(description = "Secret(SM4加密存储)")
	private String secretEncrypted;

	/**
	 * 回调地址
	 */
	@Schema(description = "回调地址")
	private String callbackUrl;

	/**
	 * 租户ID
	 */
	@Schema(description = "租户ID")
	private Long tenantId;

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
