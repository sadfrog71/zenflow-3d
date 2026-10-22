package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.discover.dto.AiAgentSkillDTO;
import com.deepdimen.matrix.discover.dto.AiAgentVersionGrayDTO;
import com.deepdimen.matrix.discover.entity.AiAgentSkillEntity;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;

import java.util.List;
import java.util.Map;

/**
 * 智能体技能关联Service
 *
 * @author matrix
 * @date 2026-04-25
 */
public interface AiAgentSkillService extends IService<AiAgentSkillEntity> {

	/**
	 * 获取智能体绑定的技能列表（含技能详情）
	 */
	List<AiAgentSkillVO> getByAgentId(Long agentId);

	/**
	 * 读取已发布的 Skill 绑定，仅供线上运行时使用。
	 */
	List<AiAgentSkillVO> getPublishedByAgentId(Long agentId);

	/**
	 * 全量替换智能体技能关联（先物理删除再批量插入）
	 */
	void saveAgentSkills(Long agentId, List<AiAgentSkillDTO> skills);

	/**
	 * 发布前校验专用 Workflow、输入输出映射和只读边界，不产生远端变更。
	 */
	void validateForPublish(Long agentId);

	/**
	 * 发布阶段把需要的 Artifact、Tool 等绑定投影到 Dify。失败必须中断发布。
	 */
	void projectForPublish(Long agentId);

	/**
	 * 将技能库生命周期同步到默认助手的草稿和发布投影。
	 */
	void syncDefaultAgentSkill(Long skillId, boolean enabled);

	/**
	 * 将当前租户全部已启用 Skill 同步到指定渠道智能体的草稿和发布投影。
	 */
	void syncEnabledSkillsToAgent(Long agentId);

	/**
	 * 移除默认助手对技能的草稿和发布绑定。
	 */
	void removeDefaultAgentSkill(Long skillId);

	/**
	 * 判断技能是否仍被非默认助手绑定。
	 */
	boolean hasNonDefaultBindings(Long skillId);

	/** 查询当前租户的 Skill 发布投影（含版本、灰度与停用状态）。 */
	List<AiAgentSkillVO> listReleaseGovernance(Long agentId);

	/** 配置某个 Skill 发布版本的灰度规则。 */
	void setReleaseGray(Long agentId, String releaseVersion, AiAgentVersionGrayDTO dto);

	/** 停用某个 Skill 发布版本，并返回影响摘要。 */
	Map<String, Object> disableRelease(Long agentId, String releaseVersion);

	/** 从历史快照回滚某个 Skill 发布版本。 */
	void rollbackRelease(Long agentId, String releaseVersion);

}
