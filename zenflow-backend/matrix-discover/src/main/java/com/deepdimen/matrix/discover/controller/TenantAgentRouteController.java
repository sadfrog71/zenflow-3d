package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService.ResolvedTenantAgentRoute;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent-route")
public class TenantAgentRouteController {

	private final TenantAgentRouteService routeService;
	private final AssistantIdentityResolver identityResolver;

	@GetMapping("/default")
	@Operation(summary = "查询当前租户和组织的渠道默认智能体")
	public R<DefaultAgentRouteVO> defaultRoute(
			@RequestParam(defaultValue = TenantAgentRouteService.UNIFIED_ASSISTANT) String channel) {
		ResolvedTenantAgentRoute route = routeService.resolve(identityResolver.currentIdentity(), channel);
		AiAgentEntity agent = route.agent();
		return R.ok(new DefaultAgentRouteVO(agent.getId(), agent.getName(), agent.getDescription(), agent.getAvatar(),
				agent.getCategory(), agent.getStatus(), agent.getDifyMode(), route.channelCode(), route.releaseVersion()));
	}

	@GetMapping("/config")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view', 'knowledge_skill_view')")
	@Operation(summary = "查询当前租户的统一对话智能体配置")
	public R<DefaultAgentRouteVO> tenantDefaultConfig(
			@RequestParam(defaultValue = TenantAgentRouteService.UNIFIED_ASSISTANT) String channel) {
		DefaultAgentRouteVO result = routeService.findTenantDefault(identityResolver.currentIdentity(), channel)
			.map(this::toVO)
			.orElse(null);
		return R.ok(result);
	}

	@PutMapping("/default")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	@Operation(summary = "配置当前租户的统一对话智能体并同步 Skill")
	public R<DefaultAgentRouteVO> configureTenantDefault(@Valid @RequestBody DefaultAgentRouteRequest request) {
		ResolvedTenantAgentRoute route = routeService.configureTenantDefault(identityResolver.currentIdentity(),
				request.channelCode(), request.agentId());
		return R.ok(toVO(route));
	}

	private DefaultAgentRouteVO toVO(ResolvedTenantAgentRoute route) {
		AiAgentEntity agent = route.agent();
		return new DefaultAgentRouteVO(agent.getId(), agent.getName(), agent.getDescription(), agent.getAvatar(),
				agent.getCategory(), agent.getStatus(), agent.getDifyMode(), route.channelCode(), route.releaseVersion());
	}

	public record DefaultAgentRouteRequest(@NotNull Long agentId, String channelCode) {

		public DefaultAgentRouteRequest {
			channelCode = channelCode == null || channelCode.isBlank()
					? TenantAgentRouteService.UNIFIED_ASSISTANT : channelCode;
		}
	}

	public record DefaultAgentRouteVO(Long id, String name, String description, String avatar, String category,
			String status, String difyMode, String channelCode, String releaseVersion) {
	}
}
