package com.deepdimen.matrix.discover.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 智能体技能关联请求DTO
 *
 * @author matrix
 * @date 2026-04-25
 */
@Data
@Schema(description = "智能体技能关联请求")
public class AiAgentSkillDTO {

	@NotNull(message = "技能ID不能为空")
	@Schema(description = "技能ID")
	private Long skillId;

	@Schema(description = "管理员配置的默认开关: 1开启 0关闭，默认1")
	private Integer isDefaultEnabled;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "技能实例配置，如chat-db的{dataId,dataName}")
	private Map<String, Object> skillConfig;

	@Schema(description = "执行策略: PRIMARY_APP_INSTRUCTION/DEDICATED_DIFY_WORKFLOW")
	private String executionStrategy;

	@Schema(description = "专用Dify Workflow对应的平台智能体ID")
	private Long workflowAgentId;

	@Schema(description = "MATRIX规范输入到Dify变量的映射")
	private Map<String, String> inputMapping;

	@Schema(description = "Dify输出到MATRIX规范输出的映射")
	private Map<String, String> outputMapping;

	@Schema(description = "结果模式: FINAL_RESPONSE/INTERMEDIATE_RESULT")
	private String resultMode;

	@Schema(description = "超时毫秒数")
	private Integer timeoutMs;

	@Schema(description = "重试策略: NONE/SAFE_ONCE")
	private String retryPolicy;

	@Schema(description = "副作用分类: READ_ONLY/PROPOSAL_ONLY")
	private String sideEffectClass;

}
