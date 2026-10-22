package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.service.AiSkillService;
import com.deepdimen.matrix.discover.vo.AiSkillVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能目录Service实现
 *
 * @author matrix
 * @date 2026-04-25
 */
@Slf4j
@Service
public class AiSkillServiceImpl extends ServiceImpl<AiSkillMapper, AiSkillEntity> implements AiSkillService {

	private static final String SYSTEM_TENANT_ID = "0";

	@Override
	public List<AiSkillVO> listAvailableSkills() {
		String currentTenantId = String.valueOf(TenantContextHolder.getTenantId());
		List<AiSkillEntity> entities = list(Wrappers.<AiSkillEntity>lambdaQuery()
			.in(AiSkillEntity::getTenantId, Arrays.asList(SYSTEM_TENANT_ID, currentTenantId))
			.eq(AiSkillEntity::getIsEnabled, 1)
			.orderByAsc(AiSkillEntity::getSort));
		return entities.stream().map(this::toVO).collect(Collectors.toList());
	}

	@Override
	public List<AiSkillEntity> findEnabledByCodes(List<String> skillCodes) {
		if (skillCodes == null || skillCodes.isEmpty()) {
			return List.of();
		}
		String currentTenantId = String.valueOf(TenantContextHolder.getTenantId());
		return list(Wrappers.<AiSkillEntity>lambdaQuery()
			.in(AiSkillEntity::getSkillCode, skillCodes)
			.in(AiSkillEntity::getTenantId, Arrays.asList(SYSTEM_TENANT_ID, currentTenantId))
			.eq(AiSkillEntity::getIsEnabled, 1));
	}

	private AiSkillVO toVO(AiSkillEntity entity) {
		AiSkillVO vo = new AiSkillVO();
		BeanUtils.copyProperties(entity, vo);
		return vo;
	}

}
