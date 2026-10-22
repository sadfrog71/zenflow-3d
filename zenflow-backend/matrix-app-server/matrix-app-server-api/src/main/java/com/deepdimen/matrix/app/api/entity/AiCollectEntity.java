package com.deepdimen.matrix.app.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通用收藏表
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@TenantTable
@TableName("ai_collect")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通用收藏表")
public class AiCollectEntity extends Model<AiCollectEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 租户ID（由 MatrixTenantHandler 插件自动注入，禁止手动赋值）
	 */
	@Schema(description = "租户ID")
	private Long tenantId;

	/**
	 * 用户ID
	 */
	@Schema(description = "用户ID")
	private Long userId;

	/**
	 * 收藏类型（agent/article）
	 */
	@Schema(description = "收藏类型")
	private String collectType;

	/**
	 * 目标ID
	 */
	@Schema(description = "目标ID")
	private Long targetId;

	/**
	 * 创建时间
	 */
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
