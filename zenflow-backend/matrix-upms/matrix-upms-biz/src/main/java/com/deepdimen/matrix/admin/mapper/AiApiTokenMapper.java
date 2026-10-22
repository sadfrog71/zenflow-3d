package com.deepdimen.matrix.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.deepdimen.matrix.admin.api.entity.AiApiTokenEntity;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * API Token Mapper
 *
 * @author matrix
 * @date 2025-03-12
 */
@Mapper
public interface AiApiTokenMapper extends MatrixBaseMapper<AiApiTokenEntity> {

	/**
	 * 统计租户下活跃 Token 数量
	 * @param tenantId 租户ID
	 * @return 活跃 Token 数量
	 */
	long countActiveByTenantId(@Param("tenantId") String tenantId);

	/**
	 * 根据 Token 值查询有效 Token（status=active 且未过期） 忽略租户拦截器，因为 Filter 调用时租户上下文尚未设置
	 * @param tokenValue Token值
	 * @return Token实体，不存在或无效返回 null
	 */
	@InterceptorIgnore(tenantLine = "true")
	AiApiTokenEntity findValidByTokenValue(@Param("tokenValue") String tokenValue);

	@InterceptorIgnore(tenantLine = "true")
	AiApiTokenEntity findValidById(@Param("id") Long id);

	/**
	 * 查询租户下第一个有效的 API Token（status=active 且未过期） 忽略租户拦截器，直接按 tenantId 查询
	 * @param tenantId 租户ID
	 * @return Token实体，不存在返回 null
	 */
	@InterceptorIgnore(tenantLine = "true")
	AiApiTokenEntity findFirstActiveByTenantId(@Param("tenantId") String tenantId);

}
