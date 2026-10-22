package com.deepdimen.matrix.assistant.generation;

/**
 * Removes provider-specific hidden reasoning blocks before they reach users or storage.
 */
public class AssistantReasoningFilter {

	private static final String OPEN = "<think>";

	private static final String CLOSE = "</think>";

	private final StringBuilder pending = new StringBuilder();

	private boolean hidden;

	public String accept(String chunk) {
		if (chunk != null) {
			pending.append(chunk);
		}
		return drain(false);
	}

	public String finish() {
		return drain(true);
	}

	public static String sanitize(String content) {
		AssistantReasoningFilter filter = new AssistantReasoningFilter();
		return filter.accept(content) + filter.finish();
	}

	private String drain(boolean finishing) {
		StringBuilder visible = new StringBuilder();
		while (!pending.isEmpty()) {
			String boundary = hidden ? CLOSE : OPEN;
			int boundaryIndex = pending.indexOf(boundary);
			if (boundaryIndex >= 0) {
				if (!hidden) {
					visible.append(pending, 0, boundaryIndex);
				}
				pending.delete(0, boundaryIndex + boundary.length());
				hidden = !hidden;
				continue;
			}
			if (hidden) {
				if (finishing) {
					pending.setLength(0);
				}
				else {
					retainPossibleBoundaryPrefix(boundary);
				}
				break;
			}
			int retained = finishing ? 0 : possibleBoundaryPrefixLength(boundary);
			int visibleLength = pending.length() - retained;
			visible.append(pending, 0, visibleLength);
			pending.delete(0, visibleLength);
			break;
		}
		return visible.toString();
	}

	private void retainPossibleBoundaryPrefix(String boundary) {
		int retained = possibleBoundaryPrefixLength(boundary);
		if (pending.length() > retained) {
			pending.delete(0, pending.length() - retained);
		}
	}

	private int possibleBoundaryPrefixLength(String boundary) {
		int max = Math.min(pending.length(), boundary.length() - 1);
		for (int length = max; length > 0; length--) {
			if (pending.substring(pending.length() - length).equals(boundary.substring(0, length))) {
				return length;
			}
		}
		return 0;
	}

}
