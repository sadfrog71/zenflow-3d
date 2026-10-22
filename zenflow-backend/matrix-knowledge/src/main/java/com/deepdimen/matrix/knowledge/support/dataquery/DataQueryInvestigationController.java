package com.deepdimen.matrix.knowledge.support.dataquery;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/data-query/manage/investigations")
@ConditionalOnProperty(prefix = "ai.dataquery.persistence", name = "enabled", havingValue = "true")
final class DataQueryInvestigationController {

	private final ObjectProvider<DataQueryInvestigationService> provider;

	private final DataQueryMetadataRepository metadata;

	DataQueryInvestigationController(ObjectProvider<DataQueryInvestigationService> provider,
			DataQueryMetadataRepository metadata) {
		this.provider = provider;
		this.metadata = metadata;
	}

	record Start(String feedbackId,
			DataQueryRelation.Definition definition) implements DataQueryCorrectionController.StrictRequest {
	}

	record Edit(int revision, String businessBasisType,
			String businessBasis) implements DataQueryCorrectionController.StrictRequest {
	}

	@GetMapping("/capability")
	Map<String, Object> capability() {
		DataQuerySecurity.managementActor().requireRead();
		return Map.of("enabled", provider.getIfAvailable() != null, "kind", DataQueryRelation.KIND,
				"humanPublicationRequired", true);
	}

	@PostMapping
	Map<String, Object> start(@RequestBody Start request) {
		return service().start(DataQuerySecurity.managementActor(), request.feedbackId(), request.definition());
	}

	@GetMapping
	List<Map<String, Object>> list(@RequestParam String feedbackId) {
		return service().list(DataQuerySecurity.managementActor(), feedbackId);
	}

	@GetMapping("/{id}")
	Map<String, Object> read(@PathVariable String id) {
		return service().read(DataQuerySecurity.managementActor(), id);
	}

	@PostMapping("/{id}/draft")
	Map<String, Object> prepare(@PathVariable String id) {
		return Map.of("ruleVersion", service().prepare(DataQuerySecurity.managementActor(), id), "revision", 1);
	}

	@PutMapping("/drafts/{version}")
	Map<String, Object> edit(@PathVariable String version, @RequestBody Edit request) {
		service();
		metadata.relations()
			.edit(DataQuerySecurity.managementActor(), version, request.revision(), request.businessBasisType(),
					request.businessBasis());
		return Map.of("revision", request.revision() + 1);
	}

	private DataQueryInvestigationService service() {
		var service = provider.getIfAvailable();
		if (service == null)
			throw new DataQueryException("INVESTIGATION_DISABLED");
		return service;
	}

	@ExceptionHandler(DataQueryException.class)
	ResponseEntity<Map<String, Object>> failure(DataQueryException e) {
		return DataQueryResponses.failure(e);
	}

	@ExceptionHandler(org.springframework.dao.DataAccessException.class)
	ResponseEntity<Map<String, Object>> unavailable() {
		return ResponseEntity.status(503).body(Map.of("code", "SETUP_STORAGE_UNAVAILABLE"));
	}

	@ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, Object>> malformed() {
		return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
	}

}
