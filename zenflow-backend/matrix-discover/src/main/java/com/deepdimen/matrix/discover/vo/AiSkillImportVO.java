package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Skill 导入结果")
public class AiSkillImportVO {

	private Long skillId;

	private Long packageId;

	private String skillCode;

	private String skillName;

	private String packageVersion;

	private String packageStatus;

	private String validationReport;

}
