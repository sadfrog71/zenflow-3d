package com.deepdimen.matrix.work.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.work.api.dto.InformationPageQuery;
import com.deepdimen.matrix.work.api.entity.InformationItem;
import com.deepdimen.matrix.work.api.vo.InformationCardVO;
import com.deepdimen.matrix.work.api.vo.InformationDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InformationItemMapper extends MatrixBaseMapper<InformationItem> {

	@Select("""
			<script>
			SELECT i.id, i.content_type, i.title, i.source_name, i.source_url, i.summary, i.tags,
			       i.published_at, d.published_at AS delivered_at,
			       IFNULL(s.read_flag, 0) AS `read`, IFNULL(s.favorite_flag, 0) AS favorite
			FROM ai_information_delivery d
			JOIN ai_information_item i ON i.id = d.item_id
			LEFT JOIN ai_information_user_state s
			  ON s.item_id = i.id AND s.tenant_id = d.tenant_id AND s.org_id = d.org_id AND s.user_id = #{userId}
			WHERE d.tenant_id = #{tenantId} AND i.tenant_id = #{tenantId}
			  AND d.org_id = #{orgId}
			  AND d.delivery_date = CURRENT_DATE
			  AND d.status = 'PUBLISHED'
			  AND i.publish_status = 'PUBLISHED'
			  AND i.del_flag = '0'
			  AND IFNULL(s.ignored_flag, 0) = 0
			<if test="query.contentType != null and query.contentType != ''">
			  AND i.content_type = #{query.contentType}
			</if>
			<if test="query.keyword != null and query.keyword != ''">
			  AND (i.title LIKE CONCAT('%', #{query.keyword}, '%')
			       OR i.summary LIKE CONCAT('%', #{query.keyword}, '%'))
			</if>
			<if test="query.favorite != null and query.favorite">
			  AND IFNULL(s.favorite_flag, 0) = 1
			</if>
			<if test="query.unread != null and query.unread">
			  AND IFNULL(s.read_flag, 0) = 0
			</if>
			ORDER BY d.delivery_date DESC, d.sort_order ASC, d.published_at DESC
			</script>
			""")
	IPage<InformationCardVO> selectPortalPage(Page<InformationCardVO> page, @Param("tenantId") Long tenantId,
			@Param("orgId") Long orgId,
			@Param("userId") Long userId, @Param("query") InformationPageQuery query);

	@Select("""
			SELECT i.id, i.content_type, i.title, i.source_name, i.source_url, i.summary, i.content, i.tags,
			       i.published_at, d.published_at AS delivered_at,
			       IFNULL(s.read_flag, 0) AS `read`, IFNULL(s.favorite_flag, 0) AS favorite
			FROM ai_information_delivery d
			JOIN ai_information_item i ON i.id = d.item_id
			LEFT JOIN ai_information_user_state s
			  ON s.item_id = i.id AND s.tenant_id = d.tenant_id AND s.org_id = d.org_id AND s.user_id = #{userId}
			WHERE d.tenant_id = #{tenantId} AND i.tenant_id = #{tenantId}
			  AND d.item_id = #{itemId} AND d.org_id = #{orgId}
			  AND d.status = 'PUBLISHED' AND i.publish_status = 'PUBLISHED' AND i.del_flag = '0'
			LIMIT 1
			""")
	InformationDetailVO selectPortalDetail(@Param("itemId") Long itemId, @Param("tenantId") Long tenantId,
			@Param("orgId") Long orgId,
			@Param("userId") Long userId);

	@Select("""
			<script>
			SELECT i.id, i.content_type, i.title, i.source_name, i.source_url, i.summary, i.content, i.tags,
			       i.published_at, d.published_at AS delivered_at,
			       IFNULL(s.read_flag, 0) AS `read`, IFNULL(s.favorite_flag, 0) AS favorite
			FROM ai_information_delivery d
			JOIN ai_information_item i ON i.id = d.item_id
			LEFT JOIN ai_information_user_state s
			  ON s.item_id = i.id AND s.org_id = d.org_id AND s.user_id = #{userId}
			 AND s.tenant_id = #{tenantId}
			WHERE d.tenant_id = #{tenantId} AND i.tenant_id = #{tenantId}
			  AND d.org_id = #{orgId} AND d.status = 'PUBLISHED'
			  AND i.publish_status = 'PUBLISHED' AND i.del_flag = '0' AND IFNULL(s.ignored_flag, 0) = 0
			<if test="from != null">AND i.published_at &gt;= #{from}</if>
			<if test="to != null">AND i.published_at &lt;= #{to}</if>
			<if test="keyword != null and keyword != ''">
			  AND (i.title LIKE CONCAT('%', #{keyword}, '%') OR i.summary LIKE CONCAT('%', #{keyword}, '%')
			       OR i.content LIKE CONCAT('%', #{keyword}, '%'))
			</if>
			<if test="itemIds != null and itemIds.size() > 0">
			  AND i.id IN
			  <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">#{itemId}</foreach>
			</if>
			ORDER BY i.published_at DESC, d.published_at DESC
			LIMIT #{limit}
			</script>
			""")
	List<InformationDetailVO> selectContextItems(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId,
			@Param("userId") Long userId,
			@Param("keyword") String keyword, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("itemIds") List<Long> itemIds, @Param("limit") int limit);

	@Select("""
			SELECT COUNT(*) AS total,
			       IFNULL(SUM(CASE WHEN IFNULL(s.read_flag, 0) = 0 THEN 1 ELSE 0 END), 0) AS unread,
			       IFNULL(SUM(CASE WHEN IFNULL(s.favorite_flag, 0) = 1 THEN 1 ELSE 0 END), 0) AS favorite
			FROM ai_information_delivery d
			JOIN ai_information_item i ON i.id = d.item_id
			LEFT JOIN ai_information_user_state s
			  ON s.item_id = i.id AND s.tenant_id = d.tenant_id AND s.org_id = d.org_id AND s.user_id = #{userId}
			WHERE d.tenant_id = #{tenantId} AND i.tenant_id = #{tenantId}
			  AND d.org_id = #{orgId} AND d.delivery_date = CURRENT_DATE
			  AND d.status = 'PUBLISHED' AND i.publish_status = 'PUBLISHED' AND i.del_flag = '0'
			  AND IFNULL(s.ignored_flag, 0) = 0
			""")
	com.deepdimen.matrix.work.api.vo.InformationStatsVO selectPortalStats(@Param("tenantId") Long tenantId,
			@Param("orgId") Long orgId,
			@Param("userId") Long userId);
}
