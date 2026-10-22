package com.deepdimen.matrix.capsule.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.deepdimen.matrix.common.security.annotation.HasPermission;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;

class CapsuleControllerContractTest {

    @Test
    void overviewRequiresTheDedicatedTenantAggregatePermission() throws NoSuchMethodException {
        Method overview = CapsuleManageController.class.getMethod("overview");
        HasPermission permission = overview.getAnnotation(HasPermission.class);

        assertNotNull(permission);
        assertEquals(1, permission.value().length);
        assertEquals("capsule_manage_view", permission.value()[0]);
    }

    @Test
    void capsuleControllerDoesNotExposeDeleteActions() {
        assertFalse(Arrays.stream(CapsuleController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(DeleteMapping.class)));
    }
}
