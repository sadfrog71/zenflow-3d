package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.deepdimen.matrix.knowledge.support.provider.AliyunModelCatalog;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

class AiModelServiceImplCatalogTest {

	private HttpServer server;

	private String origin;

	private AiModelServiceImpl service;

	@BeforeEach
	void startLocalStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
		origin = "http://127.0.0.1:" + server.getAddress().getPort();
		service = new AiModelServiceImpl(mock(ModelProvider.class), mock(ModelEndpointPolicy.class));
	}

	@AfterEach
	void stopLocalStub() {
		server.stop(0);
	}

	private AliyunModelCatalog.Response fetch() {
		return ReflectionTestUtils.invokeMethod(service, "fetchAliyunCatalogPage",
				origin + "/models?page_no=1&page_size=100", "synthetic-test-token", 2000);
	}

	@Test
	void readsNativeJsonWithTheConfiguredCredentialAndPagination() {
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<String> query = new AtomicReference<>();
		server.createContext("/models", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			query.set(exchange.getRequestURI().getQuery());
			byte[] body = "{\"success\":true,\"output\":{\"page_no\":1,\"total\":1,\"models\":[{\"model\":\"synthetic-model\"}]}}"
				.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		AliyunModelCatalog.Response result = fetch();
		assertEquals(200, result.status());
		assertEquals(1, result.body().getJSONObject("output").getInt("total"));
		assertEquals("Bearer synthetic-test-token", authorization.get());
		assertEquals("page_no=1&page_size=100", query.get());
	}

	@Test
	void doesNotFollowRedirectsOrForwardCredentials() {
		server.createContext("/models", exchange -> {
			exchange.getResponseHeaders().add("Location", origin + "/must-not-follow");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		AtomicReference<String> forwarded = new AtomicReference<>();
		server.createContext("/must-not-follow", exchange -> {
			forwarded.set(exchange.getRequestHeaders().getFirst("Authorization"));
			exchange.sendResponseHeaders(401, -1);
			exchange.close();
		});
		assertEquals(302, fetch().status());
		assertNull(forwarded.get());
	}

	@Test
	void neverExposesAnErrorResponseBody() {
		server.createContext("/models", exchange -> {
			byte[] body = "private-error-detail".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(403, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		AliyunModelCatalog.Response result = fetch();
		assertEquals(403, result.status());
		assertNull(result.body());
		assertEquals("CatalogResponse[REDACTED]", result.toString());
	}

	@Test
	void refusesAnOversizedBodyInsteadOfReadingAnUnboundedCatalogPage() {
		server.createContext("/models", exchange -> {
			byte[] body = new byte[4 * 1024 * 1024 + 1];
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		assertEquals("百炼模型目录响应超过大小限制", assertThrows(IllegalStateException.class, this::fetch).getMessage());
	}

	@Test
	void refusesInvalidJsonWithoutIncludingItInTheError() {
		server.createContext("/models", exchange -> {
			byte[] body = "<html>private-provider-diagnostic</html>".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		assertNotNull(assertThrows(IllegalStateException.class, this::fetch));
	}

}
