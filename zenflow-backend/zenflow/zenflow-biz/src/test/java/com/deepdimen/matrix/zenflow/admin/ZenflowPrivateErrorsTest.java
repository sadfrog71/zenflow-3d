package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.zenflow.capsule.ZenflowNoteErrors;
import org.junit.jupiter.api.Test;

class ZenflowPrivateErrorsTest {
    @Test void storageFailuresDoNotMasqueradeAsConflictOrExposePrivateText() {
        var errors=new ZenflowNoteErrors();String sentinel="private-sentinel-secret";
        var failure=errors.storage(new org.springframework.dao.DataIntegrityViolationException(sentinel));
        assertEquals(503,failure.getStatusCode().value());assertFalse(failure.getBody().toString().contains(sentinel));
        assertEquals(503,errors.conflict(new IllegalStateException(sentinel)).getStatusCode().value());
        assertEquals(409,errors.conflict(new IllegalStateException("胶囊已被更新，请刷新后重试")).getStatusCode().value());
        assertEquals(404,errors.invalid(new IllegalArgumentException("胶囊不存在或无权访问")).getStatusCode().value());
    }
}
