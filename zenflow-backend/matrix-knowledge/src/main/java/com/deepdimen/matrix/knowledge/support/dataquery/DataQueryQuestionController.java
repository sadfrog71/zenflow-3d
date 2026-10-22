package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

/**
 * Thin adapter to the SAME published-rule interpreter used by MATRIX chat; no model SQL
 * or new kernel.
 */
@RestController
@RequestMapping("/data-query")
@ConditionalOnProperty(prefix = "ai.dataquery", name = { "enabled", "persistence.enabled" }, havingValue = "true")
public final class DataQueryQuestionController {

	private final DataQueryQuestionService questions;

	public DataQueryQuestionController(DataQueryQuestionService questions) {
		this.questions = questions;
	}

	public record QuestionRequest(Long dataId, String question) {
		@JsonAnySetter
		public void rejectUnknown(String key, Object value) {
			throw new IllegalArgumentException("UNKNOWN_REQUEST_FIELD");
		}
	}

	@PostMapping("/question")
	public Map<String, Object> question(@RequestBody QuestionRequest request) {
		var identity = DataQuerySecurity.identity();
		if (request.dataId() == null || request.dataId() <= 0)
			throw new DataQueryException("INVALID_REQUEST");
		return questions.ask(identity, request.dataId(), request.question(), null);
	}

	@ExceptionHandler(DataQueryException.class)
	public ResponseEntity<Map<String, Object>> failure(DataQueryException e) {
		return DataQueryResponses.failure(e);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> invalid() {
		return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
	}

}
