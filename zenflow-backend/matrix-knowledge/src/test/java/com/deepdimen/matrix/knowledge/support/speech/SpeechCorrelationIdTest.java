package com.deepdimen.matrix.knowledge.support.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeechCorrelationIdTest {

	@Test
	void preservesABoundedSafeClientCorrelationId() {
		assertEquals("mobile-correlation_123", SpeechCorrelationId.resolve("mobile-correlation_123"));
	}

	@Test
	void replacesMissingOrUnsafeValues() {
		String missing = SpeechCorrelationId.resolve(null);
		String unsafe = SpeechCorrelationId.resolve("bad\nheader");

		assertTrue(missing.matches("[0-9a-f-]{36}"));
		assertTrue(unsafe.matches("[0-9a-f-]{36}"));
		assertNotEquals(missing, unsafe);
	}

}
