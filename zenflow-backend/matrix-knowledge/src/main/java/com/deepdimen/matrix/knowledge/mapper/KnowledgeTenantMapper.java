package com.deepdimen.matrix.knowledge.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * Supplies the active tenant boundary for background knowledge tasks.
 */
@Mapper
public interface KnowledgeTenantMapper {

	@Select("SELECT id FROM sys_tenant WHERE del_flag = '0' AND (status IS NULL OR status = '0') ORDER BY id")
	List<Long> selectActiveTenantIds();

}
