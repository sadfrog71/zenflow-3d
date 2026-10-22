package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.vo.AiSkillVO;

import java.util.List;

/**
 * 技能目录Service
 *
 * @author matrix
 * @date 2026-04-25
 */
public interface AiSkillService extends IService<AiSkillEntity> {

	/**
	 * 获取当前租户可见的技能列表（内置 tenant_id=0 + 当前租户自定义）
	 */
	List<AiSkillVO> listAvailableSkills();

	/**
	 * 根据 skillCode 列表查询已启用的技能（SkillProcessor 使用）
	 */
	List<AiSkillEntity> findEnabledByCodes(List<String> skillCodes);

}
