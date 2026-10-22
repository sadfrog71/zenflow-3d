package com.deepdimen.matrix.work.mapper;

import com.deepdimen.matrix.work.entity.WorkBriefRunEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WorkBriefSkillResultMapper {

	@Update("""
			UPDATE ai_work_analysis_run SET skill_run_id=#{skillRun},skill_output_json=#{output}
			WHERE tenant_id=#{tenant} AND owner_user_id=#{owner} AND policy_id=#{policy} AND id=#{businessRun}
			  AND status='RUNNING' AND skill_run_id IS NULL AND skill_output_json IS NULL
			""")
	int saveOnce(@Param("tenant") Long tenant, @Param("owner") Long owner, @Param("policy") Long policy,
			@Param("businessRun") Long businessRun, @Param("skillRun") Long skillRun, @Param("output") String output);

	@Select("""
			SELECT * FROM ai_work_analysis_run
			WHERE tenant_id=#{tenant} AND owner_user_id=#{owner} AND policy_id=#{policy} AND id=#{businessRun}
			""")
	WorkBriefRunEntity findOwned(@Param("tenant") Long tenant, @Param("owner") Long owner, @Param("policy") Long policy,
			@Param("businessRun") Long businessRun);

}
