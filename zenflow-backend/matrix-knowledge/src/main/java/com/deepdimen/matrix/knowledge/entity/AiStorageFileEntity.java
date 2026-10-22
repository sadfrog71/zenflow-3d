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
 * AI存储文件表
 *
 * @author matrix
 * @date 2025-09-30 13:56:22
 */
@Data
@TenantTable
@FieldNameConstants
@TableName("ai_storage_file")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI存储文件表")
public class AiStorageFileEntity extends Model<AiStorageFileEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 文件名称
	 */
	@Schema(description = "文件名称")
	private String fileName;

	/**
	 * 文件类型(image/video/document/audio)
	 */
	@Schema(description = "文件类型(image/video/document/audio)")
	private String fileType;

	/**
	 * 文件大小(字节)
	 */
	@Schema(description = "文件大小(字节)")
	private Long fileSize;

	/**
	 * 文件URL
	 */
	@Schema(description = "文件URL")
	private String fileUrl;

	/**
	 * 缩略图URL
	 */
	@Schema(description = "缩略图URL")
	private String thumbnailUrl;

	/**
	 * 所属分组ID
	 */
	@Schema(description = "所属分组ID")
	private Long groupId;

	/**
	 * 存储路径
	 */
	@Schema(description = "存储路径")
	private String storagePath;

	/**
	 * 文件MD5值
	 */
	@Schema(description = "文件MD5值")
	private String md5;

	/**
	 * 宽度(图片/视频)
	 */
	@Schema(description = "宽度(图片/视频)")
	private Integer width;

	/**
	 * 高度(图片/视频)
	 */
	@Schema(description = "高度(图片/视频)")
	private Integer height;

	/**
	 * 时长(视频/音频,秒)
	 */
	@Schema(description = "时长(视频/音频,秒)")
	private Integer duration;

	/**
	 * 扩展信息(EXIF等)
	 */
	@Schema(description = "扩展信息(EXIF等)")
	private String extInfo;

	/**
	 * AI识别标签
	 */
	@Schema(description = "AI识别标签")
	private String aiTags;

	/**
	 * AI识别内容
	 */
	@Schema(description = "AI识别内容")
	private String aiContent;

	/**
	 * AI是否已分析(false-否,true-是)
	 */
	@Schema(description = "AI是否已分析(false-否,true-是)")
	private String aiAnalyzed;

	/**
	 * 查看次数
	 */
	@Schema(description = "查看次数")
	private Integer viewCount;

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
	 * 更新时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记(0-正常,1-删除)
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记(0-正常,1-删除)")
	private String delFlag;

	/**
	 * 租户ID
	 */
	@Schema(description = "租户ID")
	private Long tenantId;

}
