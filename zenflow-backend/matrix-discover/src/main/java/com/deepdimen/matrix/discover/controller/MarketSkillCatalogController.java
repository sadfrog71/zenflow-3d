package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.mapper.MarketSkillCatalogMapper;
import com.deepdimen.matrix.discover.mapper.MarketSkillCatalogMapper.BoundSkill;
import com.deepdimen.matrix.discover.service.AiAgentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/aiAgent")
public class MarketSkillCatalogController {

	private final AiAgentService agents;

	private final TenantUserDirectory users;

	private final MarketSkillCatalogMapper catalog;

	/** Listing a binding is not an execution permit or a capability claim. */
	@GetMapping("/{id}/skill-bindings")
	@PreAuthorize("isAuthenticated()")
	public R<Page> list(@PathVariable Long id, @RequestParam(required = false) Long beforeId) {
		Long tenant = TenantContextHolder.getTenantId();
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var user = SecurityUtils.getUser();
		if (tenant == null || tenant <= 0 || id == null || id <= 0 || AiAgentService.DEFAULT_AGENT_ID.equals(id)
				|| auth == null || !auth.isAuthenticated() || user == null || user.getId() == null
				|| !users.isAssignableUser(tenant, user.getId()))
			throw denied();
		if (beforeId != null && beforeId <= 0)
			throw new IllegalArgumentException("分页游标无效");
		var agent = agents.getById(id);
		if (agent == null || !id.equals(agent.getId()) || !tenant.toString().equals(agent.getTenantId())
				|| !"published".equals(agent.getStatus()))
			throw denied();
		var detail = agents.getAgentDetail(id);
		if (detail == null || !id.equals(detail.getId()) || !Boolean.TRUE.equals(detail.getAccessible()))
			throw denied();
		var rows = catalog.list(tenant.toString(), id.toString(), beforeId);
		boolean more = rows.size() > 50;
		var items = List.copyOf(rows.subList(0, Math.min(50, rows.size())));
		return R.ok(new Page(items, more ? items.get(items.size() - 1).getBindingId() : null));
	}

	public record Page(List<BoundSkill> items, String nextCursor) {
	}

	private static AccessDeniedException denied() {
		return new AccessDeniedException("当前用户无权访问该智能体的 Skill 绑定");
	}

}
