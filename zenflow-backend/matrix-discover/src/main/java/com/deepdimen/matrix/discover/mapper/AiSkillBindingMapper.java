package com.deepdimen.matrix.discover.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.discover.entity.AiSkillBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Binding revision creation and this pointer swap must share one transaction. */
@Mapper
public interface AiSkillBindingMapper extends BaseMapper<AiSkillBindingEntity> {

	@Update("""
			UPDATE ai_skill_binding b
			SET b.active_revision_id = #{revisionId}, b.revision = b.revision + 1,
			    b.updated_by = #{actorId}, b.updated_at = CURRENT_TIMESTAMP(3)
			WHERE b.id = #{bindingId} AND b.tenant_id = #{tenantId} AND b.revision = #{expectedRevision}
			  AND EXISTS (SELECT 1 FROM ai_skill_binding_revision r
			              WHERE r.id = #{revisionId} AND r.binding_id = b.id AND r.tenant_id = b.tenant_id)
			""")
	int activateRevision(@Param("tenantId") String tenantId, @Param("bindingId") Long bindingId,
			@Param("revisionId") Long revisionId, @Param("expectedRevision") Long expectedRevision,
			@Param("actorId") String actorId);

	@org.apache.ibatis.annotations.Select("SELECT * FROM ai_skill_binding WHERE id=#{id} AND tenant_id=#{tenant} FOR UPDATE")
	AiSkillBindingEntity lockOwned(@Param("tenant") String tenant, @Param("id") Long id);

}
