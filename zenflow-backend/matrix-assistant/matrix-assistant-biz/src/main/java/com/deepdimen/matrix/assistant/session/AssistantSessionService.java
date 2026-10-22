package com.deepdimen.matrix.assistant.session;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.attachment.AssistantAttachmentService;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.CreateRunRequest;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.CancelRunResponse;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunAcceptedResponse;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunSnapshot;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessagePage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSessionPage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSessionSummary;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SendMessageRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionPreferencesRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionStatusRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionTitleRequest;
import com.deepdimen.matrix.assistant.orchestrator.AssistantContextOrchestrator;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord;
import com.deepdimen.matrix.assistant.run.AssistantSourceSelectionNormalizer;
import com.deepdimen.matrix.assistant.run.AssistantSourceSelectionNormalizer.NormalizedSourceSelection;

@Service
public class AssistantSessionService {

	private final AssistantSessionRepository repository;

	private final AssistantContextOrchestrator orchestrator;

	private final AssistantRunEngine runEngine;

	private final AssistantAttachmentService attachmentService;

	private final AssistantSourceSelectionNormalizer sourceSelectionNormalizer;

	@Value("${matrix.assistant.blocking-timeout-seconds:60}")
	private long blockingTimeoutSeconds = 60;

	public AssistantSessionService(AssistantSessionRepository repository, AssistantContextOrchestrator orchestrator,
			AssistantRunEngine runEngine) {
		this(repository, orchestrator, runEngine, null, new AssistantSourceSelectionNormalizer());
	}

	public AssistantSessionService(AssistantSessionRepository repository, AssistantContextOrchestrator orchestrator,
			AssistantRunEngine runEngine, AssistantAttachmentService attachmentService) {
		this(repository, orchestrator, runEngine, attachmentService, new AssistantSourceSelectionNormalizer());
	}

	@Autowired
	public AssistantSessionService(AssistantSessionRepository repository, AssistantContextOrchestrator orchestrator,
			AssistantRunEngine runEngine, AssistantAttachmentService attachmentService,
			AssistantSourceSelectionNormalizer sourceSelectionNormalizer) {
		this.repository = repository;
		this.orchestrator = orchestrator;
		this.runEngine = runEngine;
		this.attachmentService = attachmentService;
		this.sourceSelectionNormalizer = sourceSelectionNormalizer;
	}

	public AssistantSession create(IdentityEnvelope identity, CreateSessionRequest request) {
		Instant now = Instant.now();
		List<ProviderId> requested = request == null || request.activeScopes() == null ? List.of()
				: request.activeScopes();
		List<ProviderId> scopes = validateProviderSelection(identity, requested);
		String title = request == null || request.title() == null || request.title().isBlank() ? "新对话"
				: request.title().trim();
		return repository
			.saveSession(new AssistantSession(UUID.randomUUID().toString(), identity.tenantId(), identity.userId(),
					title, SessionStatus.ACTIVE, scopes, null, request == null ? null : request.sourceRoute(),
                    ("新对话".equals(title)) ? com.deepdimen.matrix.assistant.api.AssistantSessionContract.TitleSource.DEFAULT : com.deepdimen.matrix.assistant.api.AssistantSessionContract.TitleSource.MANUAL, null, null, 1, now, now, identity.subjectType()));
	}

	public AssistantSession get(IdentityEnvelope identity, String sessionId) {
		return requireOwned(identity, sessionId);
	}

	public AssistantSessionPage listSessions(IdentityEnvelope identity, SessionStatus status, String keyword,
			String cursor, int limit) {
		requireExecutionRunIdentity(identity);
		int safeLimit = Math.max(1, Math.min(limit, 100));
		SessionCursor decoded = decodeCursor(cursor);
		List<AssistantSessionSummary> found = repository.findSessions(identity.tenantId(), identity.userId(),
				status == null ? SessionStatus.ACTIVE : status, normalizeKeyword(keyword),
				decoded == null ? null : decoded.lastMessageAt(), decoded == null ? null : decoded.sessionId(),
				safeLimit + 1, identity.subjectType());
		boolean hasMore = found.size() > safeLimit;
		List<AssistantSessionSummary> items = hasMore ? List.copyOf(found.subList(0, safeLimit)) : found;
		String nextCursor = hasMore && !items.isEmpty() ? encodeCursor(items.get(items.size() - 1)) : null;
		return new AssistantSessionPage(items, nextCursor, hasMore);
	}

	public AssistantSession updateTitle(IdentityEnvelope identity, String sessionId,
			UpdateSessionTitleRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
		}
		String title = normalizeTitle(request.title());
		requireOwned(identity, sessionId);
		return repository
			.updateTitle(sessionId, identity.tenantId(), identity.userId(), title, request.expectedVersion(), identity.subjectType())
			.orElseThrow(this::versionConflict);
	}

	public AssistantSession updatePreferences(IdentityEnvelope identity, String sessionId,
			UpdateSessionPreferencesRequest request) {
		if (request == null || request.activeScopes() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activeScopes 不能为空");
		}
		if (request.activeSkillCode() != null && request.activeSkillCode().length() > 128) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能代码过长");
		}
		requireOwned(identity, sessionId);
		List<ProviderId> scopes = validateProviderSelection(identity, request.activeScopes());
		return repository
			.updatePreferences(sessionId, identity.tenantId(), identity.userId(), scopes, request.activeSkillCode(),
					request.lastSourceRoute(), request.expectedVersion(), identity.subjectType())
			.orElseThrow(this::versionConflict);
	}

	public AssistantSession archive(IdentityEnvelope identity, String sessionId, UpdateSessionStatusRequest request) {
		return updateStatus(identity, sessionId, request, SessionStatus.ARCHIVED);
	}

	public AssistantSession restore(IdentityEnvelope identity, String sessionId, UpdateSessionStatusRequest request) {
		return updateStatus(identity, sessionId, request, SessionStatus.ACTIVE);
	}

	public List<AssistantMessage> messages(IdentityEnvelope identity, String sessionId) {
		requireOwned(identity, sessionId);
		return repository.findMessages(sessionId);
	}

	public AssistantMessagePage messagePage(IdentityEnvelope identity, String sessionId, Long beforeSequence,
			int limit) {
		requireOwned(identity, sessionId);
		if (beforeSequence != null && beforeSequence <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beforeSequence 必须大于 0");
		}
		int safeLimit = Math.max(1, Math.min(limit, 100));
		List<AssistantMessage> found = repository.findMessagesBefore(sessionId, beforeSequence, safeLimit + 1);
		boolean hasMore = found.size() > safeLimit;
		List<AssistantMessage> items = hasMore ? List.copyOf(found.subList(found.size() - safeLimit, found.size()))
				: found;
		Long nextBeforeSequence = hasMore && !items.isEmpty() ? items.get(0).sequenceNo() : null;
		return new AssistantMessagePage(items, nextBeforeSequence, hasMore);
	}

	public List<Map<String, Object>> executionRuns(IdentityEnvelope identity) {
		requireExecutionRunIdentity(identity);
		return repository.listExecutionRuns(identity.tenantId(), identity.userId(), false, 100, identity.subjectType());
	}

	@PreAuthorize("isAuthenticated() and @pms.hasPermission('knowledge_agent_view')")
	public List<Map<String, Object>> tenantExecutionRuns(IdentityEnvelope identity) {
		requireExecutionRunIdentity(identity);
		return repository.listExecutionRuns(identity.tenantId(), identity.userId(), true, 100, identity.subjectType());
	}

	private void requireExecutionRunIdentity(IdentityEnvelope identity) {
		if (identity == null || identity.tenantId() == null || identity.tenantId().isBlank()
				|| identity.userId() == null || identity.userId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未获取到有效登录身份");
		}
	}

	public AssistantContextPack contextPack(IdentityEnvelope identity, String sessionId, String contextPackId) {
		requireOwned(identity, sessionId);
		AssistantContextPack contextPack = repository.findContextPack(contextPackId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Context Pack 不存在"));
		if (!sessionId.equals(contextPack.assistantSessionId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Context Pack 不存在");
		}
		if (contextPack.expiresAt().isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.GONE, "Context Pack 已过期");
		}
		if (!contextPack.identityFingerprint().equals(orchestrator.fingerprint(identity))) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "身份或数据范围已变化，请重新提问");
		}
		return contextPack;
	}

	public CitationResolution resolveCitation(IdentityEnvelope identity, String sessionId, String contextPackId,
			String citationId) {
		AssistantContextPack contextPack = contextPack(identity, sessionId, contextPackId);
		ContextFragment fragment = contextPack.fragments()
			.stream()
			.filter(item -> item.citation() != null && citationId.equals(item.citation().citationId()))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "引用不存在"));
		return orchestrator.resolveCitation(identity, fragment.citation());
	}

	public MessageExchange send(IdentityEnvelope identity, String sessionId, SendMessageRequest request) {
		if (request == null || request.content() == null || request.content().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
		}
		if (request.explicitSkillCodes().size() > 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKILL_SELECTION_LIMIT_EXCEEDED");
		}
		AssistantSession session = requireOwned(identity, sessionId);
		requireActive(session);
		NormalizedSourceSelection sourceSelection = normalizeSourceSelection(request.sourceMode(),
				request.enabledScopes(), session.activeScopes());
		List<ProviderId> scopes = validateProviderSelection(identity, sourceSelection.providerIds());
		String clientMessageId = request.clientMessageId() == null || request.clientMessageId().isBlank()
				? UUID.randomUUID().toString() : request.clientMessageId();
		if (request.sourceMode() == null) {
			runEngine.startOrGet(identity, session, clientMessageId, request.content(), scopes,
					sourceSelection.providerSelectionExplicit(), request.scopeHint(), request.explicitSkillCodes(),
					null, null, null);
		}
		else {
			runEngine.startOrGet(identity, session, clientMessageId, request.content(), scopes,
					sourceSelection.providerSelectionExplicit(), request.scopeHint(), request.explicitSkillCodes(),
					null, null, null, List.of(), sourceSelection.sourceMode());
		}
		long timeoutSeconds = Math.max(5, Math.min(blockingTimeoutSeconds, 300));
		return runEngine.awaitExchange(sessionId, clientMessageId, java.time.Duration.ofSeconds(timeoutSeconds));
	}

	public RunAcceptedResponse createRun(IdentityEnvelope identity, String sessionId, CreateRunRequest request) {
		if (request != null && request.clarificationSelection() != null && request.sourceMode() != SourceMode.AUTO)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CLARIFICATION_SELECTION_REQUIRES_AUTO");
		if (request == null || request.content() == null || request.content().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
		}
		AssistantSession session = requireOwned(identity, sessionId);
		requireActive(session);
		NormalizedSourceSelection sourceSelection = normalizeSourceSelection(request.sourceMode(),
				request.providerCodes(), session.activeScopes());
		List<ProviderId> selected = validateProviderSelection(identity, sourceSelection.providerIds());
		List<AssistantAttachment> attachments = request.attachmentUploadIds().isEmpty() ? List.of()
				: requireAttachmentService().resolve(identity, request.attachmentUploadIds());
		RunAcceptedResponse accepted;
		if (request.sourceMode() == null) {
			accepted = runEngine.startOrGet(identity, session, request.clientMessageId(), request.content(), selected,
					sourceSelection.providerSelectionExplicit(), request.scopeHint(),
					request.skillId() == null ? List.of() : List.of(request.skillId()), request.temperature(),
					request.topP(), request.maxOutputTokens(), attachments);
		}
		else {
			accepted = runEngine.startOrGet(identity, session, request.clientMessageId(), request.content(), selected,
					sourceSelection.providerSelectionExplicit(), request.scopeHint(),
					request.skillId() == null ? List.of() : List.of(request.skillId()), request.temperature(),
					request.topP(), request.maxOutputTokens(), attachments, sourceSelection.sourceMode(),
					request.clarificationSelection());
		}
		if (!attachments.isEmpty()) {
			requireAttachmentService().markConsumed(identity, attachments);
		}
		return accepted;
	}

	private AssistantAttachmentService requireAttachmentService() {
		if (attachmentService == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ATTACHMENT_SERVICE_UNAVAILABLE");
		}
		return attachmentService;
	}

	public List<RunSnapshot> recentRuns(IdentityEnvelope identity, String sessionId) {
		requireOwned(identity, sessionId);
		return runEngine.recentRuns(sessionId, 20).stream().map(run -> snapshot(identity, run)).toList();
	}

	public List<RunSnapshot> runs(IdentityEnvelope identity, String sessionId) {
		requireOwned(identity, sessionId);
		return runEngine.runs(sessionId, 200).stream().map(run -> snapshot(identity, run)).toList();
	}

	public RunSnapshot runSnapshot(IdentityEnvelope identity, String sessionId, String runId) {
		requireOwned(identity, sessionId);
		AssistantRunRecord run = runEngine.requireOwnedRun(identity, sessionId, runId);
		return snapshot(identity, run);
	}

	private RunSnapshot snapshot(IdentityEnvelope identity, AssistantRunRecord run) {
		return new RunSnapshot(run.runId(), run.sessionId(), run.status(), run.draftContent(), run.lastEventSeq(),
				run.responseMessageId(), run.input().content(), run.input().providerIds(),
				run.input().selectedSkillId(), run.contextPackId(), run.errorCode(), run.errorMessageSafe(),
				run.createdAt(), run.startedAt(), run.completedAt(), run.input().attachments(),
				run.input().effectiveSourceMode(), run.effectivePhase(),
				run.executionPlan() == null ? null : run.executionPlan().planDigest(),
				runEngine.stepTraces(run.runId()),
				run.executionPlan() == null ? List.of() : run.executionPlan().reasonCodes(), run.routeDecision(),
				runEngine.visibleClarification(identity, run), run.input().scopeHint(),
				run.input().clarificationSelection());
	}

	public CancelRunResponse cancelRun(IdentityEnvelope identity, String sessionId, String runId) {
		requireOwned(identity, sessionId);
		return runEngine.requestCancel(identity, sessionId, runId);
	}

	private AssistantSession requireOwned(IdentityEnvelope identity, String sessionId) {
		AssistantSession session = repository.findSession(sessionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
		if (!session.tenantId().equals(identity.tenantId()) || !session.ownerUserId().equals(identity.userId()) || !session.ownerSubjectType().equals(identity.subjectType())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
		}
		return session;
	}

	private AssistantSession updateStatus(IdentityEnvelope identity, String sessionId,
			UpdateSessionStatusRequest request, SessionStatus targetStatus) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedVersion 不能为空");
		}
		AssistantSession current = requireOwned(identity, sessionId);
		if (current.status() == targetStatus) {
			return current;
		}
		Instant archivedAt = targetStatus == SessionStatus.ARCHIVED ? Instant.now() : null;
		return repository
			.updateStatus(sessionId, identity.tenantId(), identity.userId(), targetStatus, archivedAt,
					request.expectedVersion(), identity.subjectType())
			.orElseThrow(this::versionConflict);
	}

	private void requireActive(AssistantSession session) {
		if (session.status() != SessionStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_ARCHIVED");
		}
	}

	private ResponseStatusException versionConflict() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_VERSION_CONFLICT");
	}

	private String normalizeTitle(String title) {
		String normalized = title == null ? "" : title.replaceAll("\\s+", " ").trim();
		int length = normalized.codePointCount(0, normalized.length());
		if (length < 1 || length > 80) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题长度必须为 1 到 80 个字符");
		}
		return normalized;
	}

	private String normalizeKeyword(String keyword) {
		String normalized = keyword == null ? null : keyword.trim();
		return normalized == null || normalized.isEmpty() ? null : normalized;
	}

	private String encodeCursor(AssistantSessionSummary summary) {
		String value = summary.lastMessageAt() + "|" + summary.assistantSessionId();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private SessionCursor decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = decoded.split("\\|", 2);
			if (parts.length != 2 || parts[1].isBlank()) {
				throw new IllegalArgumentException("invalid cursor");
			}
			return new SessionCursor(Instant.parse(parts[0]), parts[1]);
		}
		catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话游标无效");
		}
	}

	private record SessionCursor(Instant lastMessageAt, String sessionId) {
	}

	private List<ProviderId> validateProviderSelection(IdentityEnvelope identity, List<ProviderId> selected) {
		if (orchestrator == null) {
			return List.copyOf(selected == null ? List.of() : selected);
		}
		try {
			return orchestrator.validateProviderSelection(identity, selected == null ? List.of() : selected);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private NormalizedSourceSelection normalizeSourceSelection(
			com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode sourceMode,
			List<ProviderId> requested, List<ProviderId> active) {
		try {
			return sourceSelectionNormalizer.normalize(sourceMode, requested, active);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

}
