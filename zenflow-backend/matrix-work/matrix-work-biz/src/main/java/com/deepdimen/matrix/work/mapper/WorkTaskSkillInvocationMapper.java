package com.deepdimen.matrix.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.work.entity.WorkTaskSkillInvocationEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WorkTaskSkillInvocationMapper extends BaseMapper<WorkTaskSkillInvocationEntity> {

	@Select("""
			SELECT * FROM ai_work_task_skill_invocation
			WHERE tenant_id=#{tenant} AND owner_user_id=#{owner} AND policy_id=#{policy} AND id=#{businessRun} AND slot_code=#{slot}
			""")
	WorkTaskSkillInvocationEntity findOwned(@Param("tenant") Long tenant, @Param("owner") Long owner,
			@Param("policy") Long policy, @Param("businessRun") Long businessRun, @Param("slot") String slot);

	@Select("SELECT * FROM ai_work_task_skill_invocation WHERE tenant_id=#{tenant} AND dispatch_run_id=#{dispatchRun} AND slot_code='TASK_DISPATCH'")
	WorkTaskSkillInvocationEntity findDispatch(@Param("tenant") Long tenant, @Param("dispatchRun") Long dispatchRun);

	@Update("""
			UPDATE ai_work_task_skill_invocation SET skill_run_id=#{skillRun},skill_output_json=#{output},status='RAW_RESULT'
			WHERE tenant_id=#{tenant} AND owner_user_id=#{owner} AND policy_id=#{policy} AND id=#{businessRun} AND slot_code=#{slot}
			AND status='PREPARED' AND skill_run_id IS NULL AND skill_output_json IS NULL
			""")
	int saveOnce(@Param("tenant") Long tenant, @Param("owner") Long owner, @Param("policy") Long policy,
			@Param("businessRun") Long businessRun, @Param("slot") String slot, @Param("skillRun") Long skillRun,
			@Param("output") String output);

}
