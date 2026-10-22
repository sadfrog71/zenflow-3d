package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.AiUserSkillPrefEntity;
import com.deepdimen.matrix.discover.mapper.AiUserSkillPrefMapper;
import com.deepdimen.matrix.discover.service.AiUserSkillPrefService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户技能偏好Service实现
 *
 * @author matrix
 * @date 2026-04-27
 */
@Slf4j
@Service
public class AiUserSkillPrefServiceImpl extends ServiceImpl<AiUserSkillPrefMapper, AiUserSkillPrefEntity>
		implements AiUserSkillPrefService {

	@Override
	public List<Map<String, Object>> getByUserAndAgent(Long agentId) {
		Long userId = SecurityUtils.getUser().getId();
		List<AiUserSkillPrefEntity> entities = list(Wrappers.<AiUserSkillPrefEntity>lambdaQuery()
			.eq(AiUserSkillPrefEntity::getUserId, userId)
			.eq(AiUserSkillPrefEntity::getAgentId, agentId));
		List<Map<String, Object>> result = new ArrayList<>();
		for (AiUserSkillPrefEntity e : entities) {
			Map<String, Object> item = new HashMap<>();
			item.put("skillId", e.getSkillId());
			item.put("isEnabled", e.getIsEnabled());
			result.add(item);
		}
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveUserPrefs(Long agentId, List<Map<String, Object>> prefs) {
		Long userId = SecurityUtils.getUser().getId();
		baseMapper.delete(Wrappers.<AiUserSkillPrefEntity>lambdaQuery()
			.eq(AiUserSkillPrefEntity::getUserId, userId)
			.eq(AiUserSkillPrefEntity::getAgentId, agentId));
		if (prefs == null || prefs.isEmpty()) {
			return;
		}
		List<AiUserSkillPrefEntity> entities = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now();
		for (Map<String, Object> pref : prefs) {
			Object skillIdObj = pref.get("skillId");
			Object isEnabledObj = pref.get("isEnabled");
			if (skillIdObj == null) {
				continue;
			}
			AiUserSkillPrefEntity entity = new AiUserSkillPrefEntity();
			entity.setUserId(userId);
			entity.setAgentId(agentId);
			entity.setSkillId(Long.valueOf(skillIdObj.toString()));
			entity.setIsEnabled(isEnabledObj != null ? Integer.valueOf(isEnabledObj.toString()) : 1);
			entity.setUpdateTime(now);
			entities.add(entity);
		}
		if (!entities.isEmpty()) {
			saveBatch(entities);
		}
	}

}
