package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.capability.UnavailableAssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.capability.UnavailableAssistantSkillCatalogProvider;
import com.deepdimen.matrix.assistant.controller.AssistantSessionController;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.generation.AssistantNativeProfileResolver;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.orchestrator.AssistantContextOrchestrator;
import com.deepdimen.matrix.assistant.policy.AssistantIntentPolicy;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.run.AssistantPromptComposer;
import com.deepdimen.matrix.assistant.run.AssistantRunDispatchService;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.run.AssistantRunEventService;
import com.deepdimen.matrix.assistant.run.InMemoryAssistantRunStore;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import reactor.core.publisher.Flux;

/** API-level native Run proof with no Dify adapter or Dify configuration. */
class UnifiedAssistantEndToEndTest {

	private static final ProviderId TEST_PROVIDER = ProviderId.of("E2E_SOURCE");

	private final AtomicReference<IdentityEnvelope> currentIdentity = new AtomicReference<>();

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockMvc mockMvc;

	private InMemoryAssistantRunStore runStore;

	@BeforeEach
	void setUp() {
		AssistantContextProvider provider = new TenantOrgProvider();
		InMemoryAssistantSessionRepository sessions = new InMemoryAssistantSessionRepository();
		runStore = new InMemoryAssistantRunStore(sessions);
		AssistantContextOrchestrator orchestrator = new AssistantContextOrchestrator(
				new AssistantProviderRegistry(List.of(provider)));
		AssistantModelGateway gateway = new StreamingGateway();
		AssistantNativeProfileResolver profileResolver = (runId, identity, requireStreaming, classification,
				containsContext) -> new AssistantNativeProfileResolver.ResolvedNativeProfile("native-1", "Native E2E",
						"model-1", gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, runId,
								identity, "model-1", requireStreaming, classification, containsContext)));
		AssistantRunDispatchService directDispatch = new AssistantRunDispatchService() {
			@Override
			public void dispatch(String runId, java.util.function.Consumer<String> worker) {
				worker.accept(runId);
			}
		};
		AssistantRunEngine engine = new AssistantRunEngine(runStore, sessions, new AssistantRunEventService(runStore),
				directDispatch, orchestrator, new UnavailableAssistantPromptSkillCatalog(), profileResolver, gateway,
				new AssistantIntentPolicy(), new AssistantPromptComposer());
		AssistantSessionService service = new AssistantSessionService(sessions, orchestrator, engine);
		AssistantIdentityResolver resolver = currentIdentity::get;
		AssistantSessionController controller = new AssistantSessionController(resolver, service,
				new UnavailableAssistantSkillCatalogProvider());
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void nativeRunCompletesWithDurableStreamEventsAndRejectsOtherTenant() throws Exception {
		currentIdentity.set(identity("11", "7", "101"));
		String sessionBody = mockMvc
			.perform(post("/assistant/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"E2E\",\"activeScopes\":[\"E2E_SOURCE\"]}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String sessionId = new ObjectMapper().readTree(sessionBody).path("assistantSessionId").asText();

		String runBody = mockMvc
			.perform(post("/assistant/sessions/{sessionId}/runs", sessionId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"clientMessageId\":\"e2e-message-1\",\"content\":\"读取当前范围\","
						+ "\"providerCodes\":[\"E2E_SOURCE\"]}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.eventsUrl").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode accepted = objectMapper.readTree(runBody);
		String runId = accepted.path("runId").asText();

		mockMvc.perform(get("/assistant/sessions/{sessionId}/runs/{runId}", sessionId, runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.draftContent").value("tenant=11;org=101"))
			.andExpect(jsonPath("$.inputContent").value("读取当前范围"))
			.andExpect(jsonPath("$.providerCodes[0]").value("E2E_SOURCE"));

		var events = runStore.findAfter(runId, 0, 100);
		assertTrue(events.stream().anyMatch(event -> event.type() == RunEventType.RESPONSE_DELTA));
		assertEquals(RunEventType.RUN_COMPLETED, events.get(events.size() - 1).type());

		currentIdentity.set(identity("12", "8", "201"));
		mockMvc.perform(get("/assistant/sessions/{sessionId}/runs/{runId}", sessionId, runId))
			.andExpect(status().isNotFound());
	}

	private IdentityEnvelope identity(String tenantId, String userId, String orgId) {
		return new IdentityEnvelope(tenantId, userId, orgId, List.of(orgId), List.of(), List.of(),
				"e2e-" + tenantId + "-" + orgId, "ASSISTANT_CONTEXT");
	}

	private static final class TenantOrgProvider implements AssistantContextProvider {

		@Override
		public ProviderDescriptor describe() {
			return new ProviderDescriptor(AssistantContextContract.VERSION, TEST_PROVIDER, "E2E Source",
					"Tenant and organization scoped test source", "generic", new ProviderCapabilities(true, true, false,
							false, true, false, false, List.of("E2E_ITEM"), List.of()),
					new ProviderLimits(5, 1000, 1000));
		}

		@Override
		public ProviderResult query(ProviderQuery request) {
			String sourceId = request.identity().tenantId() + ":" + request.identity().currentOrgId();
			Citation citation = new Citation("e2e:" + sourceId, TEST_PROVIDER, "E2E_ITEM", sourceId, "1", "ROUTE",
					"e2e", Map.of("id", sourceId));
			ContextFragment fragment = new ContextFragment("fragment:" + sourceId, TEST_PROVIDER, "E2E_ITEM", sourceId,
					"1", FactType.DOMAIN_FACT, "Scoped source",
					"tenant=" + request.identity().tenantId() + ";org=" + request.identity().currentOrgId(),
					Instant.now(), Instant.now(), "hash:" + sourceId, citation, DataClassification.SENSITIVE, 10);
			return new ProviderResult(AssistantContextContract.VERSION, TEST_PROVIDER, ProviderStatus.OK, Instant.now(),
					1, 0, false, List.of(fragment), List.of());
		}

		@Override
		public CitationResolution resolve(AssistantContextContract.CitationResolveRequest request) {
			return new CitationResolution(request.citationId(), ProviderStatus.OK, request.citation(), null);
		}

	}

	private static final class StreamingGateway implements AssistantModelGateway {

		@Override
		public ResolvedAssistantModel resolve(ModelResolutionRequest request) {
			return new ResolvedAssistantModel(AssistantModelContract.VERSION, "model-1", "Native E2E", "test",
					new ModelCapabilities(true, 8192, 1024, true),
					new AssistantEgress(DeploymentDomain.INTERNAL, "cn", true, DataClassification.SENSITIVE, "e2e"));
		}

		@Override
		public Flux<ModelChunk> stream(AssistantModelRequest request) {
			String answer = "tenant=" + request.identity().tenantId() + ";org=" + request.identity().currentOrgId();
			return Flux.just(new ModelChunk(request.runId(), 0, answer, false, ""),
					new ModelChunk(request.runId(), 1, "", true, "stop"));
		}

	}

}
