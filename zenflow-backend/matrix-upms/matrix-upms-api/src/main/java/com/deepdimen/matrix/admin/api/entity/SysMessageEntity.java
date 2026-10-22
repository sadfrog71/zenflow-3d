package com.deepdimen.matrix.admin.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内信息
 *
 * @author matrix
 * @date 2023-10-25 13:37:25
 */
@Data
@TenantTable
@TableName("sys_message")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "站内信息")
public class SysMessageEntity extends Model<SysMessageEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 分类
	 */
	@Schema(description = "分类 0-公告 1-站内信")
	private String category;

	/**
	 * 标题
	 */
	@Schema(description = "标题")
	private String title;

	/**
	 * 内容
	 */
	@Schema(description = "内容")
	private String content;

	/**
	 * 是否推送
	 */
	@Schema(description = "是否推送")
	private String sendFlag;

	/**
	 * 通知全体
	 */
	@Schema(description = "通知全体")
	private String allFlag;

	/**
	 * 排序 （越大越在前）
	 */
	@Schema(description = "排序 （越大越在前）")
	private Integer sort;

	/**
	 * Business idempotency key. Manual messages keep this field null.
	 */
	@Schema(description = "业务幂等键")
	private String bizKey;

	@Schema(description = "业务来源类型")
	private String sourceType;

	@Schema(description = "业务来源ID")
	private String sourceId;

	@Schema(description = "业务事件类型")
	private String eventType;

	@Schema(description = "消息优先级")
	private String priority;

	@Schema(description = "是否需要业务处理")
	private Integer actionRequired;

	@Schema(description = "来源组织ID")
	private Long orgId;

	@Schema(description = "前台内部目标路径")
	private String targetPath;

	@Schema(description = "前台目标查询参数JSON")
	private String targetQuery;

	@Schema(description = "消息失效时间")
	private LocalDateTime expireTime;

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
	 * 更新人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新人")
	private String updateBy;

	/**
	 * 更新时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	/**
	 * 删除时间
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除时间")
	private String delFlag;

	/**
	 * 租户
	 */
	@Schema(description = "租户")
	private Long tenantId;

}
