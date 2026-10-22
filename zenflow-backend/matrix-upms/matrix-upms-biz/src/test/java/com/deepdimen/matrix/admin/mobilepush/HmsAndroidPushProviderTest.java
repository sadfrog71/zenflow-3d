package com.deepdimen.matrix.admin.mobilepush;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HmsAndroidPushProviderTest {

	private final ObjectMapper json = new ObjectMapper();

	private final HttpClient http = mock(HttpClient.class);

	private final ArrayDeque<HttpResponse<InputStream>> responses = new ArrayDeque<>();

	private final List<HttpRequest> requests = new ArrayList<>();

	private HmsAndroidPushProvider provider;

	private final String binding = "01234567-1234-1234-1234-012345678902";

	@BeforeEach
	void setup() throws Exception {
		var properties = new MobilePushProperties();
		properties.setEnabled(true);
		properties.setHmsAppId("12345678");
		properties.setHmsClientSecret("test-secret");
		provider = new HmsAndroidPushProvider(properties, json, http);
		doAnswer(call -> {
			requests.add(call.getArgument(0));
			return responses.remove();
		}).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	private void respond(int status, String body) {
		HttpResponse<InputStream> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		responses.add(response);
	}

	private void oauth() {
		respond(200, "{\"access_token\":\"provider-credential\",\"expires_in\":3600}");
	}

	private PushProvider.Result send() {
		return provider.send("device-token", "123", binding, "delivery");
	}

	@Test
	void classifiesAcceptedInvalidAndTemporaryResponsesWithoutExposingProviderText() {
		oauth();
		respond(200, "{\"code\":\"80000000\",\"requestId\":\"receipt-1\"}");
		assertEquals(new PushProvider.Result("ACCEPTED", "receipt-1", null), send());
		respond(200, "{\"code\":\"80300007\"}");
		assertEquals("INVALID_TOKEN", send().status());
		respond(200, "{\"code\":\"80000001\"}");
		assertEquals("RETRY", send().status());
		respond(200, "{\"code\":\"secret diagnostic text\"}");
		assertEquals("PROVIDER_REJECTED", send().failureCode());
		assertEquals(1, requests.stream().filter(r -> r.uri().getHost().startsWith("oauth-")).count());
		assertTrue(requests.stream().allMatch(r -> r.timeout().isPresent()));
	}

	@Test
	void unauthorizedResponseInvalidatesCachedOAuthCredential() {
		oauth();
		respond(401, "");
		assertEquals("PROVIDER_AUTH_EXPIRED", send().failureCode());
		oauth();
		respond(200, "{\"code\":\"80000000\"}");
		assertEquals("ACCEPTED", send().status());
		assertEquals(2, requests.stream().filter(r -> r.uri().getHost().startsWith("oauth-")).count());
	}

	@Test
	void retriesHttpThrottlingServerFailuresAndMalformedBoundedBodies() {
		oauth();
		respond(429, "");
		assertEquals("RETRY", send().status());
		respond(503, "");
		assertEquals("RETRY", send().status());
		respond(200, "not-json");
		assertEquals("PROVIDER_UNAVAILABLE", send().failureCode());
		respond(200, "x".repeat(65537));
		assertEquals("PROVIDER_UNAVAILABLE", send().failureCode());
		respond(400, "private diagnostic");
		assertEquals("FAILED_FINAL", send().status());
	}

	@Test
	void payloadContainsOnlyOpaqueRoutingDataAndFixedApplicationIntent() throws Exception {
		Map<String, Object> payload = provider.payload("device-token", "123", binding, "delivery");
		var message = json.valueToTree(payload).path("message");
		assertEquals(json.readTree("{\"schemaVersion\":1,\"messageId\":\"123\",\"bindingId\":\"" + binding + "\"}"),
				json.readTree(message.path("data").asText()));
		String intent = message.path("android").path("notification").path("click_action").path("intent").asText();
		assertTrue(intent.contains("package=com.avataraitest.mobile;"));
		assertFalse(intent.contains("token"));
		assertFalse(intent.contains("tenant"));
		assertEquals("matrix_messages", message.path("android").path("notification").path("channel_id").asText());
		assertThrows(IllegalArgumentException.class, () -> provider.payload("token", "../admin", binding, "delivery"));
	}

}
