package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提示词
 *
 * @author matrix
 * @date 2024-03-20 16:29:05
 */
@Data
@TableName("ai_prompt")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "提示词")
@TenantTable
public class AiPromptEntity extends Model<AiPromptEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 标题
	 */
	@Schema(description = "标题")
	private String act;

	/**
	 * 提示词
	 */
	@Schema(description = "提示词")
	private String prompt;

	/**
	 * 列表排序
	 */
	@Schema(description = "列表排序")
	private Long promptSort;

	/**
	 * 状态
	 */
	@Schema(description = "状态")
	private String promptStatus;

	/**
	 * 提示词类型: personal/enterprise
	 */
	@Schema(description = "提示词类型: personal-个人/enterprise-企业")
	private String promptType;

	/**
	 * 提示词分类(字典prompt_category)
	 */
	@Schema(description = "提示词分类: 字典prompt_category，默认uncategorized")
	private String promptCategory;

	/**
	 * 使用次数
	 */
	@Schema(description = "使用次数")
	private Integer useCount;

	/**
	 * 所属人
	 */
	@Schema(description = "所属人")
	private Long userId;

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

}
