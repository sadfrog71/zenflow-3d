package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

/**
 * No client tenant, actor, approval timestamp, connection credentials, SQL or environment
 * writes.
 */
@RestController
@RequestMapping("/data-query/setup")
public final class DataQuerySetupController {

	private final DataQuerySetupService service;

	public DataQuerySetupController(DataQuerySetupService service) {
		this.service = service;
	}

	public record BindingRequest(String expectedRevision, String expectedFingerprint, Boolean enabled,
			String reason) implements DataQueryCorrectionController.StrictRequest {
	}

	public record AccessRequest(String expectedRevision, List<DataQueryAccessRepository.Target> targets,
			String validUntil, String reason) implements DataQueryCorrectionController.StrictRequest {
	}

	@GetMapping("/{dataId}")
	public Map<String, Object> view(@PathVariable String dataId) {
		return service.view(DataQuerySecurity.managementActor(), dataId);
	}

	@PutMapping("/{dataId}/binding")
	public DataQuerySetupService.Binding binding(@PathVariable String dataId, @RequestBody BindingRequest request) {
		return service.saveBinding(DataQuerySecurity.managementActor(), dataId, request.expectedRevision(),
				request.expectedFingerprint(), request.enabled(), request.reason());
	}

	@GetMapping("/{dataId}/users")
	public List<DataQuerySetupService.UserOption> users(@PathVariable String dataId,
			@RequestParam(defaultValue = "") String search) {
		return service.users(DataQuerySecurity.managementActor(), dataId, search);
	}

	@GetMapping("/{dataId}/access")
	public DataQueryAccessRepository.Access access(@PathVariable String dataId) {
		return service.access(DataQuerySecurity.managementActor(), dataId);
	}

	@PutMapping("/{dataId}/access")
	public DataQueryAccessRepository.Access access(@PathVariable String dataId, @RequestBody AccessRequest request) {
		return service.saveAccess(DataQuerySecurity.managementActor(), dataId, request.expectedRevision(),
				request.targets(), request.validUntil(), request.reason());
	}

	@RequestMapping(value = "/{dataId}/grants/{userId}", method = { RequestMethod.GET, RequestMethod.PUT })
	public Object retiredGrants(@PathVariable String dataId, @PathVariable String userId) {
		return service.retiredGrants(DataQuerySecurity.managementActor(), dataId);
	}

	@ExceptionHandler(DataQueryException.class)
	public ResponseEntity<Map<String, Object>> failure(DataQueryException ex) {
		return DataQueryResponses.failure(ex);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> malformed() {
		return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
	}

	@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> conflict() {
		return ResponseEntity.status(409).body(Map.of("code", "SETUP_CONFLICT"));
	}

	@ExceptionHandler(org.springframework.dao.DataAccessException.class)
	public ResponseEntity<Map<String, Object>> unavailable() {
		return ResponseEntity.status(503).body(Map.of("code", "SETUP_STORAGE_UNAVAILABLE"));
	}

}
