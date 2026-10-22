package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Authenticated management only; no AI tool registration, tenant/actor fields or client
 * pass flags.
 */
@RestController
@RequestMapping("/data-query")
@ConditionalOnProperty(prefix = "ai.dataquery.persistence", name = "enabled", havingValue = "true")
public final class DataQueryCorrectionController {

	private final DataQueryCorrectionService service;

	public DataQueryCorrectionController(DataQueryCorrectionService service) {
		this.service = service;
	}

	public interface StrictRequest {

		@JsonAnySetter
		default void rejectUnknown(String key, Object value) {
			throw new IllegalArgumentException("UNKNOWN_REQUEST_FIELD");
		}

	}

	public record FeedbackRequest(String queryId, DataQueryCorrectionService.Category category, String description,
			String requestId) implements StrictRequest {
	}

	public record DraftRequest(String feedbackId, List<String> intentTerms, String expectedMonth,
			String correctionKind) implements StrictRequest {
	}

	public record EditRequest(int revision, List<String> intentTerms, String expectedMonth,
			String correctionKind) implements StrictRequest {
	}

	public record RevisionRequest(int revision) implements StrictRequest {
	}

	public record PublishRequest(int revision, String reason) implements StrictRequest {
	}

	public record RollbackRequest(String expectedActiveVersion, String targetVersion,
			String reason) implements StrictRequest {
	}

	@PostMapping("/feedback")
	public Map<String, Object> feedback(@RequestBody FeedbackRequest request) {
		return Map.of("feedbackId", service.feedback(DataQuerySecurity.identity(), request.queryId(),
				request.category(), request.description(), null, request.requestId()));
	}

	@GetMapping("/manage/workbench")
	public DataQueryCorrectionService.Workbench workbench() {
		return service.workbench(DataQuerySecurity.managementActor());
	}

	public record InitialRequest(String dataId, String definitionDigest, Boolean acknowledged, String expectedMonth,
			String reason) implements StrictRequest {
	}

	@PostMapping("/manage/initial-drafts")
	public Map<String, Object> initial(@RequestBody InitialRequest request) {
		return Map.of("ruleVersion",
				service.initialDraft(DataQuerySecurity.managementActor(), request.dataId(), request.definitionDigest(),
						Boolean.TRUE.equals(request.acknowledged()), request.expectedMonth(), request.reason()),
				"revision", 1);
	}

	@PostMapping("/manage/drafts")
	public Map<String, Object> draft(@RequestBody DraftRequest request) {
		return Map.of("ruleVersion", service.draft(DataQuerySecurity.managementActor(), request.feedbackId(),
				request.intentTerms(), request.expectedMonth(), request.correctionKind()), "revision", 1);
	}

	@GetMapping("/manage/drafts/{version}")
	public Map<String, Object> inspect(@PathVariable String version) {
		return service.inspect(DataQuerySecurity.managementActor(), version);
	}

	@PutMapping("/manage/drafts/{version}")
	public Map<String, Object> edit(@PathVariable String version, @RequestBody EditRequest request) {
		service.edit(DataQuerySecurity.managementActor(), version, request.revision(), request.intentTerms(),
				request.expectedMonth(), request.correctionKind());
		return Map.of("revision", request.revision() + 1);
	}

	@PostMapping("/manage/drafts/{version}/verify")
	public Map<String, Object> verify(@PathVariable String version, @RequestBody RevisionRequest request) {
		return Map.of("report", service.verify(DataQuerySecurity.managementActor(), version, request.revision()));
	}

	@PostMapping("/manage/drafts/{version}/approve")
	public Map<String, Object> approve(@PathVariable String version, @RequestBody RevisionRequest request) {
		service.approve(DataQuerySecurity.managementActor(), version, request.revision());
		return Map.of("status", "APPROVED");
	}

	@PostMapping("/manage/drafts/{version}/publish")
	public Map<String, Object> publish(@PathVariable String version, @RequestBody PublishRequest request) {
		service.publish(DataQuerySecurity.managementActor(), version, request.revision(), request.reason());
		return Map.of("status", "PUBLISHED");
	}

	@PostMapping("/manage/rollback")
	public Map<String, Object> rollback(@RequestBody RollbackRequest request) {
		service.rollback(DataQuerySecurity.managementActor(), request.expectedActiveVersion(), request.targetVersion(),
				request.reason());
		return Map.of("status", "ROLLED_BACK");
	}

	@GetMapping("/manage/technical-experiences")
	public Map<String, Object> technicalExperiences() {
		return service.technicalExperiences(DataQuerySecurity.managementActor());
	}

	@PostMapping("/manage/technical-experiences/{version}/disable")
	public Map<String, Object> disableTechnicalExperience(@PathVariable String version,
			@RequestBody java.util.Map<String, Object> request) {
		if (!request.keySet().equals(java.util.Set.of("reason")) || !(request.get("reason") instanceof String reason))
			throw new DataQueryException("INVALID_REQUEST");
		service.disableTechnicalExperience(DataQuerySecurity.managementActor(), version, reason);
		return Map.of("status", "DISABLED");
	}

	@ExceptionHandler(DataQueryException.class)
	public ResponseEntity<Map<String, Object>> failure(DataQueryException e) {
		return DataQueryResponses.failure(e);
	}

	@ExceptionHandler(org.springframework.dao.DataAccessException.class)
	public ResponseEntity<Map<String, Object>> storageUnavailable() {
		return ResponseEntity.status(503).body(Map.of("code", "SETUP_STORAGE_UNAVAILABLE"));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> malformed() {
		return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
	}

}
