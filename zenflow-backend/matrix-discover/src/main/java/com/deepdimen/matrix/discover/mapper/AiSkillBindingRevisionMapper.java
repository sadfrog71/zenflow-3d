package com.deepdimen.matrix.discover.mapper;

import com.deepdimen.matrix.discover.entity.AiSkillBindingRevisionEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiSkillBindingRevisionMapper {

	@Insert("""
			INSERT INTO ai_skill_binding_revision
			(id,tenant_id,binding_id,release_id,enabled,audience_json,activation_mode,parameter_json,
			 fallback_policy,revision,created_by,created_at)
			VALUES (#{r.id},#{r.tenantId},#{r.bindingId},#{r.releaseId},#{r.enabled},#{r.audienceJson},
			        #{r.activationMode},#{r.parameterJson},#{r.fallbackPolicy},#{r.revision},#{r.createdBy},CURRENT_TIMESTAMP(3))
			""")
	int insertRevision(@Param("r") AiSkillBindingRevisionEntity revision);

	@Select("SELECT * FROM ai_skill_binding_revision WHERE tenant_id=#{tenant} AND binding_id=#{binding} AND id=#{id}")
	AiSkillBindingRevisionEntity find(@Param("tenant") String tenant, @Param("binding") Long binding,
			@Param("id") Long id);

	/** Called while holding the parent binding row lock. */
	@Select("SELECT COALESCE(MAX(revision),0)+1 FROM ai_skill_binding_revision WHERE tenant_id=#{tenant} AND binding_id=#{binding}")
	long nextRevision(@Param("tenant") String tenant, @Param("binding") Long binding);

	@Select("""
			SELECT * FROM ai_skill_binding_revision WHERE tenant_id=#{tenant} AND binding_id=#{binding}
			  AND (#{beforeId} IS NULL OR id < #{beforeId}) ORDER BY id DESC LIMIT 50
			""")
	java.util.List<AiSkillBindingRevisionEntity> listOwned(@Param("tenant") String tenant,
			@Param("binding") Long binding, @Param("beforeId") Long beforeId);

}
