package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/data-query/queries")
@ConditionalOnProperty(prefix = "ai.dataquery", name = { "enabled", "persistence.enabled" }, havingValue = "true")
public class DataQueryExecutionController {

	private final DataQueryExecutionDetails details;

	private final DataQueryVisualizations visualizations;

	public DataQueryExecutionController(DataQueryMetadataRepository metadata, ObjectMapper json) {
		details = new DataQueryExecutionDetails(metadata, json);
		visualizations = metadata.visualizations();
	}

	@GetMapping("/{queryId}/execution")
	public ResponseEntity<DataQueryExecutionDetails.Detail> execution(@PathVariable String queryId) {
		return ResponseEntity.ok()
			.cacheControl(org.springframework.http.CacheControl.noStore())
			.body(details.read(DataQuerySecurity.identity(), queryId));
	}

	@GetMapping("/{queryId}/visualization")
	public ResponseEntity<DataQueryVisualizations.View> visualization(@PathVariable String queryId) {
		return ResponseEntity.ok()
			.cacheControl(org.springframework.http.CacheControl.noStore())
			.body(visualizations.read(DataQuerySecurity.identity(), queryId));
	}

	@PutMapping("/{queryId}/visualization")
	public ResponseEntity<DataQueryVisualizations.Saved> saveVisualization(@PathVariable String queryId,
			@RequestBody DataQueryVisualizations.Update update) {
		return ResponseEntity.ok()
			.cacheControl(org.springframework.http.CacheControl.noStore())
			.body(visualizations.update(DataQuerySecurity.identity(), queryId, update));
	}

	@ExceptionHandler(DataQueryException.class)
	public ResponseEntity<Map<String, Object>> failure(DataQueryException error) {
		return DataQueryResponses.failure(error);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<Map<String, Object>> unavailable() {
		return ResponseEntity.status(503).body(Map.of("code", "QUERY_DETAILS_UNAVAILABLE"));
	}

}
