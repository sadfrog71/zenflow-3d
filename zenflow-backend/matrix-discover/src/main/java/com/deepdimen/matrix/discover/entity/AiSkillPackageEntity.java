package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 导入的 Agent Skills 包。原始 ZIP 只用于审计和重新导出，运行时不直接执行归档内容。
 */
@Data
@TableName("ai_skill_package")
@EqualsAndHashCode(callSuper = true)
public class AiSkillPackageEntity extends Model<AiSkillPackageEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private String tenantId;

	/** LEGACY packages alone may feed the old agent publication path. */
	private String sourceChannel;

	private Long skillId;

	private String packageVersion;

	private String originalFilename;

	private String packageSha256;

	private Long packageSize;

	private String manifestJson;

	private String validationReport;

	private String packageStatus;

	/** 原始归档不在列表查询中加载，避免后台分页把 ZIP 全部读入内存。 */
	@TableField(select = false)
	private byte[] archiveData;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
