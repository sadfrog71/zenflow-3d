package com.deepdimen.matrix.assistant.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.CancelRunResponse;
import com.deepdimen.matrix.assistant.run.AssistantRunEventStreamService;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;

@RestController
@RequestMapping("/assistant/sessions/{sessionId}/runs/{runId}")
@ConditionalOnBean(AssistantIdentityResolver.class)
public class AssistantRunController {

	private final AssistantIdentityResolver identityResolver;

	private final AssistantSessionService sessionService;

	private final AssistantRunEventStreamService streamService;

	public AssistantRunController(AssistantIdentityResolver identityResolver, AssistantSessionService sessionService,
			AssistantRunEventStreamService streamService) {
		this.identityResolver = identityResolver;
		this.sessionService = sessionService;
		this.streamService = streamService;
	}

	@GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@PathVariable("sessionId") String sessionId, @PathVariable("runId") String runId,
			@RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
			@RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
		sessionService.runSnapshot(identityResolver.currentIdentity(), sessionId, runId);
		return streamService.stream(runId, Math.max(afterSeq, parseLastEventId(lastEventId)));
	}

	@PostMapping("/cancel")
	public CancelRunResponse cancel(@PathVariable("sessionId") String sessionId, @PathVariable("runId") String runId) {
		return sessionService.cancelRun(identityResolver.currentIdentity(), sessionId, runId);
	}

	private long parseLastEventId(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Math.max(0, Long.parseLong(value));
		}
		catch (NumberFormatException ex) {
			throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
					"Last-Event-ID must be a non-negative integer");
		}
	}

}
