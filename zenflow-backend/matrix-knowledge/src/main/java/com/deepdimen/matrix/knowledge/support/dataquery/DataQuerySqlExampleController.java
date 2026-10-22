package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQueryCorrectionController.StrictRequest;

/** Management only. Identity comes from MATRIX; never registered as a model tool. */
@RestController
@RequestMapping("/data-query/manage/sql-examples")
@ConditionalOnProperty(prefix = "ai.dataquery.sql-examples", name = "enabled", havingValue = "true")
public class DataQuerySqlExampleController {

	private final DataQuerySqlExamples service;

	public DataQuerySqlExampleController(DataQuerySqlExamples service) {
		this.service = service;
	}

	public record Create(DataQuerySqlExamples.Input input, String sourceQueryId) implements StrictRequest {
	}

	public record Edit(int revision, DataQuerySqlExamples.Input input) implements StrictRequest {
	}

	public record Revision(int revision) implements StrictRequest {
	}

	public record Reason(int revision, String reason) implements StrictRequest {
	}

	public record Rollback(int revision, String targetVersion, String reason) implements StrictRequest {
	}

	@GetMapping
	public DataQuerySqlExamples.Catalog list(@RequestParam(required = false) String dataId) {
		return service.list(DataQuerySecurity.managementActor(), dataId);
	}

	@GetMapping("/datasets")
	public java.util.List<DataQueryMetadataRepository.ExampleDataset> datasets() {
		return service.datasets(DataQuerySecurity.managementActor());
	}

	@GetMapping("/selection")
	public DataQuerySqlExamples.Selection selection(@RequestParam String dataId) {
		return service.selection(DataQuerySecurity.managementActor(), dataId);
	}

	@PutMapping("/selection")
	@com.deepdimen.matrix.common.log.annotation.SysLog("修改SQL示例候选集合")
	public DataQuerySqlExamples.Selection select(@RequestBody DataQuerySqlExamples.Selection request) {
		return service.selectReferences(DataQuerySecurity.managementActor(), request);
	}

	@GetMapping("/seed/{queryId}")
	public Map<String, Object> seed(@PathVariable String queryId) {
		return service.seed(DataQuerySecurity.managementActor(), queryId);
	}

	@PostMapping
	public DataQuerySqlExamples.Detail create(@RequestBody Create request) {
		return service.create(DataQuerySecurity.managementActor(), request.input(), request.sourceQueryId());
	}

	@GetMapping("/{version}")
	public DataQuerySqlExamples.Detail inspect(@PathVariable String version) {
		return service.inspect(DataQuerySecurity.managementActor(), version);
	}

	@PutMapping("/{version}")
	public DataQuerySqlExamples.Detail edit(@PathVariable String version, @RequestBody Edit request) {
		return service.edit(DataQuerySecurity.managementActor(), version, request.revision(), request.input());
	}

	@PostMapping("/{version}/revise")
	public DataQuerySqlExamples.Detail revise(@PathVariable String version, @RequestBody Revision request) {
		return service.revise(DataQuerySecurity.managementActor(), version, request.revision());
	}

	@PostMapping("/{version}/verify")
	public DataQuerySqlExamples.Detail verify(@PathVariable String version, @RequestBody Revision request) {
		return service.verify(DataQuerySecurity.managementActor(), version, request.revision());
	}

	@PostMapping("/{version}/approve")
	public DataQuerySqlExamples.Detail approve(@PathVariable String version, @RequestBody Revision request) {
		return service.approve(DataQuerySecurity.managementActor(), version, request.revision());
	}

	@PostMapping("/{version}/publish")
	public DataQuerySqlExamples.Detail publish(@PathVariable String version, @RequestBody Reason request) {
		return service.publish(DataQuerySecurity.managementActor(), version, request.revision(), request.reason());
	}

	@PostMapping("/{version}/disable")
	public DataQuerySqlExamples.Detail disable(@PathVariable String version, @RequestBody Reason request) {
		return service.disable(DataQuerySecurity.managementActor(), version, request.revision(), request.reason());
	}

	@PostMapping("/{version}/rollback")
	public DataQuerySqlExamples.Detail rollback(@PathVariable String version, @RequestBody Rollback request) {
		return service.rollback(DataQuerySecurity.managementActor(), version, request.revision(),
				request.targetVersion(), request.reason());
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
