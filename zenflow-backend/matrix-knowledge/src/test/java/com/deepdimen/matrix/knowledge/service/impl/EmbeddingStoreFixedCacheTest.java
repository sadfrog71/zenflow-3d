package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.support.handler.rag.EmbeddingStoreFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

class EmbeddingStoreFixedCacheTest {

	private final EmbeddingStoreFactory factory = mock(EmbeddingStoreFactory.class);

	private final EmbeddingStoreServiceImpl service = new EmbeddingStoreServiceImpl(Map.of("test", factory),
			mock(AiEmbedStoreMapper.class), mock(AiDatasetMapper.class));

	private final String collection = "fixed-cache-test";

	@AfterEach
	void cleanup() {
		for (long tenant : new long[] { 71001L, 71002L }) {
			TenantContextHolder.setTenantId(tenant);
			service.deleteEmbeddingStore(collection);
		}
		TenantContextHolder.clear();
	}

	private AiEmbedStoreEntity config() {
		TenantContextHolder.setTenantId(71001L);
		var config = new AiEmbedStoreEntity();
		config.setStoreId(91L);
		config.setStoreType("test");
		when(factory.support("test")).thenReturn(true);
		return config;
	}

	@Test
	void fixedVersionsReuseOnlyTheirOwnTenantAndConfiguration() throws Exception {
		var config = config();
		var first = new InMemoryEmbeddingStore<TextSegment>();
		var changed = new InMemoryEmbeddingStore<TextSegment>();
		var otherTenant = new InMemoryEmbeddingStore<TextSegment>();
		when(factory.createEmbeddingStore(config, collection, 3)).thenReturn(first, changed, otherTenant);
		assertSame(first, service.embeddingStoreFixed(collection, config, 3, "a".repeat(64)));
		assertSame(first, service.embeddingStoreFixed(collection, config, 3, "a".repeat(64)));
		assertSame(changed, service.embeddingStoreFixed(collection, config, 3, "b".repeat(64)));
		TenantContextHolder.setTenantId(71002L);
		assertSame(otherTenant, service.embeddingStoreFixed(collection, config, 3, "a".repeat(64)));
		verify(factory, times(3)).createEmbeddingStore(config, collection, 3);
	}

	@Test
	void fixedEntryDoesNotUseLegacyCacheAndCollectionInvalidationRemovesBoth() throws Exception {
		var config = config();
		var model = new AiModelEntity();
		model.setDimensions(3);
		var legacy = new InMemoryEmbeddingStore<TextSegment>();
		var fixed = new InMemoryEmbeddingStore<TextSegment>();
		var refreshed = new InMemoryEmbeddingStore<TextSegment>();
		when(factory.createEmbeddingStore(config, collection, 3)).thenReturn(legacy, fixed, refreshed);
		assertSame(legacy, service.embeddingStore(collection, config, model));
		assertSame(fixed, service.embeddingStoreFixed(collection, config, 3, "a".repeat(64)));
		assertSame(legacy, service.embeddingStore(collection));
		service.deleteEmbeddingStore(collection);
		assertSame(refreshed, service.embeddingStoreFixed(collection, config, 3, "a".repeat(64)));
	}

	@Test
	void rejectsMissingConfigurationAndDoesNotFallbackToMemory() {
		var config = config();
		assertThrows(IllegalArgumentException.class,
				() -> service.embeddingStoreFixed(collection, null, 3, "a".repeat(64)));
		assertThrows(IllegalArgumentException.class,
				() -> service.embeddingStoreFixed(collection, config, 0, "a".repeat(64)));
		assertThrows(IllegalArgumentException.class,
				() -> service.embeddingStoreFixed(collection, config, 3, "unverified"));
		when(factory.support("test")).thenReturn(false);
		assertThrows(IllegalStateException.class,
				() -> service.embeddingStoreFixed(collection, config, 3, "a".repeat(64)));
	}

}
