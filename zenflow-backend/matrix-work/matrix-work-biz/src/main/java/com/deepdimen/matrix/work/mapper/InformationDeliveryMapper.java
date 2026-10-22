package com.deepdimen.matrix.work.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.work.api.entity.InformationDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface InformationDeliveryMapper extends MatrixBaseMapper<InformationDelivery> {

	@Select("SELECT COUNT(*) FROM ai_information_delivery WHERE org_id = #{orgId} AND delivery_date = #{date} AND status = 'PUBLISHED'")
	int countPublishedOnDate(@Param("orgId") Long orgId, @Param("date") LocalDate date);

	@Select("""
			SELECT COUNT(*) FROM ai_information_delivery d
			JOIN ai_information_item i ON i.id = d.item_id
			WHERE d.org_id = #{orgId} AND d.delivery_date = #{date} AND d.status = 'PUBLISHED'
			  AND i.source_policy_id = #{policyId}
			""")
	int countPolicyPublishedOnDate(@Param("orgId") Long orgId, @Param("policyId") Long policyId,
			@Param("date") LocalDate date);
}
