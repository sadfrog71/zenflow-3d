package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.skill.DifyMarketConfigurationReader;
import com.deepdimen.matrix.discover.skill.DifyChatConfigurationSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/aiAgent")
public class MarketSkillConfigurationController {

	private final DifyMarketConfigurationReader configurations;

	@GetMapping("/{id}/skill-configuration")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit') && @pms.hasPermission('knowledge_skill_enable')")
	public R<DifyChatConfigurationSnapshot> get(@PathVariable Long id) {
		return R.ok(configurations.read(id));
	}

}
