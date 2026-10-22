package com.deepdimen.matrix.assistant.session;

public final class AssistantSessionTitles {

	private static final int AUTO_TITLE_MAX_CODE_POINTS = 32;

	private AssistantSessionTitles() {
	}

	public static String fromFirstMessage(String content) {
		if (content == null) {
			return "新对话";
		}
		String normalized = content.replaceAll("[`#>*_~\\[\\]()]", " ").replaceAll("\\s+", " ").trim();
		if (normalized.isEmpty()) {
			return "新对话";
		}
		int codePoints = normalized.codePointCount(0, normalized.length());
		if (codePoints <= AUTO_TITLE_MAX_CODE_POINTS) {
			return normalized;
		}
		int end = normalized.offsetByCodePoints(0, AUTO_TITLE_MAX_CODE_POINTS);
		return normalized.substring(0, end);
	}

}
