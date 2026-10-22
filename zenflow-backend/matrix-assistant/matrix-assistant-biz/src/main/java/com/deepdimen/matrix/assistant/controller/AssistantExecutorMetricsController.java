package com.deepdimen.matrix.assistant.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.run.AssistantRunDispatchService;

/**
 * Shared instance pools are platform diagnostics; tenant managers cannot query other
 * tenants' load.
 */
@RestController
@RequestMapping("/assistant/metrics/executors")
@ConditionalOnBean(AssistantIdentityResolver.class)
public class AssistantExecutorMetricsController {

	private final AssistantRunDispatchService dispatch;

	public AssistantExecutorMetricsController(AssistantRunDispatchService dispatch) {
		this.dispatch = dispatch;
	}

	@GetMapping
	@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
	public AssistantRunDispatchService.ExecutorSnapshot query() {
		return dispatch.metrics();
	}

}
