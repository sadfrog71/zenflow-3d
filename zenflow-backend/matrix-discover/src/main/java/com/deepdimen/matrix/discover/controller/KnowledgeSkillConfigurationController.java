package com.deepdimen.matrix.discover.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.skill.api.AgentSkillConfigurationAdapter;
import com.deepdimen.matrix.skill.api.AgentSkillTargetReader;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;

@RestController
@RequestMapping("/aiAgent")
public class KnowledgeSkillConfigurationController {

	private final AgentSkillTargetReader targets;

	private final List<AgentSkillConfigurationAdapter> adapters;

	public KnowledgeSkillConfigurationController(AgentSkillTargetReader targets,
			List<AgentSkillConfigurationAdapter> adapters) {
		this.targets = targets;
		this.adapters = List.copyOf(adapters);
	}

	@GetMapping("/{id}/knowledge-skill-configuration")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit') && @pms.hasPermission('knowledge_skill_enable')")
	public R<AgentSkillConfigurationAdapter.Configuration> get(@PathVariable Long id) {
		if (id == null || id <= 0 || id == 2026L)
			throw new IllegalArgumentException("AGENT_SKILL_UNSUPPORTED");
		var consumer = new Consumer("MARKET_AGENT", id.toString(), "CHAT", "configuration-check");
		var target = targets.read(consumer);
		if (target == null || !id.toString().equals(target.agentId()) || !"knowledge".equals(target.agentType()))
			throw new IllegalArgumentException("AGENT_SKILL_UNSUPPORTED");
		var matches = adapters.stream().filter(adapter -> "knowledge".equals(adapter.agentType())).toList();
		if (matches.size() != 1)
			throw new IllegalStateException("AGENT_ADAPTER_UNAVAILABLE");
		return R.ok(matches.get(0).inspect(consumer));
	}

}
