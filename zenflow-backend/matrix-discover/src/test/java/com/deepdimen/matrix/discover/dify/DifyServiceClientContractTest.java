package com.deepdimen.matrix.discover.dify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

class DifyServiceClientContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final AtomicReference<String> requestMethod = new AtomicReference<>();

	private final AtomicReference<String> requestPath = new AtomicReference<>();

	private final AtomicReference<String> requestAuthorization = new AtomicReference<>();

	private final AtomicReference<String> requestBody = new AtomicReference<>();

	private com.sun.net.httpserver.HttpServer server;

	private DifyServiceClient client;

	@BeforeEach
	void setUp() throws IOException {
		server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requestMethod.set(exchange.getRequestMethod());
			requestPath.set(exchange.getRequestURI().getPath());
			requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			String response = exchange.getRequestURI().getPath().endsWith("/parameters")
					? "{\"user_input_form\":[{\"variable\":\"matrix_query\"}]}"
					: "{\"task_id\":\"task-1\",\"conversation_id\":\"conversation-1\",\"answer\":\"ok\"}";
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
		String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
		WebClient webClient = WebClient.builder()
			.clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder().build()))
			.build();
		client = new DifyServiceClient(webClient, objectMapper, baseUrl, 5);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void postsDifyChatContractWithBearerHeaderAndJsonBody() throws Exception {
		JsonNode response = client.post("/chat-messages", "test-api-key",
				Map.of("inputs", Map.of("scope", "work"), "query", "hello", "response_mode", "blocking",
						"user", "assistant-user"))
			.block(Duration.ofSeconds(3));

		assertNotNull(response);
		assertEquals("POST", requestMethod.get());
		assertEquals("/v1/chat-messages", requestPath.get());
		assertEquals("Bearer test-api-key", requestAuthorization.get());
		JsonNode body = objectMapper.readTree(requestBody.get());
		assertEquals("hello", body.path("query").asText());
		assertEquals("blocking", body.path("response_mode").asText());
		assertEquals("assistant-user", body.path("user").asText());
		assertEquals("ok", response.path("answer").asText());
	}

	@Test
	void readsWorkflowParametersUsingTheSameServiceApiPrefix() throws Exception {
		JsonNode response = client.getParameters("workflow-api-key").block(Duration.ofSeconds(3));

		assertNotNull(response);
		assertEquals("GET", requestMethod.get());
		assertEquals("/v1/parameters", requestPath.get());
		assertEquals("Bearer workflow-api-key", requestAuthorization.get());
		assertEquals("matrix_query", response.path("user_input_form").get(0).path("variable").asText());
	}

}
