package com.deepdimen.matrix.discover.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiSkillPackageMapper extends BaseMapper<AiSkillPackageEntity> {

	@Select("""
			select id, tenant_id, skill_id, package_version, original_filename, package_sha256,
			       package_size, package_status, archive_data
			from ai_skill_package
			where id = #{id} and tenant_id in ('0', #{tenantId}) and source_channel = 'LEGACY'
			limit 1
			""")
	AiSkillPackageEntity selectArchiveById(@Param("id") Long id, @Param("tenantId") String tenantId);

	@Select("""
			SELECT * FROM ai_skill_package
			WHERE id = #{id} AND tenant_id = #{tenantId} AND source_channel = 'MANAGED'
			FOR UPDATE
			""")
	AiSkillPackageEntity lockManagedPackage(@Param("id") Long id, @Param("tenantId") String tenantId);

	@org.apache.ibatis.annotations.Update("""
			UPDATE ai_skill_package SET package_status = 'APPROVED', validation_report = #{report},
			    update_by = #{actor}, update_time = CURRENT_TIMESTAMP(3)
			WHERE id = #{id} AND tenant_id = #{tenantId} AND source_channel = 'MANAGED'
			    AND package_status = 'PENDING_REVIEW' AND package_sha256 = #{sha256}
			""")
	int approveManaged(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("sha256") String sha256,
			@Param("report") String report, @Param("actor") String actor);

	@org.apache.ibatis.annotations.Select("SELECT * FROM ai_skill_package WHERE id=#{id} AND tenant_id=#{tenant} AND source_channel='MANAGED'")
	AiSkillPackageEntity findManagedArchive(@org.apache.ibatis.annotations.Param("id") Long id,
			@org.apache.ibatis.annotations.Param("tenant") String tenant);

}
