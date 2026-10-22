package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.MatrixBootApplication;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.companion.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Test classes only: actual HTTP/SSE on a rehearsal database, never a production vendor
 * result.
 */
public class InvellePreviewHttpMain {

	private static final AtomicInteger CALLS = new AtomicInteger();

	@TestConfiguration(proxyBeanMethods = false)
	static class Controlled {

		@Bean
		@Primary
		@ConditionalOnProperty(name = "zenflow.preview-http-qa", havingValue = "true")
		ZenflowModelGateway httpTestTransport(ZenflowModelService models, ZenflowProperties product, ObjectMapper json,
				JdbcTemplate jdbc) {
			if (!"1".equals(System.getenv("INVELLE_PREVIEW_HTTP_QA"))
					|| !jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("zenflow_core_rehearsal_"))
				throw new IllegalStateException("HTTP_REHEARSAL_REQUIRED");
			return new ZenflowModelGateway(models, product, json, (uri, key, body, sink, data) -> {
				if (CALLS.incrementAndGet() > 20)
					throw new java.io.IOException("CONTROLLED_REQUEST_LIMIT");
				boolean cancellation = body.contains("HTTP_QA_CANCEL"), failure = body.contains("HTTP_QA_FAILURE");
				var fragments = List.of("Controlled streaming response. ", "Only the vendor transport is replaced. ",
						"The original administrator Skill and Engine complete this run.");
				int chunks = cancellation ? 20 : fragments.size();
				for (int i = 0; i < chunks; i++) {
					if (sink.isCancelled())
						return;
					data.accept(json.writeValueAsString(Map.of("choices",
							List.of(Map.of("delta", Map.of("content", fragments.get(i % fragments.size())))))));
					Thread.sleep(1200);
					if (failure && i == 0)
						throw new java.io.IOException("CONTROLLED_STREAM_FAILURE");
				}
				data.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
				data.accept("[DONE]");
			});
		}

	}

	public static void main(String[] args) {
		if (!"1".equals(System.getenv("INVELLE_PREVIEW_HTTP_QA")))
			throw new IllegalStateException("HTTP_REHEARSAL_REQUIRED");
		var context = new SpringApplication(MatrixBootApplication.class, Controlled.class).run(args);
		try {
			if (!context.getEnvironment().getProperty("zenflow.preview-http-qa", Boolean.class, false))
				throw new IllegalStateException("CONTROLLED_TRANSPORT_REQUIRED");
			var jdbc = context.getBean(JdbcTemplate.class);
			assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("zenflow_core_rehearsal_"));
			TenantContextHolder.setTenantId(1L);
			var actor = (MatrixUser) context.getBean(ZenflowAdminUserDetailsService.class)
				.loadUserByUsername("invelle-admin");
			actor.getAttributes().put(SecurityConstants.CLIENT_ID, ZenflowAdminUserDetailsService.CLIENT);
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(actor, "", actor.getAuthorities()));
			var models = context.getBean(ZenflowModelService.class);
			var model = models.save(null,
					Map.of("name", "Controlled HTTP stream — test transport", "modelName", "controlled-http-only",
							"provider", "FIXTURE", "baseUrl", "https://api.openai.com/v1", "apiKey",
							"invalid-controlled-http-key", "temperature", .6, "maxOutputTokens", 2048,
							"allowPrivateContext", false));
			String modelId = model.get("id").toString();
			models.recordTest(modelId, ((Number) model.get("version")).intValue());
			models.activate(modelId, ((Number) model.get("version")).intValue());
			var companions = context.getBean(ZenflowCompanionService.class);
			for (int variant = 0; variant < 2; variant++) {
				var draft = first(companions.createDraft(Map.of()));
				String id = draft.get("id").toString();
				draft = first(companions.save(id, Map.of("expectedVersion", draft.get("version"), "retrievalPolicy",
						ZenflowReferencePolicy.empty().projection(), "authoring",
						Map.of("principles",
								"Controlled HTTP fixture variant " + variant + ": listen calmly and be truthful.",
								"coreSummary", "Controlled streaming browser test", "citationMode", "ON_DEMAND",
								"examples", List.of()))));
				companions.prepare(id, Map.of("expectedVersion", draft.get("version"), "modelId", modelId));
				var worker = context.getBean(ZenflowCompanionPrepareWorker.class);
				for (int i = 0; i < 20 && "PREPARING".equals(jdbc.queryForObject(
						"SELECT state FROM zf_companion_revision WHERE tenant_id=1 AND id=?", String.class, id)); i++)
					worker.poll();
				assertEquals("READY", jdbc.queryForObject(
						"SELECT state FROM zf_companion_revision WHERE tenant_id=1 AND id=?", String.class, id));
				if (variant == 0)
					companions.publish(id, Map.of("expectedBindingVersion", companions.detail().get("bindingVersion")));
			}
			System.out.println(
					"INVELLE_PREVIEW_HTTP_READY: rehearsal only; controlled vendor transport; actual auth HTTP SSE Skill Engine");
		}
		catch (Throwable failure) {
			System.out.println("INVELLE_PREVIEW_HTTP_FAIL: " + failure.getClass().getSimpleName());
			context.close();
			System.exit(1);
		}
		finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> first(Map<String, Object> config) {
		return ((List<Map<String, Object>>) config.get("revisions")).get(0);
	}

}
