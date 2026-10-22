package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Reads current configuration only. This does not publish, pin or authorize execution.
 */
@Component
@RequiredArgsConstructor
public class DifyMarketConfigurationReader {

	private final AiAgentService agents;

	private final ObjectProvider<DifyAppApi> apis;

	private final ObjectMapper json;

	public DifyChatConfigurationSnapshot read(Long id) {
		Long tenant = TenantContextHolder.getTenantId();
		if (tenant == null || tenant <= 0 || id == null || id <= 0 || AiAgentService.DEFAULT_AGENT_ID.equals(id))
			throw denied();
		var detail = agents.getAgentDetail(id);
		if (detail == null || !id.equals(detail.getId()) || !Boolean.TRUE.equals(detail.getAccessible()))
			throw denied();
		var agent = agents.getById(id);
		if (agent == null || !id.equals(agent.getId()) || !tenant.toString().equals(agent.getTenantId()))
			throw denied();
		if (!"dify".equals(agent.getAgentType()) || !"chat".equals(agent.getDifyMode())
				|| !"published".equals(agent.getStatus()) || agent.getDifyAppId() == null
				|| agent.getDifyAppId().isBlank())
			throw new IllegalArgumentException("DIFY_CHAT_CONFIGURATION_UNVERIFIED");
		var api = apis.getIfAvailable();
		if (api == null)
			throw new IllegalStateException("AGENT_ADAPTER_UNAVAILABLE");
		var snapshot = DifyChatConfigurationSnapshot.capture(agent.getDifyAppId(), api.getApp(agent.getDifyAppId()),
				json);
		// Remote inspection may take time; reject a concurrent local repoint or
		// permission revocation.
		var latest = agents.getById(id);
		var access = agents.getAgentDetail(id);
		if (latest == null || access == null || !id.equals(access.getId())
				|| !Boolean.TRUE.equals(access.getAccessible()) || !tenant.toString().equals(latest.getTenantId())
				|| !"published".equals(latest.getStatus()) || !"dify".equals(latest.getAgentType())
				|| !"chat".equals(latest.getDifyMode()) || !snapshot.appId().equals(latest.getDifyAppId())
				|| !tenant.equals(TenantContextHolder.getTenantId()))
			throw denied();
		return snapshot;
	}

	private static org.springframework.security.access.AccessDeniedException denied() {
		return new org.springframework.security.access.AccessDeniedException("当前用户无权读取该市场智能体的配置");
	}

}
