package com.deepdimen.matrix.discover.mapper;

import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.*;

/** Read-only projection. Never returns instructions, parameters or execution tokens. */
@Mapper
public interface MarketSkillCatalogMapper {

	@Select("""
			<script>
			SELECT CAST(b.id AS CHAR) AS binding_id, b.binding_key,
			       CAST(r.id AS CHAR) AS binding_revision_id, CAST(s.id AS CHAR) AS release_id,
			       CAST(s.skill_id AS CHAR) AS skill_id, k.skill_name, s.version, s.runtime_kind
			FROM ai_skill_binding b
			JOIN ai_skill_binding_revision r ON r.id=b.active_revision_id
			    AND r.binding_id=b.id AND r.tenant_id=b.tenant_id
			JOIN ai_skill_release s ON s.id=r.release_id AND s.tenant_id=b.tenant_id
			JOIN ai_skill k ON k.id=s.skill_id AND k.tenant_id=b.tenant_id
			WHERE b.tenant_id=#{tenant} AND b.consumer_type='MARKET_AGENT'
			  AND b.consumer_id=#{agent} AND b.slot_code='CHAT' AND k.del_flag='0'
			  AND r.enabled=1 AND r.activation_mode='EXPLICIT' AND s.status='PUBLISHED'
			  AND JSON_LENGTH(r.audience_json)=1
			  AND JSON_UNQUOTE(JSON_EXTRACT(r.audience_json,'$.scope'))='AGENT'
			  AND JSON_LENGTH(r.fallback_policy)=1
			  AND JSON_UNQUOTE(JSON_EXTRACT(r.fallback_policy,'$.mode'))='NONE'
			<if test="before != null">AND b.id &lt; #{before}</if>
			ORDER BY b.id DESC LIMIT 51
			</script>
			""")
	List<BoundSkill> list(@Param("tenant") String tenant, @Param("agent") String agent, @Param("before") Long before);

	@Data
	class BoundSkill {

		private String bindingId;

		private String bindingKey;

		private String bindingRevisionId;

		private String releaseId;

		private String skillId;

		private String skillName;

		private String version;

		private String runtimeKind;

	}

}
