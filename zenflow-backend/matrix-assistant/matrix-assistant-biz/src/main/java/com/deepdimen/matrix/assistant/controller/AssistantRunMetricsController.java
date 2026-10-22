package com.deepdimen.matrix.assistant.controller;

import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsRepository;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsService;

@RestController
@RequestMapping("/assistant/metrics")
@ConditionalOnBean(AssistantIdentityResolver.class)
public class AssistantRunMetricsController {

	private final AssistantIdentityResolver identities;

	private final AssistantRunMetricsService metrics;

	private final ObjectProvider<AssistantRunMetricsRepository> repositories;

	public AssistantRunMetricsController(AssistantIdentityResolver identities, AssistantRunMetricsService metrics,
			ObjectProvider<AssistantRunMetricsRepository> repositories) {
		this.identities = identities;
		this.metrics = metrics;
		this.repositories = repositories;
	}

	@GetMapping
	@PreAuthorize("@pms.hasPermission('assistant_provider_tenant_manage')")
	public AssistantRunMetricsService.QueryResult query(
			@RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(name = "limit", defaultValue = "100") int limit) {
		var identity = identities.currentIdentity();
		var repository = repositories.getIfAvailable();
		if (repository == null)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "运行指标存储未启用");
		return metrics.query(identity, repository, from, to, limit);
	}

}
