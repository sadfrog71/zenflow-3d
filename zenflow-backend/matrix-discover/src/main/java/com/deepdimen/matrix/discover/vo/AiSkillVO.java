package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 技能列表VO
 *
 * @author matrix
 * @date 2026-04-25
 */
@Data
@Schema(description = "技能列表视图")
public class AiSkillVO {

	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "技能标识")
	private String skillCode;

	@Schema(description = "技能名称")
	private String skillName;

	@Schema(description = "技能描述")
	private String description;

	@Schema(description = "图标URL或emoji")
	private String icon;

	@Schema(description = "实现类型: prompt_injection/dify_plugin/matrix_plugin")
	private String implType;

	@Schema(description = "是否可用")
	private Integer isEnabled;

	@Schema(description = "排序权重")
	private Integer sort;

}
