package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.skill.MarketSkillRequestStore;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** The resolution token stays in this server call and is never a browser credential. */
@RestController
@RequestMapping("/aiAgent")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "matrix.skill.market", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MarketSkillRunController {

	private final MarketSkillRequestStore requests;

	private final SkillBindingResolver resolver;

	private final SkillExecutionService executions;

	private final ObjectMapper json;

	@PostMapping("/{id}/skill-runs")
	@PreAuthorize("isAuthenticated()")
	public void run(@PathVariable Long id, @Valid @RequestBody RunRequest request, HttpServletResponse response)
			throws IOException {
		if (request.input() == null || !request.input().isObject())
			throw new IllegalArgumentException("INPUT_INVALID");
		var saved = requests.prepare(id, request.bindingKey(), request.clientRequestId(), request.input().toString());
		var identity = new Identity(saved.getTenantId().toString(), saved.getActorId().toString(),
				saved.getActorId().toString(), "market-skill:" + saved.getId());
		var consumer = new Consumer("MARKET_AGENT", saved.getAgentId().toString(), "CHAT", saved.getBindingKey());
		var resolved = resolver.resolve(new ResolveRequest(identity, consumer, null, saved.getId().toString(), "{}"));
		if (!request.expectedReleaseId().equals(resolved.snapshot().releaseId())
				|| !request.expectedBindingRevisionId().equals(resolved.snapshot().bindingRevisionId()))
			throw new IllegalArgumentException("BINDING_REVISION_CONFLICT");
		ExecuteResult result;
		try {
			result = executions.execute(new ExecuteRequest(identity, consumer, resolved.resolvedSnapshotToken(),
					saved.getInputSnapshot(), null, request.clientRequestId(), null));
		}
		catch (SkillExecutionFailure failure) {
			result = failure.result();
		}
		// Also works for idempotent replay, where the transport did not write any events.
		response.setCharacterEncoding("UTF-8");
		boolean streaming = "streaming".equals(request.input().path("response_mode").asText());
		if (streaming) {
			response.setContentType("text/event-stream");
			response.setHeader("Cache-Control", "no-cache");
			response.getWriter().write("event: skill_result\ndata: " + json.writeValueAsString(result) + "\n\n");
		}
		else {
			response.setContentType("application/json");
			response.getWriter().write(json.writeValueAsString(R.ok(result)));
		}
		response.getWriter().flush();
	}

	public record RunRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String bindingKey,
			@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String clientRequestId,
			@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedReleaseId,
			@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedBindingRevisionId, @NotNull JsonNode input) {
	}

}
