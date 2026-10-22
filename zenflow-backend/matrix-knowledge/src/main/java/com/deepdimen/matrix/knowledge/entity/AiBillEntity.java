package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.common.sensitive.annotation.Sensitive;
import com.deepdimen.matrix.common.sensitive.core.SensitiveTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * AI使用账单记录表 用于记录用户使用AI服务产生的令牌消耗和费用明细
 *
 * @author matrix
 * @date 2024-03-26 11:26:59
 */
@Data
@TableName("ai_bill")
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI使用账单记录表")
@TenantTable
public class AiBillEntity extends Model<AiBillEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 用户ID 使用AI服务的用户标识
	 */
	@Schema(description = "用户ID")
	private Long userId;

	/**
	 * 消息键值 关联的消息唯一标识
	 */
	@Schema(description = "消息messageKey")
	private Long messageKey;

	/**
	 * 用户名 非数据库字段，仅用于展示
	 */
	@TableField(exist = false)
	private String username;

	/**
	 * 提示令牌数量 用户输入消耗的token数
	 */
	@Schema(description = "提示令牌数量")
	private Long promptTokens;

	/**
	 * 补全令牌数量 AI生成回复消耗的token数
	 */
	@Schema(description = "补全令牌数量")
	private Long completionTokens;

	/**
	 * 模型名称 使用的AI模型标识（如：gpt-4、claude-3等）
	 */
	@Schema(description = "模型名称")
	private String model;

	/**
	 * 请求ID API调用的唯一请求标识
	 */
	@Schema(description = "请求ID")
	private String reqid;

	/**
	 * IP地址 用户发起请求的IP地址（脱敏处理）
	 */
	@Schema(description = "IP地址")
	@Sensitive(type = SensitiveTypeEnum.IPV4)
	private String ip;

	/**
	 * 备注
	 */
	@Schema(description = "备注")
	private String note;

	/**
	 * 令牌ID 关联的令牌配额标识
	 */
	@Schema(description = "令牌ID")
	private Long tokenId;

	/**
	 * 令牌数量 总消耗的token数量
	 */
	@Schema(description = "令牌数量")
	private Long tokens;

	/**
	 * 令牌类型 0-系统配额 1-用户配额
	 */
	@Schema(description = "令牌类型 0 系统  1 用户")
	private String tokenType;

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
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

	/**
	 * 使用时间（毫秒）
	 */
	@Schema(description = "使用时间，单位毫秒")
	private Long useTime;

	/**
	 * 租户ID
	 */
	@Schema(description = "租户ID")
	private String tenantId;

}
