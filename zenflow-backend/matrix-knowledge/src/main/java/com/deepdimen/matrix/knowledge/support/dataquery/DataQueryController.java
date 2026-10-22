package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQueryResponses.envelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * No @Inner or anonymous bypass. Identity comes only from MATRIX's authenticated
 * principal.
 */
@RestController
@RequestMapping("/data-query")
@ConditionalOnProperty(prefix = "ai.dataquery", name = "enabled", havingValue = "true")
public class DataQueryController {

	private final ControlledQueryService service;

	public DataQueryController(ControlledQueryService service) {
		this.service = service;
	}

	public record CandidateRequest(String dataset, String metric, String dimension, LocalDate from, LocalDate to,
			String timezone, String order, Map<String, Object> filters) {
		@JsonAnySetter
		public void rejectUnknown(String name, Object value) {
			throw new IllegalArgumentException("UNKNOWN_REQUEST_FIELD");
		}
	}

	public record ExecuteRequest(String planHandle) {
		@JsonAnySetter
		public void rejectUnknown(String name, Object value) {
			throw new IllegalArgumentException("UNKNOWN_REQUEST_FIELD");
		}
	}

	@PostMapping("/validate")
	public Map<String, Object> validate(@RequestBody CandidateRequest request) {
		var identity = identity();
		if (request.filters() == null || !request.filters().isEmpty()) {
			throw new DataQueryException("UNSUPPORTED_FILTERS");
		}
		var candidate = new PatrolQueryCompiler.Candidate(request.dataset(), request.metric(), request.dimension(),
				request.from(), request.to(), request.timezone(), request.order());
		var validated = service.validate(identity, candidate);
		var response = envelope("READY", "PLAN_VALIDATED", "查询计划已校验");
		response.put("planHandle", validated.planHandle());
		response.put("expiresAt", validated.expiresAt().toString());
		response.put("ruleVersion", validated.ruleVersion());
		return response;
	}

	@PostMapping("/execute")
	public Map<String, Object> execute(@RequestBody ExecuteRequest request) {
		var identity = identity();
		if (request.planHandle() == null || !request.planHandle().matches("[A-Za-z0-9_-]{43}")) {
			throw new DataQueryException("INVALID_PLAN_HANDLE");
		}
		var result = service.execute(identity, request.planHandle());
		return DataQueryResponses.success(result);
	}

	@ExceptionHandler(DataQueryException.class)
	public ResponseEntity<Map<String, Object>> controlledFailure(DataQueryException exception) {
		return DataQueryResponses.failure(exception);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> malformedRequest() {
		return ResponseEntity.badRequest().body(envelope("REJECTED", "INVALID_REQUEST", "请求格式不正确或包含不支持的字段。"));
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<Map<String, Object>> infrastructureFailure() {
		return ResponseEntity.status(503).body(envelope("ERROR", "SERVICE_UNAVAILABLE", "查询服务暂不可用。"));
	}

	private static ControlledQueryService.Identity identity() {
		return DataQuerySecurity.identity();
	}

}
