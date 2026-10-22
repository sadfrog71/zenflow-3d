package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * 知识切片
 *
 * @author matrix
 * @date 2024-03-14 13:39:40
 */
@Data
@TableName("ai_slice")
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识切片")
@TenantTable
public class AiSliceEntity extends Model<AiSliceEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 文档ID
	 */
	@Schema(description = "文档ID")
	private Long documentId;

	/**
	 * 保存向量ID
	 */
	@Schema(description = "向量ID")
	private String qdrantId;

	/**
	 * 名称
	 */
	@Schema(description = "名称")
	private String name;

	/**
	 * 大小
	 */
	@Schema(description = "大小")
	private Long fileSize;

	/**
	 * 命中次数
	 */
	@Schema(description = "命中次数")
	private Long hitCount = 0L;

	/**
	 * 字符数量
	 */
	@Schema(description = "字符数量")
	private Long charCount = 0L;

	/**
	 * 内容
	 */
	@Schema(description = "内容")
	private String content;

	/**
	 * 训练状态
	 */
	@Schema(description = "训练状态")
	private String sliceStatus;

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
