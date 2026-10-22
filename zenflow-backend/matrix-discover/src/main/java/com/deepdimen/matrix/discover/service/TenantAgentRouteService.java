package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiTenantAgentRouteEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiTenantAgentRouteMapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantAgentRouteService {

	public static final String UNIFIED_ASSISTANT = "UNIFIED_ASSISTANT";

	public static final String WORK_BRIEF = "WORK_BRIEF";

	private final AiTenantAgentRouteMapper routeMapper;

	private final AiAgentMapper agentMapper;

	private final AiAgentSkillService agentSkillService;

	public ResolvedTenantAgentRoute resolve(IdentityEnvelope identity, String channelCode) {
		Long tenantId = requireCurrentTenant(identity);
		Long orgId = parseOptional(identity.currentOrgId());
		AiTenantAgentRouteEntity route = routeMapper
			.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
				.eq(AiTenantAgentRouteEntity::getTenantId, tenantId)
				.eq(AiTenantAgentRouteEntity::getChannelCode, channelCode)
				.eq(AiTenantAgentRouteEntity::getEnabled, 1)
				.in(AiTenantAgentRouteEntity::getOrgId,
						orgId == null ? java.util.List.of(0L) : java.util.List.of(orgId, 0L)))
			.stream()
			.sorted(Comparator
				.comparing((AiTenantAgentRouteEntity item) -> orgId != null && orgId.equals(item.getOrgId()))
				.reversed())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("当前租户未配置渠道智能体：" + channelCode));
		AiAgentEntity agent = validateAgent(identity, route.getAgentId());
		return new ResolvedTenantAgentRoute(route.getId(), tenantId, route.getOrgId(), channelCode, agent,
				route.getReleaseVersion());
	}

	public Optional<ResolvedTenantAgentRoute> findTenantDefault(IdentityEnvelope identity, String channelCode) {
		Long tenantId = requireCurrentTenant(identity);
		AiTenantAgentRouteEntity route = routeMapper
			.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
				.eq(AiTenantAgentRouteEntity::getTenantId, tenantId)
				.eq(AiTenantAgentRouteEntity::getOrgId, 0L)
				.eq(AiTenantAgentRouteEntity::getChannelCode, channelCode)
				.eq(AiTenantAgentRouteEntity::getEnabled, 1))
			.stream()
			.findFirst()
			.orElse(null);
		if (route == null) {
			return Optional.empty();
		}
		AiAgentEntity agent = validateAgent(identity, route.getAgentId());
		return Optional.of(new ResolvedTenantAgentRoute(route.getId(), tenantId, route.getOrgId(), channelCode, agent,
				route.getReleaseVersion()));
	}

	@Transactional(rollbackFor = Exception.class)
	public ResolvedTenantAgentRoute configureTenantDefault(IdentityEnvelope identity, String channelCode,
			Long agentId) {
		if (!UNIFIED_ASSISTANT.equals(channelCode)) {
			throw new IllegalArgumentException("当前仅支持配置统一对话智能体");
		}
		Long tenantId = requireCurrentTenant(identity);
		AiAgentEntity agent = validateAgent(identity, agentId);
		if (!"dify".equals(agent.getAgentType())
				|| !Set.of("chat", "agent-chat", "advanced-chat").contains(agent.getDifyMode())
				|| !StringUtils.hasText(agent.getDifyApiKey())) {
			throw new IllegalArgumentException("统一对话智能体必须是已发布且已配置 API Key 的 Dify 对话应用");
		}

		LocalDateTime now = LocalDateTime.now();
		AiTenantAgentRouteEntity route = routeMapper
			.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
				.eq(AiTenantAgentRouteEntity::getTenantId, tenantId)
				.eq(AiTenantAgentRouteEntity::getOrgId, 0L)
				.eq(AiTenantAgentRouteEntity::getChannelCode, channelCode))
			.stream()
			.findFirst()
			.orElse(null);
		if (route == null) {
			route = new AiTenantAgentRouteEntity();
			route.setTenantId(tenantId);
			route.setOrgId(0L);
			route.setChannelCode(channelCode);
			route.setCreateBy(identity.userId());
			route.setCreateTime(now);
			route.setDelFlag("0");
			route.setReleaseVersion("v1");
			route.setEnabled(1);
			route.setAgentId(agentId);
			route.setUpdateBy(identity.userId());
			route.setUpdateTime(now);
			routeMapper.insert(route);
		}
		else {
			route.setAgentId(agentId);
			route.setEnabled(1);
			route.setDelFlag("0");
			route.setReleaseVersion(StringUtils.hasText(route.getReleaseVersion()) ? route.getReleaseVersion() : "v1");
			route.setUpdateBy(identity.userId());
			route.setUpdateTime(now);
			routeMapper.updateById(route);
		}

		agentSkillService.syncEnabledSkillsToAgent(agentId);
		return new ResolvedTenantAgentRoute(route.getId(), tenantId, 0L, channelCode, agent, route.getReleaseVersion());
	}

	/**
	 * Dedicated native cut-over path. The existing Dify-only method intentionally remains
	 * unchanged so legacy management callers cannot broaden their contract.
	 */
	@Transactional(rollbackFor = Exception.class)
	public RouteTransition switchTenantDefaultNative(IdentityEnvelope identity, Long nativeAgentId) {
		Long tenantId = requireCurrentTenant(identity);
		AiAgentEntity nativeAgent = validateAgent(identity, nativeAgentId);
		if (!"native".equals(nativeAgent.getAgentType())) {
			throw new IllegalArgumentException("统一对话原生路由只能切换到 native Profile");
		}
		AiTenantAgentRouteEntity route = routeMapper
			.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
				.eq(AiTenantAgentRouteEntity::getTenantId, tenantId)
				.eq(AiTenantAgentRouteEntity::getOrgId, 0L)
				.eq(AiTenantAgentRouteEntity::getChannelCode, UNIFIED_ASSISTANT))
			.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("当前租户缺少可切换的统一对话路由"));
		Long previousAgentId = route.getAgentId();
		int updated = routeMapper.update(null,
				Wrappers.<AiTenantAgentRouteEntity>update()
					.eq("id", route.getId())
					.eq("tenant_id", tenantId)
					.eq("agent_id", previousAgentId)
					.set("agent_id", nativeAgentId)
					.set("enabled", 1)
					.set("update_by", identity.userId())
					.set("update_time", LocalDateTime.now()));
		if (updated != 1) {
			throw new IllegalStateException("原生统一对话路由切换失败");
		}
		return new RouteTransition(route.getId(), previousAgentId, nativeAgentId);
	}

	@Transactional(rollbackFor = Exception.class)
	public void restoreTenantDefault(IdentityEnvelope identity, Long expectedCurrentAgentId, Long previousAgentId) {
		Long tenantId = requireCurrentTenant(identity);
		validateAgent(identity, previousAgentId);
		AiTenantAgentRouteEntity route = routeMapper
			.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
				.eq(AiTenantAgentRouteEntity::getTenantId, tenantId)
				.eq(AiTenantAgentRouteEntity::getOrgId, 0L)
				.eq(AiTenantAgentRouteEntity::getChannelCode, UNIFIED_ASSISTANT))
			.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("统一对话路由不存在"));
		if (!expectedCurrentAgentId.equals(route.getAgentId())) {
			throw new IllegalStateException("ROUTE_CHANGED_SINCE_SWITCH");
		}
		int updated = routeMapper.update(null,
				Wrappers.<AiTenantAgentRouteEntity>update()
					.eq("id", route.getId())
					.eq("tenant_id", tenantId)
					.eq("agent_id", expectedCurrentAgentId)
					.set("agent_id", previousAgentId)
					.set("update_by", identity.userId())
					.set("update_time", LocalDateTime.now()));
		if (updated != 1) {
			throw new IllegalStateException("统一对话路由回滚失败");
		}
	}

	private Long requireCurrentTenant(IdentityEnvelope identity) {
		Long tenantId = parseRequired(identity.tenantId(), "租户");
		if (!tenantId.equals(TenantContextHolder.getTenantId())) {
			throw new IllegalStateException("请求租户与登录身份不一致");
		}
		return tenantId;
	}

	private AiAgentEntity validateAgent(IdentityEnvelope identity, Long agentId) {
		AiAgentEntity agent = agentId == null ? null : TenantBroker.noneAs(() -> agentMapper.selectById(agentId));
		if (agent == null || !"published".equals(agent.getStatus()) || !"0".equals(agent.getDelFlag())) {
			throw new IllegalStateException("租户渠道智能体未发布");
		}
		if (!identity.tenantId().equals(agent.getTenantId())) {
			throw new IllegalStateException("租户渠道智能体归属不一致");
		}
		return agent;
	}

	private Long parseRequired(String value, String label) {
		Long result = parseOptional(value);
		if (result == null) {
			throw new IllegalStateException(label + "上下文缺失");
		}
		return result;
	}

	private Long parseOptional(String value) {
		try {
			return value == null || value.isBlank() ? null : Long.valueOf(value);
		}
		catch (NumberFormatException ex) {
			throw new IllegalStateException("非法身份上下文", ex);
		}
	}

	public record ResolvedTenantAgentRoute(Long routeId, Long tenantId, Long orgId, String channelCode,
			AiAgentEntity agent, String releaseVersion) {
	}

	public record RouteTransition(Long routeId, Long previousAgentId, Long newAgentId) {
	}

}
