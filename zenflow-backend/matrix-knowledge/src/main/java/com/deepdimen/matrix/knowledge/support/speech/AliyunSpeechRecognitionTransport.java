package com.deepdimen.matrix.knowledge.support.speech;

import java.net.URI;
import java.util.Map;

/**
 * HTTP transport seam for Alibaba Cloud Model Studio OpenAI-compatible ASR.
 */
public interface AliyunSpeechRecognitionTransport {

	Response post(URI endpoint, Map<String, String> headers, String body);

	record Response(int httpStatus, String body) {
	}

}
