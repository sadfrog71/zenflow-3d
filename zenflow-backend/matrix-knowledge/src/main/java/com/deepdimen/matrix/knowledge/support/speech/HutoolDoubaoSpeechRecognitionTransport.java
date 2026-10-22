package com.deepdimen.matrix.knowledge.support.speech;

import java.net.URI;
import java.util.Map;

import org.springframework.stereotype.Component;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

@Component
public class HutoolDoubaoSpeechRecognitionTransport implements DoubaoSpeechRecognitionTransport {

	private static final int TIMEOUT_MS = 90_000;

	@Override
	public Response post(URI endpoint, Map<String, String> headers, String body) {
		try (HttpResponse response = HttpRequest.post(endpoint.toString())
			.addHeaders(headers)
			.body(body)
			.timeout(TIMEOUT_MS)
			.setFollowRedirects(false)
			.execute()) {
			return new Response(response.getStatus(), response.header("X-Api-Status-Code"),
					response.header("X-Api-Message"), response.header("X-Tt-Logid"), response.body());
		}
	}

}
