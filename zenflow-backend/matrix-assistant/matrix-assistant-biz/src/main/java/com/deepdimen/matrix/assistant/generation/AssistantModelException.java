package com.deepdimen.matrix.assistant.generation;

public class AssistantModelException extends RuntimeException {

	private final String errorCode;

	public AssistantModelException(String errorCode, String safeMessage) {
		super(safeMessage);
		this.errorCode = errorCode;
	}

	public String errorCode() {
		return errorCode;
	}

}
