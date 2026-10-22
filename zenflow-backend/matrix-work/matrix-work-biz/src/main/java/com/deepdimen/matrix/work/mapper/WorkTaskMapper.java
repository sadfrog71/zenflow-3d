package com.deepdimen.matrix.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkTaskMapper extends BaseMapper<WorkTaskEntity> {

	@Select("SELECT * FROM ai_work_task WHERE tenant_id=#{tenantId} AND id=#{id} AND del_flag='0' FOR UPDATE")
	WorkTaskEntity lock(@Param("tenantId") Long tenantId, @Param("id") Long id);

}
