package com.deepdimen.matrix.admin.api.feign;

import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import com.deepdimen.matrix.admin.api.vo.AgentShareValidationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 智能体服务 Feign 接口
 *
 * @author matrix
 * @date 2026-06-29
 */
@FeignClient(contextId = "remoteAgentService", value = ServiceNameConstants.DISCOVER_SERVICE)
public interface RemoteAgentService {

	/**
	 * 按智能体ID查询所属租户ID（供 share 免密登录定位租户使用）
	 * @param agentId 智能体ID
	 * @return 租户ID，智能体不存在时返回 null
	 */
	@NoToken
	@GetMapping("/aiAgent/tenant-id")
	R<String> getTenantIdByAgentId(@RequestParam("agentId") Long agentId);

	@NoToken
	@GetMapping("/aiAgent/share-validation")
	R<AgentShareValidationVO> validateShareAgent(@RequestParam("agentId") Long agentId);

}
