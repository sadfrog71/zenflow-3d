package com.deepdimen.matrix.admin.mapper;

import com.deepdimen.matrix.admin.api.entity.SysMessageEntity;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageSnapshot;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysMessageMapper extends MatrixBaseMapper<SysMessageEntity> {

	@Select("""
			SELECT m.id, m.title, LEFT(COALESCE(NULLIF(r.content, ''), m.content), 1000) AS content,
			       m.source_type, m.event_type, m.priority, m.target_path,
			       m.action_required, m.create_time AS occurred_at
			FROM sys_message_relation r
			JOIN sys_message m ON m.id = r.msg_id AND m.tenant_id = r.tenant_id
			WHERE r.tenant_id = #{tenantId} AND m.tenant_id = #{tenantId}
			  AND r.user_id = #{userId} AND r.read_flag = '0'
			  AND r.del_flag = '0' AND m.del_flag = '0' AND m.send_flag = '1'
			  AND (m.org_id IS NULL OR m.org_id = 0 OR m.org_id = #{orgId})
			  AND (m.expire_time IS NULL OR m.expire_time > CURRENT_TIMESTAMP)
			  AND (#{from} IS NULL OR m.create_time >= #{from})
			ORDER BY m.sort DESC, m.create_time DESC
			LIMIT #{limit}
			""")
	List<BusinessMessageSnapshot> selectUnreadForDigest(@Param("tenantId") Long tenantId,
			@Param("userId") Long userId, @Param("orgId") Long orgId,
			@Param("from") LocalDateTime from, @Param("limit") int limit);

}
