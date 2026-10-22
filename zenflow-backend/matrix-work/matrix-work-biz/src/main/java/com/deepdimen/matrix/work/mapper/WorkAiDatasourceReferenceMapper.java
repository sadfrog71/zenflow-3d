package com.deepdimen.matrix.work.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkAiDatasourceReferenceMapper {

	@Select("""
			SELECT COUNT(1)
			FROM ai_datasource
			WHERE tenant_id = #{tenantId}
			  AND id = #{datasourceId}
			  AND del_flag = '0'
			""")
	int countVisible(@Param("tenantId") Long tenantId, @Param("datasourceId") Long datasourceId);

}
