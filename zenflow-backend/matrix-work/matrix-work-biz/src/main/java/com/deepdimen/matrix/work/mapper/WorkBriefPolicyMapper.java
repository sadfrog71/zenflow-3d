package com.deepdimen.matrix.work.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;

@Mapper
public interface WorkBriefPolicyMapper extends BaseMapper<WorkBriefPolicyEntity> {

	@org.apache.ibatis.annotations.Update("UPDATE ai_work_brief_policy SET skill_management_mode='MANAGED' WHERE tenant_id=#{tenant} AND id=#{id} AND del_flag='0'")
	int markManaged(@org.apache.ibatis.annotations.Param("tenant") Long tenant,
			@org.apache.ibatis.annotations.Param("id") Long id);

}
