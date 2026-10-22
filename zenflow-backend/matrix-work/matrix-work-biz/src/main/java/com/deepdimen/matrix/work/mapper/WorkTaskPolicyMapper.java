package com.deepdimen.matrix.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkTaskPolicyMapper extends BaseMapper<WorkTaskPolicyEntity> {

	@org.apache.ibatis.annotations.Update("""
			UPDATE ai_work_task_policy SET parse_skill_management_mode='MANAGED', version=version+1
			WHERE tenant_id=#{tenant} AND id=#{id} AND version=#{version} AND enabled=1
			AND chat_model_id=#{model} AND parse_skill_package_id=#{sourcePackage}
			AND parse_skill_management_mode IN ('LEGACY','MANAGED')
			""")
	int markParseManaged(@org.apache.ibatis.annotations.Param("tenant") Long tenant,
			@org.apache.ibatis.annotations.Param("id") Long id,
			@org.apache.ibatis.annotations.Param("version") Integer version,
			@org.apache.ibatis.annotations.Param("model") Long model,
			@org.apache.ibatis.annotations.Param("sourcePackage") Long sourcePackage);

	@org.apache.ibatis.annotations.Update("""
			UPDATE ai_work_task_policy SET dispatch_skill_management_mode='MANAGED', version=version+1
			WHERE tenant_id=#{tenant} AND id=#{id} AND version=#{version} AND enabled=1
			AND chat_model_id=#{model} AND dispatch_skill_package_id=#{sourcePackage}
			AND dispatch_skill_management_mode IN ('LEGACY','MANAGED')
			""")
	int markDispatchManaged(@org.apache.ibatis.annotations.Param("tenant") Long tenant,
			@org.apache.ibatis.annotations.Param("id") Long id,
			@org.apache.ibatis.annotations.Param("version") Integer version,
			@org.apache.ibatis.annotations.Param("model") Long model,
			@org.apache.ibatis.annotations.Param("sourcePackage") Long sourcePackage);

}
