package com.deepdimen.matrix.work.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InformationOrganizationMapper {
	@Select("SELECT COUNT(*) FROM sys_dept WHERE dept_id = #{orgId} AND del_flag = '0'")
	int countActiveOrganization(@Param("orgId") Long orgId);
}
