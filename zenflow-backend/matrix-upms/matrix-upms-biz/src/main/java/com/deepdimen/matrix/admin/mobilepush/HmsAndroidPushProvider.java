package com.deepdimen.matrix.admin.mobilepush;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HmsAndroidPushProvider implements PushProvider {

	private final MobilePushProperties properties;

	private final ObjectMapper json;

	private final HttpClient http;

	private String accessToken;

	private Instant tokenExpiry = Instant.EPOCH;

	@org.springframework.beans.factory.annotation.Autowired
	public HmsAndroidPushProvider(MobilePushProperties properties, ObjectMapper json) {
		this(properties, json,
				HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NEVER)
					.build());
	}

	HmsAndroidPushProvider(MobilePushProperties properties, ObjectMapper json, HttpClient http) {
		this.properties = properties;
		this.json = json;
		this.http = http;
	}

	@Override
	public String channel() {
		return "HMS_ANDROID";
	}

	@Override
	public Result send(String token, String messageId, String bindingId, String notificationTag) {
		if (!properties.isEnabled())
			return new Result("FAILED_FINAL", null, "CHANNEL_DISABLED");
		String appId = properties.getHmsAppId();
		if (appId == null || !appId.matches("[0-9]{5,32}") || properties.getHmsClientSecret() == null
				|| properties.getHmsClientSecret().isBlank())
			return new Result("FAILED_FINAL", null, "CHANNEL_NOT_CONFIGURED");
		try {
			String credential = credential();
			Map<String, Object> payload = payload(token, messageId, bindingId, notificationTag);
			HttpRequest request = HttpRequest
				.newBuilder(URI.create("https://push-api.cloud.huawei.com/v1/" + appId + "/messages:send"))
				.timeout(Duration.ofSeconds(20))
				.header("Authorization", "Bearer " + credential)
				.header("Content-Type", "application/json; charset=UTF-8")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
				.build();
			HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			JsonNode result;
			try (InputStream body = response.body()) {
				if (response.statusCode() == 401) {
					invalidate();
					return Result.retry("PROVIDER_AUTH_EXPIRED");
				}
				if (response.statusCode() == 429 || response.statusCode() >= 500)
					return Result.retry("PROVIDER_TEMPORARY");
				if (response.statusCode() != 200)
					return new Result("FAILED_FINAL", null, "PROVIDER_HTTP_REJECTED");
				result = read(body);
			}
			String code = result.path("code").asText();
			if ("80000000".equals(code)) {
				String receipt = result.path("requestId").asText("");
				return new Result("ACCEPTED", receipt.matches("[A-Za-z0-9._:-]{0,256}") ? receipt : null, null);
			}
			if ("80300007".equals(code))
				return new Result("INVALID_TOKEN", null, code);
			if ("80000001".equals(code))
				return Result.retry(code);
			return new Result("FAILED_FINAL", null, code.matches("[0-9]{8}") ? code : "PROVIDER_REJECTED");
		}
		catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
			return Result.retry("INTERRUPTED");
		}
		catch (Exception ignored) {
			return Result.retry("PROVIDER_UNAVAILABLE");
		}
	}

	Map<String, Object> payload(String token, String messageId, String bindingId, String tag) throws Exception {
		if (!messageId.matches("[0-9]{1,20}") || !bindingId.matches("[0-9a-f-]{36}"))
			throw new IllegalArgumentException("Invalid push intent");
		String intent = "intent:#Intent;action=com.avatarai.mobile.PUSH_OPEN;package=" + properties.applicationId()
				+ ";S.messageId=" + messageId + ";S.bindingId=" + bindingId + ";S.schemaVersion=1;end";
		Map<String, Object> data = Map.of("schemaVersion", 1, "messageId", messageId, "bindingId", bindingId);
		Map<String, Object> notification = Map.of("title", "衍智云", "body", "你有一条新的站内消息，请打开应用查看", "click_action",
				Map.of("type", 1, "intent", intent), "channel_id", "matrix_messages", "tag", tag, "foreground_show",
				false);
		return Map.of("validate_only", false, "message",
				Map.of("token", List.of(token), "data", json.writeValueAsString(data), "android",
						Map.of("ttl", "600s", "collapse_key", -1, "notification", notification)));
	}

	private synchronized void invalidate() {
		accessToken = null;
		tokenExpiry = Instant.EPOCH;
	}

	private synchronized String credential() throws Exception {
		if (accessToken != null && Instant.now().isBefore(tokenExpiry))
			return accessToken;
		String form = "grant_type=client_credentials&client_id=" + encode(properties.getHmsAppId()) + "&client_secret="
				+ encode(properties.getHmsClientSecret());
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth-login.cloud.huawei.com/oauth2/v3/token"))
			.timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build();
		HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		JsonNode value;
		try (InputStream body = response.body()) {
			if (response.statusCode() != 200)
				throw new IllegalStateException("Provider authentication unavailable");
			value = read(body);
		}
		String issued = value.path("access_token").asText();
		int lifetime = value.path("expires_in").asInt();
		if (issued.isBlank() || lifetime <= 60 || issued.length() > 8192)
			throw new IllegalStateException("Provider authentication invalid");
		accessToken = issued;
		tokenExpiry = Instant.now().plusSeconds(Math.min(lifetime - 60, 3500));
		return issued;
	}

	private JsonNode read(InputStream body) throws Exception {
		byte[] bytes = body.readNBytes(65537);
		if (bytes.length > 65536)
			throw new IllegalStateException("Provider response too large");
		return json.readTree(bytes);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
