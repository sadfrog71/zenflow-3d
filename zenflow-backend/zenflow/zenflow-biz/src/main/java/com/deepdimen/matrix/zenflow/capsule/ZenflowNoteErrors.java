package com.deepdimen.matrix.zenflow.capsule;

import java.sql.SQLException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes={ZenflowNoteController.class,ZenflowMemoryController.class,ZenflowPreferencesController.class,com.deepdimen.matrix.zenflow.conversation.ZenflowConversationController.class,com.deepdimen.matrix.zenflow.conversation.ZenflowRunController.class})
public class ZenflowNoteErrors {
    private static final Logger LOG=LoggerFactory.getLogger(ZenflowNoteErrors.class);
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> response(ResponseStatusException error) {
        String reason=error.getReason();
        return failure(error.getStatusCode().value(),reason!=null && reason.matches("[A-Z_]{1,80}")?reason:"NOTE_REQUEST_FAILED");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> invalid(IllegalArgumentException error) {
        if("胶囊不存在或无权访问".equals(error.getMessage()))return failure(404,"NOTE_NOT_FOUND");
        diagnostic(error);return failure(503,"NOTE_STORAGE_UNAVAILABLE");
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,Object>> conflict(IllegalStateException error) {
        if("胶囊已被更新，请刷新后重试".equals(error.getMessage()))return failure(409,"VERSION_CONFLICT");
        diagnostic(error);return failure(503,"NOTE_STORAGE_UNAVAILABLE");
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,Object>> storage(DataAccessException error) {
        diagnostic(error);return failure(503,"NOTE_STORAGE_UNAVAILABLE");
    }
    private void diagnostic(Exception error) {
        Throwable root=error;
        for(int i=0;i<12 && root.getCause()!=null;i++)root=root.getCause();
        String state=root instanceof SQLException sql?sql.getSQLState():"";
        int number=root instanceof SQLException sql?sql.getErrorCode():0;
        // Exception messages and SQL parameters can include private text, so neither is logged.
        LOG.warn("Private storage failed; type={}, sqlState={}, sqlCode={}",root.getClass().getSimpleName(),state,number);
    }
    private ResponseEntity<Map<String,Object>> failure(int status,String code) {
        return ResponseEntity.status(status).body(Map.of("code",1,"msg","操作未完成","data",Map.of("errorCode",code)));
    }
}
