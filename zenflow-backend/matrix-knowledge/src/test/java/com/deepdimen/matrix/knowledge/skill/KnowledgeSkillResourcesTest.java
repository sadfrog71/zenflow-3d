package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.context.KnowledgeDatasetAccessService;
import com.deepdimen.matrix.knowledge.entity.*;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.skill.api.AgentSkillTargetReader;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class KnowledgeSkillResourcesTest {

	final ObjectMapper json = new ObjectMapper();

	final KnowledgeMarketConfigurationReader configs = mock(KnowledgeMarketConfigurationReader.class);

	final KnowledgeDatasetAccessService access = mock(KnowledgeDatasetAccessService.class);

	final AiModelMapper models = mock(AiModelMapper.class);

	final AiEmbedStoreMapper stores = mock(AiEmbedStoreMapper.class);

	final AiEmbedStoreEntity store = new AiEmbedStoreEntity();

	final ObjectProvider<AssistantIdentityResolver> identities = mock(ObjectProvider.class);

	final AssistantIdentityResolver resolver = mock(AssistantIdentityResolver.class);

	final KnowledgeSkillResources resources = new KnowledgeSkillResources(configs, access, models, stores, identities,
			json);

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final AiModelEntity model = new AiModelEntity();

	final AiDatasetEntity dataset = new AiDatasetEntity();

	final AiModelEntity embedding = new AiModelEntity();

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		when(identities.getIfAvailable()).thenReturn(resolver);
		when(resolver.currentIdentity()).thenReturn(new IdentityEnvelope("7", "3", null, List.of(), List.of("9"),
				List.of(), "inspect", "ASSISTANT_CONTEXT"));
		var config = KnowledgeMarketConfigurationSnapshot.capture(
				new AgentSkillTargetReader.Target("88", "knowledge",
						"{\"datasetIds\":[\"31\"],\"modelConfig\":{\"provider\":\"local\",\"modelName\":\"qwen\"}}"),
				json);
		when(configs.read(consumer)).thenReturn(config);
		model.setId(77L);
		model.setModelType("CHAT");
		model.setProvider("local");
		model.setModelName("qwen");
		model.setApiKey("fixture-secret");
		when(models.selectById(77L)).thenReturn(model);
		embedding.setId(78L);
		embedding.setName("kb-embedding");
		embedding.setModelType("Embedding");
		embedding.setModelName("embed-v1");
		when(models.selectList(any())).thenReturn(List.of(embedding));
		dataset.setEmbeddingModel("kb-embedding");
		dataset.setId(31L);
		dataset.setStoreId(91L);
		store.setStoreId(91L);
		store.setStoreType("milvus");
		store.setApiKey("fixture-store-secret");
		when(stores.selectById(91L)).thenReturn(store);
		dataset.setTenantId("7");
		dataset.setCollectionName("kb7");
		dataset.setTopK(5);
		when(access.accessibleDatasets(any(), eq(List.of(31L)))).thenReturn(List.of(dataset));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	ResolvedSkillSnapshot release(KnowledgeSkillResources.Capture capture) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", "KNOWLEDGE", "77", capture.digest()), "{}", "{}", "rules",
				capture.resources(), "{}", List.of());
	}

	@Test
	void readsExistingModelAndPermissionsAndReturnsOnlyFingerprints() {
		var capture = resources.capture(consumer, "77");
		var verified = resources.verify(identity, consumer, release(capture));
		assertEquals(capture.digest(), verified.digest());
		assertEquals("MARKET_SKILL", verified.identity().purpose());
		assertEquals("market-skill:99", verified.identity().correlationId());
		assertFalse(capture.toString().contains("fixture-secret"));
		assertEquals(3, capture.resources().size());
		var used = org.mockito.ArgumentCaptor.forClass(IdentityEnvelope.class);
		verify(access, times(2)).accessibleDatasets(used.capture(), eq(List.of(31L)));
		assertEquals(List.of("9"), used.getValue().roleIds());
	}

	@Test
	void storageChangesInvalidateReleaseAndMissingStoreCannotFallback() {
		var captured = resources.capture(consumer, "77");
		assertFalse(captured.toString().contains("fixture-store-secret"));
		store.setUri("changed-endpoint");
		assertThrows(IllegalArgumentException.class, () -> resources.verify(identity, consumer, release(captured)));
		when(stores.selectById(91L)).thenReturn(null);
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "77"));
	}

	@Test
	void readinessUsesUniqueConfiguredChatModelAndNeverChoosesAnAmbiguousDefault() {
		when(models.selectList(any())).thenReturn(List.of(model), List.of(embedding));
		assertEquals("77", resources.captureConfigured(consumer).modelId());
		when(models.selectList(any())).thenReturn(List.of(model, model));
		assertThrows(IllegalArgumentException.class, () -> resources.captureConfigured(consumer));
	}

	@Test
	void countersDoNotChangeConfigurationButRetrievalSettingsDo() {
		var before = resources.capture(consumer, "77");
		dataset.setUnits(100L);
		dataset.setFileSize(200L);
		dataset.setName("renamed");
		assertEquals(before.digest(), resources.capture(consumer, "77").digest());
		dataset.setTopK(6);
		assertThrows(IllegalArgumentException.class, () -> resources.verify(identity, consumer, release(before)));
	}

	@Test
	void modelIdentityAndCredentialRotationCannotSilentlyReuseOldRelease() {
		var before = resources.capture(consumer, "77");
		model.setApiKey("rotated");
		assertThrows(IllegalArgumentException.class, () -> resources.verify(identity, consumer, release(before)));
		model.setModelName("other");
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "77"));
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "default"));
	}

	@Test
	void retrievalModelChangesInvalidateReleaseEvenWhenDatasetNameIsUnchanged() {
		var before = resources.capture(consumer, "77");
		embedding.setApiKey("rotated-embedding-secret");
		assertThrows(IllegalArgumentException.class, () -> resources.verify(identity, consumer, release(before)));
		assertFalse(resources.capture(consumer, "77").toString().contains("rotated-embedding-secret"));
	}

	@Test
	void missingAmbiguousAndWrongTypeRetrievalModelsDoNotUseDefaults() {
		dataset.setEmbeddingModel(null);
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "77"));
		dataset.setEmbeddingModel("kb-embedding");
		when(models.selectList(any())).thenReturn(List.of());
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "77"));
		when(models.selectList(any())).thenReturn(List.of(embedding, embedding));
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "77"));
		when(models.selectList(any())).thenReturn(List.of(embedding));
		embedding.setModelType("CHAT");
		assertThrows(IllegalArgumentException.class, () -> resources.capture(consumer, "77"));
	}

	@Test
	void configuredRerankerMustResolveAndItsConfigurationIsPinned() {
		var reranker = new AiModelEntity();
		reranker.setId(79L);
		reranker.setName("kb-reranker");
		reranker.setModelType("Reranker");
		dataset.setRerankerModel("kb-reranker");
		when(models.selectList(any())).thenReturn(List.of(embedding), List.of(reranker));
		var before = resources.capture(consumer, "77");
		reranker.setBaseUrl("https://changed.example");
		when(models.selectList(any())).thenReturn(List.of(embedding), List.of(reranker));
		assertThrows(IllegalArgumentException.class, () -> resources.verify(identity, consumer, release(before)));
	}

	@Test void deniedOrCrossTenantDatasetsCannotProduceReleaseResources(){
		when(access.accessibleDatasets(any(),any())).thenReturn(List.of());assertThrows(org.springframework.security.access.AccessDeniedException.class,()->resources.capture(consumer,"77"));
		when(access.accessibleDatasets(any(),any())).thenReturn(List.of(dataset));dataset.setTenantId("8");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->resources.capture(consumer,"77"));
	}

	@Test
	void resolvedRetrievalUsesOnlyPinnedAuthorizedDatasetAndModel() {
		var snapshot = release(resources.capture(consumer, "77"));
		var resolved = resources.resolveRetrieval(identity, consumer, snapshot, 31L);
		assertSame(embedding, resolved.embedding());
		assertSame(dataset, resolved.dataset());
		assertNull(resolved.reranker());
		assertThrows(org.springframework.security.access.AccessDeniedException.class,
				() -> resources.resolveRetrieval(identity, consumer, snapshot, 32L));
		when(access.accessibleDatasets(any(), eq(List.of(31L)))).thenReturn(List.of(dataset), List.of());
		assertThrows(org.springframework.security.access.AccessDeniedException.class,
				() -> resources.resolveRetrieval(identity, consumer, snapshot, 31L));
	}

	@Test
	void missingIdentityBridgeAndImpersonationDoNotFallback() {
		var before = resources.capture(consumer, "77");
		assertThrows(org.springframework.security.access.AccessDeniedException.class,
				() -> resources.verify(identity, null, release(before)));
		assertThrows(org.springframework.security.access.AccessDeniedException.class,
				() -> resources.verify(new Identity("7", "4", "4", "market-skill:99"), consumer, release(before)));
		when(identities.getIfAvailable()).thenReturn(null);
		assertThrows(IllegalStateException.class, () -> resources.capture(consumer, "77"));
	}

}
