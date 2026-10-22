package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;
import java.time.LocalDateTime;

/**
 * 智能体技能关联VO
 *
 * @author matrix
 * @date 2026-04-25
 */
@Data
@Schema(description = "智能体技能关联视图")
public class AiAgentSkillVO {

	@Schema(description = "技能ID")
	private Long skillId;

	@Schema(description = "技能标识")
	private String skillCode;

	@Schema(description = "技能名称")
	private String skillName;

	@Schema(description = "技能描述")
	private String description;

	@Schema(description = "图标URL或emoji")
	private String icon;

	@Schema(description = "实现类型")
	private String implType;

	@Schema(description = "管理员配置的默认开关")
	private Integer isDefaultEnabled;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "技能实例配置，如chat-db的{dataId,dataName}")
	private Map<String, Object> skillConfig;

	@Schema(description = "执行策略")
	private String executionStrategy;

	@Schema(description = "专用Dify Workflow对应的平台智能体ID")
	private Long workflowAgentId;

	@Schema(description = "MATRIX规范输入到Dify变量的映射")
	private Map<String, String> inputMapping;

	@Schema(description = "Dify输出到MATRIX规范输出的映射")
	private Map<String, String> outputMapping;

	@Schema(description = "结果模式")
	private String resultMode;

	@Schema(description = "超时毫秒数")
	private Integer timeoutMs;

	@Schema(description = "重试策略")
	private String retryPolicy;

	@Schema(description = "副作用分类")
	private String sideEffectClass;

	@Schema(description = "Skill发布版本")
	private String releaseVersion;

	@Schema(description = "Skill发布投影ID")
	private Long skillReleaseId;

	@Schema(description = "发布状态: ACTIVE/GRAY/DISABLED")
	private String releaseStatus;

	@Schema(description = "灰度规则JSON")
	private Map<String, Object> grayConfig;

	@Schema(description = "已发布运行时类型")
	private String runtimeKind;

	@Schema(description = "已冻结的 Prompt Skill 指令")
	private String promptSnapshot;

	@Schema(description = "指令内容 SHA-256")
	private String instructionDigest;

	@Schema(description = "Skill 包 SHA-256")
	private String packageSha256;

	@Schema(description = "Skill 必需 Provider 列表")
	private java.util.List<String> requiredProviders;

	@Schema(description = "已冻结的分析策略")
	private Map<String, Object> analysisPolicySnapshot;

	@Schema(description = "分析策略 SHA-256")
	private String analysisPolicyDigest;

	@Schema(description = "已冻结的 AnalysisResult JSON Schema")
	private Map<String, Object> analysisResultSchema;

	@Schema(description = "AnalysisResult Schema 内容标识")
	private String analysisResultSchemaId;

	@Schema(description = "发布时间")
	private LocalDateTime releasedAt;

	@Schema(description = "停用时间")
	private LocalDateTime disabledAt;

}
