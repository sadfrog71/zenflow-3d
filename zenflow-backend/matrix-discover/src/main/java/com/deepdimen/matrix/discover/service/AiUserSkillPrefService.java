package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.discover.entity.AiUserSkillPrefEntity;

import java.util.List;
import java.util.Map;

/**
 * 用户技能偏好Service
 *
 * @author matrix
 * @date 2026-04-27
 */
public interface AiUserSkillPrefService extends IService<AiUserSkillPrefEntity> {

	/**
	 * 查询当前用户对指定智能体的技能偏好
	 * @return skillId -> isEnabled 的映射列表
	 */
	List<Map<String, Object>> getByUserAndAgent(Long agentId);

	/**
	 * 保存当前用户对指定智能体的技能偏好（全量替换）
	 */
	void saveUserPrefs(Long agentId, List<Map<String, Object>> prefs);

}
