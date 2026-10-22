package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.skill.MarketSkillCapabilities;
import com.deepdimen.matrix.skill.api.SkillContract.AgentCapabilities;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/aiAgent")
public class MarketSkillCapabilitiesController {

	private final AiAgentService agents;

	private final MarketSkillCapabilities capabilities;

	@GetMapping("/{id}/skill-capabilities")
	@PreAuthorize("isAuthenticated()")
	public R<AgentCapabilities> get(@PathVariable Long id) {
		Long tenant = TenantContextHolder.getTenantId();
		if (tenant == null || tenant <= 0 || id == null || id <= 0 || AiAgentService.DEFAULT_AGENT_ID.equals(id))
			throw new AccessDeniedException("该智能体不能通过市场 Skill 入口访问");
		var agent = agents.getAgentDetail(id);
		if (agent == null || !id.equals(agent.getId()) || !Boolean.TRUE.equals(agent.getAccessible()))
			throw new AccessDeniedException("当前用户无权访问该智能体");
		return R.ok(capabilities.inspect(tenant.toString(), id.toString(), agent.getAgentType()));
	}

}
