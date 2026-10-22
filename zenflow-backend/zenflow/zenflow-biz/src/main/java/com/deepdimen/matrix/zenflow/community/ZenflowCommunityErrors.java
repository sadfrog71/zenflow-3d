package com.deepdimen.matrix.zenflow.community;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
/** Submitted text, image data and SQL parameters must never appear in error responses. */
@RestControllerAdvice(assignableTypes={ZenflowCommunityController.class,ZenflowCommunityAdminController.class})
public class ZenflowCommunityErrors {
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<Map<String,String>> request(ResponseStatusException error){String reason=error.getReason();return ResponseEntity.status(error.getStatusCode()).body(Map.of("errorCode",reason!=null&&reason.matches("[A-Z_]{1,80}")?reason:"COMMUNITY_REQUEST_FAILED"));}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class)
 public ResponseEntity<Map<String,String>> storage(org.springframework.dao.DataAccessException error){return ResponseEntity.status(503).body(Map.of("errorCode","COMMUNITY_STORAGE_UNAVAILABLE"));}
}
