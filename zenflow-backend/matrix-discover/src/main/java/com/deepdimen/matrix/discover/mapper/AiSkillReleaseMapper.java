package com.deepdimen.matrix.discover.mapper;

import com.deepdimen.matrix.discover.entity.AiSkillReleaseEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Published content is append-only: deliberately exposes no generic update or delete. */
@Mapper
public interface AiSkillReleaseMapper {

	@Insert("""
			INSERT INTO ai_skill_release
			  (id, tenant_id, skill_id, package_id, version, runtime_kind, content_snapshot,
			   input_schema, output_schema, executor_ref, resource_refs, limits_json,
			   allowed_parameters, package_sha256, release_digest, status, created_by, created_at)
			VALUES
			  (#{release.id}, #{release.tenantId}, #{release.skillId}, #{release.packageId},
			   #{release.version}, #{release.runtimeKind}, #{release.contentSnapshot},
			   #{release.inputSchema}, #{release.outputSchema}, #{release.executorRef},
			   #{release.resourceRefs}, #{release.limitsJson}, #{release.allowedParameters},
			   #{release.packageSha256}, #{release.releaseDigest}, 'PUBLISHED',
			   #{release.createdBy}, CURRENT_TIMESTAMP(3))
			""")
	int insertRelease(@Param("release") AiSkillReleaseEntity release);

	/**
	 * Caller must separately authorize system releases; never infer access from tenant 0.
	 */
	@Select("""
			SELECT * FROM ai_skill_release WHERE id = #{id} AND tenant_id = #{ownerTenantId}
			""")
	AiSkillReleaseEntity findOwned(@Param("ownerTenantId") String ownerTenantId, @Param("id") Long id);

	@Select("""
			SELECT * FROM ai_skill_release
			WHERE tenant_id=#{tenant} AND skill_id=#{skill}
			  AND (#{beforeId} IS NULL OR id < #{beforeId})
			ORDER BY id DESC LIMIT 50
			""")
	java.util.List<AiSkillReleaseEntity> listOwned(@Param("tenant") String tenant, @Param("skill") Long skill,
			@Param("beforeId") Long beforeId);

	@Update("""
			UPDATE ai_skill_release
			SET status = 'REVOKED', revoked_by = #{actorId}, revoked_at = CURRENT_TIMESTAMP(3),
			    revocation_reason = #{reason}
			WHERE id = #{id} AND tenant_id = #{tenantId} AND tenant_id <> '0'
			  AND status = 'PUBLISHED' AND release_digest = #{expectedDigest}
			""")
	int revoke(@Param("tenantId") String tenantId, @Param("id") Long id, @Param("expectedDigest") String expectedDigest,
			@Param("actorId") String actorId, @Param("reason") String reason);

}
