package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.skill.MarketSkillRequestStore;
import com.deepdimen.matrix.discover.skill.MarketSkillResultStore;
import com.deepdimen.matrix.skill.api.SkillRunMetadataReader;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Read access remains available when new market executions are disabled. */
@RestController
@RequestMapping("/aiAgent")
@RequiredArgsConstructor
public class MarketSkillRequestController {

	private final MarketSkillRequestStore requests;

	private final SkillRunMetadataReader metadata;

	private final MarketSkillResultStore results;

	@GetMapping("/{id}/skill-requests/{clientRequestId}")
	@PreAuthorize("isAuthenticated()")
	public R<RunView> find(@PathVariable Long id, @PathVariable String clientRequestId) {
		var row = requests.findByClientRequest(id, clientRequestId);
		if (row == null)
			return R.ok(new RunView(clientRequestId, false, null, null, null, null, RunStatus.UNKNOWN, null));
		var business = row.getId().toString();
		var identity = new Identity(row.getTenantId().toString(), row.getActorId().toString(),
				row.getActorId().toString(), "market-skill:" + business);
		var consumer = new Consumer("MARKET_AGENT", row.getAgentId().toString(), "CHAT", row.getBindingKey());
		var run = metadata.find(identity.tenantId(), identity.actorId(), consumer, business);
		if (run == null)
			return R
				.ok(new RunView(clientRequestId, true, row.getBindingKey(), null, null, null, RunStatus.UNKNOWN, null));
		RunStatus status = RunStatus.valueOf(run.status());
		String output = null;
		if (status == RunStatus.SUCCEEDED) {
			output = results.read(identity, consumer, business, run.skillRunId(),
					"market-skill:" + business + ":" + run.skillRunId());
		}
		requests.load(identity, consumer, business);
		return R.ok(new RunView(clientRequestId, true, row.getBindingKey(), run.skillRunId(), run.releaseId(),
				run.bindingRevisionId(), status, output));
	}

	public record RunView(String clientRequestId, boolean requestFound, String bindingKey, String skillRunId,
			String actualReleaseId, String bindingRevisionId, RunStatus status, String resultJson) {
	}

}
