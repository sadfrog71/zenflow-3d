package com.deepdimen.matrix.assistant.controller;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessagePage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSessionPage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SendMessageRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSkillCapability;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSkillPreferenceRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionPreferencesRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionStatusRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionTitleRequest;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.CreateRunRequest;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunAcceptedResponse;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunSnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantSkillCatalogProvider;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;

@RestController
@RequestMapping("/assistant")
@ConditionalOnBean(AssistantIdentityResolver.class)
public class AssistantSessionController {

	private final AssistantIdentityResolver identityResolver;

	private final AssistantSessionService sessionService;

	private final AssistantSkillCatalogProvider skillCatalogProvider;

	public AssistantSessionController(AssistantIdentityResolver identityResolver,
			AssistantSessionService sessionService, AssistantSkillCatalogProvider skillCatalogProvider) {
		this.identityResolver = identityResolver;
		this.sessionService = sessionService;
		this.skillCatalogProvider = skillCatalogProvider;
	}

	@GetMapping("/skills/catalog")
	public List<AssistantSkillCapability> skills() {
		return skillCatalogProvider.listSkills(identityResolver.currentIdentity());
	}

	@PutMapping("/skills/preferences")
	public void saveSkillPreferences(@RequestBody UpdateSkillPreferenceRequest request) {
		skillCatalogProvider.savePreferences(identityResolver.currentIdentity(), request.enabledSkillCodes());
	}

	@GetMapping("/execution-runs")
	@PreAuthorize("isAuthenticated()")
	public List<Map<String, Object>> executionRuns() {
		return sessionService.executionRuns(identityResolver.currentIdentity());
	}

	@GetMapping("/execution-runs/tenant")
	@PreAuthorize("isAuthenticated() and @pms.hasPermission('knowledge_agent_view')")
	public List<Map<String, Object>> tenantExecutionRuns() {
		return sessionService.tenantExecutionRuns(identityResolver.currentIdentity());
	}

	@PostMapping("/sessions")
	public AssistantSession create(@RequestBody(required = false) CreateSessionRequest request) {
		return sessionService.create(identityResolver.currentIdentity(), request);
	}

	@GetMapping("/sessions")
	public AssistantSessionPage sessions(@RequestParam(name = "status", defaultValue = "ACTIVE") SessionStatus status,
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "limit", defaultValue = "30") int limit) {
		return sessionService.listSessions(identityResolver.currentIdentity(), status, keyword, cursor, limit);
	}

	@GetMapping("/sessions/{sessionId}")
	public AssistantSession get(@PathVariable("sessionId") String sessionId) {
		return sessionService.get(identityResolver.currentIdentity(), sessionId);
	}

	@PatchMapping("/sessions/{sessionId}/title")
	public AssistantSession updateTitle(@PathVariable("sessionId") String sessionId,
			@RequestBody UpdateSessionTitleRequest request) {
		return sessionService.updateTitle(identityResolver.currentIdentity(), sessionId, request);
	}

	@PutMapping("/sessions/{sessionId}/preferences")
	public AssistantSession updatePreferences(@PathVariable("sessionId") String sessionId,
			@RequestBody UpdateSessionPreferencesRequest request) {
		return sessionService.updatePreferences(identityResolver.currentIdentity(), sessionId, request);
	}

	@PostMapping("/sessions/{sessionId}/archive")
	public AssistantSession archive(@PathVariable("sessionId") String sessionId,
			@RequestBody UpdateSessionStatusRequest request) {
		return sessionService.archive(identityResolver.currentIdentity(), sessionId, request);
	}

	@PostMapping("/sessions/{sessionId}/restore")
	public AssistantSession restore(@PathVariable("sessionId") String sessionId,
			@RequestBody UpdateSessionStatusRequest request) {
		return sessionService.restore(identityResolver.currentIdentity(), sessionId, request);
	}

	@GetMapping("/sessions/{sessionId}/messages")
	public List<AssistantMessage> messages(@PathVariable("sessionId") String sessionId) {
		return sessionService.messages(identityResolver.currentIdentity(), sessionId);
	}

	@GetMapping("/sessions/{sessionId}/messages/page")
	public AssistantMessagePage messagePage(@PathVariable("sessionId") String sessionId,
			@RequestParam(name = "beforeSequence", required = false) Long beforeSequence,
			@RequestParam(name = "limit", defaultValue = "50") int limit) {
		return sessionService.messagePage(identityResolver.currentIdentity(), sessionId, beforeSequence, limit);
	}

	@PostMapping("/sessions/{sessionId}/messages")
	public MessageExchange send(@PathVariable("sessionId") String sessionId, @RequestBody SendMessageRequest request) {
		return sessionService.send(identityResolver.currentIdentity(), sessionId, request);
	}

	@PostMapping("/sessions/{sessionId}/runs")
	public ResponseEntity<RunAcceptedResponse> createRun(@PathVariable("sessionId") String sessionId,
			@RequestBody CreateRunRequest request) {
		return ResponseEntity.accepted()
			.body(sessionService.createRun(identityResolver.currentIdentity(), sessionId, request));
	}

	@GetMapping("/sessions/{sessionId}/runs")
	public List<RunSnapshot> runs(@PathVariable("sessionId") String sessionId,
			@RequestParam(value = "recent", defaultValue = "false") boolean recent) {
		return recent ? sessionService.recentRuns(identityResolver.currentIdentity(), sessionId)
				: sessionService.runs(identityResolver.currentIdentity(), sessionId);
	}

	public List<RunSnapshot> runs(String sessionId) {
		return runs(sessionId, false);
	}

	@GetMapping("/sessions/{sessionId}/runs/{runId}")
	public RunSnapshot runSnapshot(@PathVariable("sessionId") String sessionId, @PathVariable("runId") String runId) {
		return sessionService.runSnapshot(identityResolver.currentIdentity(), sessionId, runId);
	}

	@GetMapping("/sessions/{sessionId}/context-packs/{contextPackId}")
	public AssistantContextPack contextPack(@PathVariable("sessionId") String sessionId,
			@PathVariable("contextPackId") String contextPackId) {
		return sessionService.contextPack(identityResolver.currentIdentity(), sessionId, contextPackId);
	}

	@GetMapping("/sessions/{sessionId}/context-packs/{contextPackId}/citations/{citationId}")
	public CitationResolution resolveCitation(@PathVariable("sessionId") String sessionId,
			@PathVariable("contextPackId") String contextPackId, @PathVariable("citationId") String citationId) {
		return sessionService.resolveCitation(identityResolver.currentIdentity(), sessionId, contextPackId, citationId);
	}

}
