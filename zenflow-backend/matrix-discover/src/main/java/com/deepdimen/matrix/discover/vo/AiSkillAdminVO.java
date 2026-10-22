package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** Skill 管理端列表视图，不返回原始包内容。 */
@Data
@Schema(description = "Skill 管理端视图")
public class AiSkillAdminVO {

	private Long id;

	private String tenantId;

	private String skillCode;

	private String skillName;

	private String description;

	private String icon;

	private String skillType;

	private String implType;

	private Integer isEnabled;

	private Integer sort;

	private String packageStatus;

	private Long packageId;

	private String packageVersion;

	private String packageSha256;

	private String originalFilename;

	private Long packageSize;

	private String validationReport;

	private LocalDateTime updateTime;

}
