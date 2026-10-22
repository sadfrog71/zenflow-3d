package com.deepdimen.matrix.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InformationSkillInvocationMapper extends BaseMapper<InformationSkillInvocationEntity> {

	@Select("SELECT * FROM ai_information_skill_invocation WHERE tenant_id=#{tenant} AND collection_run_id=#{run} AND document_digest=#{document} AND slot_code=#{slot}")
	InformationSkillInvocationEntity findStage(@Param("tenant") Long tenant, @Param("run") Long run,
			@Param("document") String document, @Param("slot") String slot);

	@Select("SELECT * FROM ai_information_skill_invocation WHERE tenant_id=#{tenant} AND collection_run_id=#{run} AND id=#{id}")
	InformationSkillInvocationEntity findOwned(@Param("tenant") Long tenant, @Param("run") Long run,
			@Param("id") Long id);

	@Update("""
			UPDATE ai_information_skill_invocation SET skill_run_id=#{skillRun},skill_output_json=#{output},status='RAW_RESULT'
			WHERE tenant_id=#{tenant} AND collection_run_id=#{run} AND id=#{id} AND actor_id=#{actor}
			AND status='PREPARED' AND skill_run_id IS NULL AND skill_output_json IS NULL
			""")
	int saveOnce(@Param("tenant") Long tenant, @Param("run") Long run, @Param("id") Long id,
			@Param("actor") String actor, @Param("skillRun") Long skillRun, @Param("output") String output);

}
