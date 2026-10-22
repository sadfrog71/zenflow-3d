package com.deepdimen.matrix.admin.api.feign;

import com.deepdimen.matrix.admin.api.entity.AiAuthConfigEntity;
import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 认证配置 Feign 接口
 *
 * @author matrix
 * @date 2026-03-12
 */
@FeignClient(contextId = "remoteAuthConfigService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteAuthConfigService {

	/**
	 * 根据租户ID和认证类型查询认证配置
	 * @param tenantId 租户ID
	 * @param authType 认证类型
	 * @return 认证配置
	 */
	@NoToken
	@GetMapping("/aiAuthConfig/getByTenantAndType")
	R<AiAuthConfigEntity> getByTenantAndType(@RequestParam("tenantId") Long tenantId,
			@RequestParam("authType") String authType);

}
