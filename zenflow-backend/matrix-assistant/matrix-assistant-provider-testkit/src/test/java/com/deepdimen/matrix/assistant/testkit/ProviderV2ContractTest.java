package com.deepdimen.matrix.assistant.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProviderV2ContractTest extends AssistantContextProviderContract {

	private static final ProviderId PROVIDER_ID = ProviderId.of("CUSTOMER_SERVICE");

	private static final IdentityEnvelope AUTHORIZED = identity("user-1");

	private final AssistantContextProvider provider = new CustomerServiceProvider();

	@Test
	void acceptsLegacyAndFifthProviderIdsWithoutAnEnumChange() {
		assertEquals("KNOWLEDGE", ProviderId.KNOWLEDGE.value());
		assertEquals("WORK", ProviderId.WORK.value());
		assertEquals("CAPSULE", ProviderId.CAPSULE.value());
		assertEquals("INFORMATION", ProviderId.INFORMATION.value());
		assertEquals("CUSTOMER_SERVICE", PROVIDER_ID.value());
	}

	@Test
	void rejectsProviderIdsOutsideTheStableStringBoundary() {
		assertThrows(IllegalArgumentException.class, () -> ProviderId.of("customer_service"));
		assertThrows(IllegalArgumentException.class, () -> ProviderId.of("A-B"));
		assertThrows(IllegalArgumentException.class, () -> ProviderId.of(""));
		assertThrows(IllegalArgumentException.class, () -> ProviderId.of("A".repeat(65)));
	}

	@Test
	void providerIdKeepsTheLegacyJsonStringShape() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		assertEquals("\"INFORMATION\"", objectMapper.writeValueAsString(ProviderId.INFORMATION));
		assertEquals(ProviderId.INFORMATION, objectMapper.readValue("\"INFORMATION\"", ProviderId.class));
	}

	@Test
	void queryPreservesTheCallerContractVersionForCompatibilityChecks() {
		ProviderQuery query = new ProviderQuery("assistant-context/v1", "request-1", "correlation-1", AUTHORIZED,
				"customer", new ProviderScope(null, null, null, null, null), new ProviderLimits(10, 1000, 1000));
		assertEquals("assistant-context/v1", query.contractVersion());
	}

	@Test
	void queryDefaultsItsOptionalScopeAndLimits() {
		ProviderQuery query = new ProviderQuery(AssistantContextContract.VERSION, "request-1", "correlation-1",
				AUTHORIZED, "customer", null, null);
		assertNotNull(query.scope());
		assertNotNull(query.limits());
	}

	@Test
	void rejectsCitationWhoseStableResourceDoesNotMatchItsFragment() {
		Citation mismatched = new Citation("customer:42:v1", ProviderId.KNOWLEDGE, "CUSTOMER", "42", "v1", "INTERNAL",
				"CustomerDetail", Map.of());
		assertThrows(IllegalArgumentException.class, () -> new ContextFragment("fragment-1", PROVIDER_ID, "CUSTOMER",
				"42", "v1", FactType.DOMAIN_FACT, "Customer", "Authorized fact", null, null, "sha256:1", mismatched));
	}

	@Test
	void legacyV1CitationJsonIsUpgradedFromItsFragmentReference() throws Exception {
		String legacyJson = """
				{"fragmentId":"fragment-1","provider":"KNOWLEDGE","sourceType":"SLICE","sourceId":"42",
				 "sourceVersion":"v1","factType":"SOURCE_EVIDENCE","title":"Doc","excerpt":"Fact",
				 "occurredAt":null,"updatedAt":null,"contentHash":"sha256:1",
				 "citation":{"citationId":"knowledge:42","routeType":"INTERNAL","routeName":"KnowledgeDocument",
				 "routeParams":{"id":"42"}}}
				""";
		ContextFragment fragment = new ObjectMapper().readValue(legacyJson, ContextFragment.class);
		assertEquals(ProviderId.KNOWLEDGE, fragment.citation().provider());
		assertEquals("SLICE", fragment.citation().sourceType());
		assertEquals("42", fragment.citation().sourceId());
		assertEquals("v1", fragment.citation().sourceVersion());
	}

	@Override
	protected AssistantContextProvider provider() {
		return provider;
	}

	@Override
	protected ProviderQuery query() {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "correlation-1", AUTHORIZED, "customer",
				new ProviderScope(null, null, null, null, null), new ProviderLimits(10, 1000, 1000), null);
	}

	@Override
	protected CitationResolveRequest authorizedCitation() {
		return new CitationResolveRequest(citation(), AUTHORIZED);
	}

	@Override
	protected IdentityEnvelope unauthorizedIdentity() {
		return identity("user-2");
	}

	private static IdentityEnvelope identity(String userId) {
		return new IdentityEnvelope("tenant-1", userId, "org-1", null, null, null, "correlation-1",
				"ASSISTANT_CONTEXT");
	}

	private static Citation citation() {
		return new Citation("customer:42:v1", PROVIDER_ID, "CUSTOMER", "42", "v1", "INTERNAL", "CustomerDetail",
				Map.of("id", "42"));
	}

	private static final class CustomerServiceProvider implements AssistantContextProvider {

		@Override
		public ProviderDescriptor describe() {
			return new ProviderDescriptor(AssistantContextContract.VERSION, PROVIDER_ID, "Customer service",
					"Read-only customer facts", "customer-service",
					new ProviderCapabilities(true, true, true, false, true, false, false, null, null),
					new ProviderLimits(10, 1000, 1000));
		}

		@Override
		public ProviderResult query(ProviderQuery query) {
			ContextFragment fragment = new ContextFragment("fragment-1", PROVIDER_ID, "CUSTOMER", "42", "v1",
					FactType.DOMAIN_FACT, "Customer", "Authorized fact", Instant.now(), Instant.now(), "sha256:1",
					citation());
			return new ProviderResult(AssistantContextContract.VERSION, PROVIDER_ID, ProviderStatus.OK, Instant.now(),
					1, 0, false, List.of(fragment), null);
		}

		@Override
		public CitationResolution resolve(CitationResolveRequest request) {
			if (!AUTHORIZED.userId().equals(request.identity().userId())) {
				return CitationResolution.forbidden(request.citation().citationId(), "Not authorized");
			}
			return new CitationResolution(request.citation().citationId(), ProviderStatus.OK, request.citation(), null);
		}

	}

}
