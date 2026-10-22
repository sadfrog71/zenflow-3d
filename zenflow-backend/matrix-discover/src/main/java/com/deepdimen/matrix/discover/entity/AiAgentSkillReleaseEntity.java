package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 已发布的智能体 Skill 绑定投影。运行时只读取此表，避免草稿修改立即影响线上对话。
 */
@TableName("ai_agent_skill_release")
@TenantTable
@Data
public class AiAgentSkillReleaseEntity extends AiAgentSkillEntity {

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

	private LocalDateTime releasedAt;

	private LocalDateTime disabledAt;

}
