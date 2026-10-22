package com.deepdimen.matrix.discover.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 技能目录Mapper
 *
 * @author matrix
 * @date 2026-04-25
 */
@Mapper
public interface AiSkillMapper extends BaseMapper<AiSkillEntity> {

	@Select("SELECT * FROM ai_skill WHERE tenant_id = #{tenantId} AND skill_code = #{skillCode} LIMIT 1")
	AiSkillEntity selectIncludingDeleted(@Param("tenantId") String tenantId, @Param("skillCode") String skillCode);

	@Update("""
			UPDATE ai_skill
			SET skill_name = #{skill.skillName}, description = #{skill.description},
				impl_type = #{skill.implType}, config = #{skill.config}, skill_type = 'custom',
				is_enabled = 0, sort = #{skill.sort}, update_by = #{skill.updateBy},
				update_time = #{skill.updateTime}, del_flag = '0'
			WHERE id = #{skill.id} AND tenant_id = #{skill.tenantId} AND del_flag <> '0'
			""")
	int restoreDeleted(@Param("skill") AiSkillEntity skill);

}
