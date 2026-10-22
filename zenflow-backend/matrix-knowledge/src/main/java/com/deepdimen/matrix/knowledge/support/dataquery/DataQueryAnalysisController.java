package com.deepdimen.matrix.knowledge.support.dataquery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.Map;

@RestController
@RequestMapping("/data-query/queries/{queryId}/analysis")
@ConditionalOnProperty(prefix = "ai.dataquery", name = { "enabled", "persistence.enabled" }, havingValue = "true")
public class DataQueryAnalysisController {

	private final DataQueryResultAnalysis service;

	public DataQueryAnalysisController(DataQueryResultAnalysis service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<DataQueryResultAnalysis.View> read(@PathVariable String queryId) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(service.read(DataQuerySecurity.identity(), queryId));
	}

	@com.deepdimen.matrix.common.api.encrypt.annotation.NoEncrypt
	@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<Flux<ServerSentEvent<DataQueryResultAnalysis.Event>>> generate(@PathVariable String queryId,
			@RequestBody DataQueryResultAnalysis.Request request) {
		var owner = DataQuerySecurity.identity();
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header("X-Accel-Buffering", "no")
			.body(service.start(owner, queryId, request).map(event -> ServerSentEvent.builder(event).build()));
	}

	@DeleteMapping("/{requestId}")
	public ResponseEntity<DataQueryResultAnalysis.View> cancel(@PathVariable String queryId,
			@PathVariable String requestId) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(service.cancel(DataQuerySecurity.identity(), queryId, requestId));
	}

	@ExceptionHandler(DataQueryException.class)
	public ResponseEntity<Map<String, Object>> failure(DataQueryException error) {
		String code = error.getMessage();
		int status = switch (code) {
			case "UNAUTHENTICATED" -> 401;
			case "QUERY_NOT_FOUND" -> 404;
			case "AUTHORIZATION_CHANGED", "DATASET_ACCESS_DENIED", "ANALYSIS_SOURCE_CHANGED" -> 403;
			case "ANALYSIS_CHANGED", "ANALYSIS_IN_PROGRESS", "ANALYSIS_REQUEST_CHANGED", "ANALYSIS_BUSY" -> 409;
			case "INVALID_REQUEST" -> 400;
			default -> 422;
		};
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code", code));
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<Map<String, Object>> unavailable() {
		return ResponseEntity.status(503)
			.cacheControl(CacheControl.noStore())
			.body(Map.of("code", "ANALYSIS_UNAVAILABLE"));
	}

}
