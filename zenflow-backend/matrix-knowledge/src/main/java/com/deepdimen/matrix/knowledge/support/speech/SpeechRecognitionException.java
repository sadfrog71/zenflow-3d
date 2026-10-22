package com.deepdimen.matrix.knowledge.support.speech;

import lombok.Getter;

@Getter
public class SpeechRecognitionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String code;

	public SpeechRecognitionException(String code, String message) {
		super(message);
		this.code = code;
	}

	public SpeechRecognitionException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

}
