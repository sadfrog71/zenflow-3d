package com.deepdimen.matrix.knowledge.support.speech;

import java.net.URI;
import java.util.Map;

/**
 * Small transport seam for the Doubao REST adapter. Keeping HTTP outside the
 * adapter makes provider response mapping testable without calling a paid API.
 */
public interface DoubaoSpeechRecognitionTransport {

	Response post(URI endpoint, Map<String, String> headers, String body);

	record Response(int httpStatus, String providerStatus, String providerMessage, String logId, String body) {
	}

}
