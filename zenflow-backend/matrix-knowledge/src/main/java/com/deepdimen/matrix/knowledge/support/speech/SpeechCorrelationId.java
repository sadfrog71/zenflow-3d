package com.deepdimen.matrix.knowledge.support.speech;

import java.util.UUID;
import java.util.regex.Pattern;

/** Resolves a bounded trace identifier without reflecting unsafe header content. */
public final class SpeechCorrelationId {

	private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

	private SpeechCorrelationId() {
	}

	public static String resolve(String candidate) {
		String normalized = candidate == null ? null : candidate.trim();
		return normalized != null && SAFE_VALUE.matcher(normalized).matches() ? normalized
				: UUID.randomUUID().toString();
	}

}
