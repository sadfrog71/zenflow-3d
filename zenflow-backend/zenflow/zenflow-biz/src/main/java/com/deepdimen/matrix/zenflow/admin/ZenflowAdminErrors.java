package com.deepdimen.matrix.zenflow.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Return bounded product codes, never upstream responses or credential-bearing exceptions. */
@RestControllerAdvice(assignableTypes={ZenflowAdminController.class,ZenflowCompanionController.class,ZenflowUserController.class})
public class ZenflowAdminErrors {
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String,String>> storage(org.springframework.dao.DataAccessException error) {
        return ResponseEntity.status(503).body(Map.of("errorCode","ADMIN_STORAGE_UNAVAILABLE"));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException error) {
        String reason=error.getMessage();String code=reason!=null && reason.matches("[A-Z_]{1,80}")?reason:"ADMIN_REQUEST_INVALID";
        return ResponseEntity.badRequest().body(Map.of("errorCode",code));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("errorCode","ADMIN_STATE_CONFLICT"));
    }
    @ExceptionHandler(com.deepdimen.matrix.assistant.generation.AssistantModelException.class)
    public ResponseEntity<Map<String,String>> upstream(com.deepdimen.matrix.assistant.generation.AssistantModelException error) {
        return ResponseEntity.status(503).body(Map.of("errorCode",error.errorCode()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> failure(ResponseStatusException error) {
        String reason=error.getReason();
        String code=reason!=null && reason.matches("[A-Z_]{1,80}")?reason:"ADMIN_REQUEST_FAILED";
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("errorCode",code));
    }
}
