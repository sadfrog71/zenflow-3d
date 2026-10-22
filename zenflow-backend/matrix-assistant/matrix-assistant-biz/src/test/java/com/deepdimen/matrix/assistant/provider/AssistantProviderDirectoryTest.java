package com.deepdimen.matrix.assistant.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
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
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.PlatformStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.RuntimeStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.TenantProviderSetting;

class AssistantProviderDirectoryTest {

	private static final ProviderId TEST = ProviderId.of("TEST_FIFTH");

	private static final IdentityEnvelope IDENTITY = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(),
			List.of(), "provider-test", "ASSISTANT_CONTEXT");

	@Test
	void localBeanWinsOverRemoteDefinitionAndGovernanceRaisesClassification() {
		FakeRepository repository = new FakeRepository(definition(TEST, true, true, DataClassification.INTERNAL));
		AtomicInteger remoteCalls = new AtomicInteger();
		AssistantRemoteProviderFactory remote = (definition, identity, runId) -> {
			remoteCalls.incrementAndGet();
			return provider(TEST, DataClassification.PUBLIC);
		};
		AssistantProviderRegistry registry = new AssistantProviderRegistry(
				List.of(provider(TEST, DataClassification.PUBLIC)), repository, remote);

		ProviderResult result = registry.query(TEST, query(TEST));

		assertEquals(0, remoteCalls.get());
		assertEquals(DataClassification.INTERNAL, result.fragments().get(0).dataClassification());
		assertEquals(RuntimeStatus.AVAILABLE, registry.catalog(IDENTITY).get(0).status());
	}

	@Test
	void unavailableRemoteRemainsVisibleWithSafeSnapshot() {
		FakeRepository repository = new FakeRepository(definition(TEST, true, true, DataClassification.SENSITIVE));
		AssistantProviderRegistry registry = new AssistantProviderRegistry(List.of(), repository,
				(definition, identity, runId) -> new FailingProvider());

		var catalog = registry.catalog(IDENTITY);

		assertEquals(1, catalog.size());
		assertEquals(TEST, catalog.get(0).providerId());
		assertEquals(RuntimeStatus.UNAVAILABLE, catalog.get(0).status());
		assertEquals(ProviderStatus.UNAVAILABLE, registry.query(TEST, query(TEST)).status());
	}

	@Test
	void futureProviderIsAbsentUntilTenantExplicitlyConfiguresIt() {
		FakeRepository repository = new FakeRepository(definition(TEST, false, false, DataClassification.SENSITIVE));
		AssistantProviderRegistry registry = new AssistantProviderRegistry(List.of(), repository,
				(definition, identity, runId) -> new FailingProvider());

		assertFalse(registry.catalog(IDENTITY).stream().anyMatch(entry -> TEST.equals(entry.providerId())));

		repository.settings.put(TEST, new TenantProviderSetting("11", TEST, false, false,
				new ProviderLimits(10, 1000, 500), 1, Instant.now()));
		assertEquals(RuntimeStatus.DISABLED, registry.catalog(IDENTITY).get(0).status());
	}

	@Test
	void tenantLimitsAreClampedBeforeInvocationAndEnforcedAfterward() {
		FakeRepository repository = new FakeRepository(definition(TEST, true, true, DataClassification.SENSITIVE));
		repository.settings.put(TEST,
				new TenantProviderSetting("11", TEST, true, true, new ProviderLimits(1, 5, 100), 1, Instant.now()));
		AtomicReference<ProviderQuery> observed = new AtomicReference<>();
		AssistantContextProvider local = new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return descriptor(TEST);
			}

			@Override
			public ProviderResult query(ProviderQuery query) {
				observed.set(query);
				ContextFragment fragment = new ContextFragment("fragment-1", TEST, "TEST", "1", "v1",
						FactType.DOMAIN_FACT, "title", "content", Instant.now(), Instant.now(), "hash", null);
				return new ProviderResult(AssistantContextContract.VERSION, TEST, ProviderStatus.OK, Instant.now(), 1, 0,
						false, List.of(fragment), List.of());
			}
		};
		AssistantProviderRegistry registry = new AssistantProviderRegistry(List.of(local), repository, null);

		ProviderResult result = registry.query(TEST, query(TEST));

		assertEquals(1, observed.get().limits().maxItems());
		assertEquals(5, observed.get().limits().maxCharacters());
		assertEquals(100, observed.get().limits().timeoutMs());
		assertEquals(0, result.returned());
		assertTrue(result.truncated());
	}

	@Test
	void untrustedServiceIdCannotEnterControlPlane() {
		assertThrows(IllegalArgumentException.class,
				() -> new ProviderDefinition(TEST, "https://attacker.example", PlatformStatus.ACTIVE, false, false,
						DataClassification.SENSITIVE, "{}", null, null, 1, Instant.now(), Instant.now()));
	}

	@Test
	void repeatedFailuresOpenAPerProviderCircuit() {
		AtomicInteger calls = new AtomicInteger();
		AssistantContextProvider failing = new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return descriptor(TEST);
			}

			@Override
			public ProviderResult query(ProviderQuery query) {
				calls.incrementAndGet();
				throw new IllegalStateException("down");
			}
		};
		AssistantProviderRegistry registry = new AssistantProviderRegistry(List.of(failing));

		for (int attempt = 0; attempt < 4; attempt++) {
			assertEquals(ProviderStatus.UNAVAILABLE, registry.query(TEST, query(TEST)).status());
		}

		assertEquals(3, calls.get());
	}

	private static ProviderDefinition definition(ProviderId providerId, boolean enabled, boolean selected,
			DataClassification classification) {
		return new ProviderDefinition(providerId, "matrix-fifth", PlatformStatus.ACTIVE, enabled, selected,
				classification, "{}", null, null, 1, Instant.now(), Instant.now());
	}

	private static ProviderQuery query(ProviderId providerId) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "run-1", IDENTITY.correlationId(),
				IDENTITY, "query", null, null, List.of());
	}

	private static AssistantContextProvider provider(ProviderId providerId, DataClassification classification) {
		return new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return descriptor(providerId);
			}

			@Override
			public ProviderResult query(ProviderQuery query) {
				ContextFragment fragment = new ContextFragment("fragment-1", providerId, "TEST", "1", "v1",
						FactType.DOMAIN_FACT, "title", "content", Instant.now(), Instant.now(), "hash", null,
						classification, 2);
				return new ProviderResult(AssistantContextContract.VERSION, providerId, ProviderStatus.OK,
						Instant.now(), 1, 0, false, List.of(fragment), List.of());
			}
		};
	}

	private static ProviderDescriptor descriptor(ProviderId providerId) {
		return new ProviderDescriptor(AssistantContextContract.VERSION, providerId, "Fifth", "test", "test",
				new ProviderCapabilities(true, true, true, true, true, false, false, List.of("TEST"), List.of()),
				new ProviderLimits(20, 12000, 3000));
	}

	private static final class FailingProvider implements AssistantContextProvider {

		@Override
		public ProviderDescriptor describe() {
			throw new IllegalStateException("service unavailable");
		}

		@Override
		public ProviderResult query(ProviderQuery query) {
			throw new IllegalStateException("service unavailable");
		}

	}

	private static final class FakeRepository implements AssistantProviderRepository {

		private final Map<ProviderId, ProviderDefinition> definitions = new LinkedHashMap<>();

		private final Map<ProviderId, TenantProviderSetting> settings = new LinkedHashMap<>();

		private FakeRepository(ProviderDefinition... definitions) {
			for (ProviderDefinition definition : definitions) {
				this.definitions.put(definition.providerId(), definition);
			}
		}

		@Override
		public List<ProviderDefinition> findDefinitions() {
			return new ArrayList<>(definitions.values());
		}

		@Override
		public Optional<ProviderDefinition> findDefinition(ProviderId providerId) {
			return Optional.ofNullable(definitions.get(providerId));
		}

		@Override
		public ProviderDefinition saveDefinition(ProviderDefinition definition, Long expectedVersion) {
			definitions.put(definition.providerId(), definition);
			return definition;
		}

		@Override
		public void saveDescriptorSnapshot(ProviderId providerId, ProviderDescriptor descriptor, Instant reconciledAt) {
		}

		@Override
		public List<TenantProviderSetting> findTenantSettings(String tenantId) {
			return new ArrayList<>(settings.values());
		}

		@Override
		public Optional<TenantProviderSetting> findTenantSetting(String tenantId, ProviderId providerId) {
			return Optional.ofNullable(settings.get(providerId));
		}

		@Override
		public TenantProviderSetting saveTenantSetting(TenantProviderSetting setting, Long expectedVersion) {
			settings.put(setting.providerId(), setting);
			return setting;
		}

	}

}
