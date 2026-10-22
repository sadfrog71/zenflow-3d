package com.deepdimen.matrix.zenflow.books;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowModelGateway;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ZenflowBookAnnotationLimitTest {
	private final ZenflowBookAdmin admin = new ZenflowBookAdmin(mock(JdbcTemplate.class),
		new ZenflowProperties(), mock(ZenflowAdminAccess.class), mock(ZenflowModelGateway.class),
		mock(PlatformTransactionManager.class), new ObjectMapper());

	@AfterEach
	void close() { admin.close(); }

	@Test
	void completeClassicalChapterCanKeepMoreThanTwoHundredAnnotations() {
		var notes = Collections.nCopies(219, Map.of("phrase", "須菩提", "note", "佛弟子", "tone", "word"));
		List<?> accepted = ReflectionTestUtils.invokeMethod(admin, "annotations", notes);
		assertNotNull(accepted);
		assertEquals(219, accepted.size());
	}

	@Test
	void annotationCountRemainsBounded() {
		var notes = Collections.nCopies(1001, Map.of("phrase", "須菩提", "note", "佛弟子"));
		assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(admin, "annotations", notes));
	}
}
