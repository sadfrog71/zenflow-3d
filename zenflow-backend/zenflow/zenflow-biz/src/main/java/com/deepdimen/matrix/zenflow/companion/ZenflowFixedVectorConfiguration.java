package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.service.impl.EmbeddingStoreServiceImpl;
import com.deepdimen.matrix.knowledge.support.handler.rag.QdrantEmbeddingStoreFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Only the original fixed-store port is assembled; enterprise flows and implicit defaults
 * stay unavailable.
 */
@Configuration(proxyBeanMethods = false)
public class ZenflowFixedVectorConfiguration {

	@Bean(destroyMethod = "close")
	public ZenflowVectorStorePool invelleVectorStorePool() {
		return new ZenflowVectorStorePool(16, 32);
	}

	@Bean
	public EmbeddingStoreService invelleFixedEmbeddingStores(AiEmbedStoreMapper stores, AiDatasetMapper datasets,
			ZenflowVectorStorePool pool, com.deepdimen.matrix.zenflow.identity.ZenflowProperties product) {
		return new EmbeddingStoreServiceImpl(Map.of(), stores, datasets) {
			@Override
			public EmbeddingStore<TextSegment> embeddingStoreFixed(String collection, AiEmbedStoreEntity store,
					int dimensions, String digest) {
				EmbeddingStoreServiceImpl.validateFixedConfiguration(collection, store, dimensions, digest);
				if (!java.util.Objects.equals(com.deepdimen.matrix.common.data.tenant.TenantContextHolder.getTenantId(),
						product.getTenantId()))
					throw unsupported();
				var frozen = new AiEmbedStoreEntity();
				frozen.setStoreId(store.getStoreId());
				frozen.setStoreType(store.getStoreType());
				frozen.setHost(store.getHost());
				frozen.setPort(store.getPort());
				frozen.setUseTls(store.getUseTls());
				frozen.setApiKey(store.getApiKey());
				return pool.handle(product.getTenantId() + ":" + collection + "\0fixed:" + digest + ":" + dimensions,
						() -> QdrantEmbeddingStoreFactory.createFixedResource(frozen, collection, dimensions));
			}

			@Override
			public void deleteEmbeddingStore(String collection) {
				if (!java.util.Objects.equals(com.deepdimen.matrix.common.data.tenant.TenantContextHolder.getTenantId(),
						product.getTenantId()))
					throw unsupported();
				pool.invalidate(product.getTenantId() + ":" + collection + "\0fixed:");
			}

			@Override
			public EmbeddingStore<TextSegment> embeddingStore(String collection) {
				throw unsupported();
			}

			@Override
			public EmbeddingStore<TextSegment> embeddingStore(String collection, AiEmbedStoreEntity store,
					AiModelEntity model) {
				throw unsupported();
			}

			@Override
			public void delete(String collection, List<String> ids) {
				throw unsupported();
			}
		};
	}

	private static IllegalStateException unsupported() {
		return new IllegalStateException("INVELLE_FIXED_VECTOR_CONFIGURATION_REQUIRED");
	}

}
