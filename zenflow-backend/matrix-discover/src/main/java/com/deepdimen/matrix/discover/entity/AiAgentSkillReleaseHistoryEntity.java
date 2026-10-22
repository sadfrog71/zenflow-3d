package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill 发布投影的历史快照。历史记录只用于回滚与审计，不参与运行时目录。
 */
@Data
@TableName("ai_agent_skill_release_history")
@TenantTable
public class AiAgentSkillReleaseHistoryEntity extends Model<AiAgentSkillReleaseHistoryEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private String tenantId;

	private Long agentId;

	private Long skillId;

	private String releaseVersion;

	private String releaseStatus;

	private String grayConfig;

	private String runtimeKind;

	private String promptSnapshot;

	private String instructionDigest;

	private String packageSha256;

	private String requiredProvidersJson;

	private String analysisPolicySnapshot;

	private String analysisPolicyDigest;

	private String analysisResultSchema;

	private String analysisResultSchemaId;

	private Integer isDefaultEnabled;

	private String skillConfig;

	private String executionStrategy;

	private Long workflowAgentId;

	private String inputMapping;

	private String outputMapping;

	private String resultMode;

	private Integer timeoutMs;

	private String retryPolicy;

	private String sideEffectClass;

	private Integer sort;

	private String createBy;

	private LocalDateTime createTime;

}
