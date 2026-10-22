package com.deepdimen.matrix.work.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InformationPolicyMapper extends MatrixBaseMapper<InformationPolicy> {

	@org.apache.ibatis.annotations.Update("""
			UPDATE ai_information_policy SET skill_management_mode='MANAGED',skill_policy_version=skill_policy_version+1
			WHERE tenant_id=#{tenant} AND id=#{id} AND skill_policy_version=#{version}
			AND enabled=1 AND model_name=#{model} AND del_flag='0'
			AND skill_management_mode IN ('LEGACY','MANAGED')
			""")
	int markManaged(@org.apache.ibatis.annotations.Param("tenant") Long tenant,
			@org.apache.ibatis.annotations.Param("id") Long id,
			@org.apache.ibatis.annotations.Param("version") Integer version,
			@org.apache.ibatis.annotations.Param("model") String model);

}
