package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI数据集管理表 用于管理结构化数据的AI学习配置，支持数据库表的智能问答
 *
 * @author matrix
 * @date 2025-03-26 21:47:45
 */
@Data
@TableName("ai_data")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI数据集管理表")
@TenantTable
public class AiDataEntity extends Model<AiDataEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long dataId;

	/**
	 * 数据集名称 标识这个数据集的名称
	 */
	@Schema(description = "数据集名称")
	private String datasetName;

	/**
	 * 数据集描述 详细描述数据集的用途和内容
	 */
	@Schema(description = "数据集描述")
	private String description;

	/**
	 * 数据集类型 1-明细表：标准的业务数据表 2-多指标周期表：包含时间维度的指标数据 3-键值对表：简单的配置类数据 4-其他：特殊结构的数据
	 */
	@Schema(description = "数据集类型 (1-明细表, 2-多指标周期表, 3-键值对表, 4-其他)")
	private String datasetType;

	/**
	 * 关联数据源名称 数据库连接的标识名称
	 */
	@Schema(description = "关联数据源名称")
	private String dsName;

	/**
	 * 学习状态 标识AI是否已学习该数据集 0-未学习：尚未进行模型训练 1-已学习：已完成模型训练，可以进行智能问答
	 */
	@Schema(description = "学习状态（0-未学习, 1-已学习）")
	private String learningStatus;

	/**
	 * 关联数据表 该数据集包含的数据库表名列表
	 */
	@Schema(description = "关联数据表")
	private String[] tableName;

	/** 关联术语ID数组 */
	@Schema(description = "关联术语ID")
	private Long[] terminologyIds;

	@TableField(exist = false)
	@Schema(description = "编辑时读取的术语绑定快照；不入库")
	private java.util.List<Long> expectedTerminologyIds;

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
	 * 删除标记 (0-正常, 1-删除)
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记 (0-正常, 1-删除)")
	private String delFlag;

}
