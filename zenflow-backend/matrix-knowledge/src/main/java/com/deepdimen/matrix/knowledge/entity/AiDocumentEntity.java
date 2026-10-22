package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.common.data.handler.JsonStringArrayTypeHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * 知识文档
 *
 * @author matrix
 * @date 2024-03-14 13:38:59
 */
@Data
@TableName("ai_document")
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识文档")
@TenantTable
public class AiDocumentEntity extends Model<AiDocumentEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 知识库ID
	 */
	@Schema(description = "知识库ID")
	private Long datasetId;

	/** 普通元数据、标签、异步解析更新不得覆盖分类；仅分类服务显式更新。 */
	@TableField(updateStrategy = FieldStrategy.NEVER)
	private Long categoryId;

	@TableField(exist = false)
	private Boolean uncategorized;

	@com.fasterxml.jackson.annotation.JsonIgnore
	@TableField(exist = false)
	private java.util.List<Long> categoryIds;

	/**
	 * 名称
	 */
	@Schema(description = "名称")
	private String name;

	/**
	 * 文件类型
	 */
	@Schema(description = "文件类型")
	private String fileType;

	/**
	 * 文件链接
	 */
	@Schema(description = "文件链接")
	private String fileUrl;

	/**
	 * 文件hash
	 */
	private String fileHash;

	/**
	 * 文件来源
	 */
	@Schema(description = "文件来源")
	private String sourceType;

	/**
	 * 切片数量
	 */
	@Schema(description = "切片数量")
	private Long sliceCount = 0L;

	/**
	 * 命中次数
	 */
	@Schema(description = "命中次数")
	private Long hitCount = 0L;

	/**
	 * 文件大小
	 */
	@Schema(description = "文件大小")
	private Long fileSize;

	/**
	 * 切片状态 0 未切片 1 已切片 9 失败
	 */
	@Schema(description = "切片状态")
	private String sliceStatus;

	/**
	 * 切片失败原因
	 */
	@Schema(description = "失败原因")
	private String sliceFailReason;

	/**
	 * 状态 ("0" “1" "2" ”“9" 分别表示：未处理、已处理、处理中、处理失败)
	 */
	@Schema(description = "状态")
	private String fileStatus;

	/**
	 * 图谱状态
	 */
	@Schema(description = "图谱状态")
	private String graphStatus;

	/**
	 * 文档总结
	 */
	@Schema(description = "文档总结")
	private String summary;

	/**
	 * 总结状态
	 */
	@Schema(description = "状态")
	private String summaryStatus;

	/**
	 * 摘要失败原因
	 */
	@Schema(description = "失败原因")
	private String summaryFailReason;

	/**
	 * 文档配置
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Schema(description = "文档配置")
	private String documentConfig;

	/**
	 * 文档标签
	 */
	@Schema(description = "文档标签")
	@TableField(typeHandler = JsonStringArrayTypeHandler.class)
	private String[] tags;

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
	 * 成功切片数量
	 */
	@TableField(exist = false)
	@Schema(description = "成功切片数量")
	private Long successSliceCount = 0L;

	/**
	 * 所属知识库信息（用于前端条件判断）
	 */
	@TableField(exist = false)
	@Schema(description = "所属知识库信息")
	private AiDatasetEntity dataset;

}
