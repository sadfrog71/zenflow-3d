package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.handler.rag.EmbeddingStoreFactory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嵌入向量存储服务实现类
 * <p>
 * 管理不同集合的向量存储实例 支持多种向量数据库类型 提供缓存机制优化访问性能
 *
 * @author caoyuan
 * @date 2024/9/26
 */
@Service
@RequiredArgsConstructor
public class EmbeddingStoreServiceImpl implements EmbeddingStoreService {

	private static final long STORE_RETRY_COOLDOWN_MILLIS = 30_000L;

	/**
	 * 嵌入存储实例缓存 避免重复创建存储实例
	 */
	public final static Map<String, EmbeddingStore<TextSegment>> EMBEDDING_STORE_MAP = new ConcurrentHashMap<>();

	private final static Map<String, Long> STORE_RETRY_AFTER = new ConcurrentHashMap<>();

	private final static Map<String, Object> STORE_CREATION_LOCKS = new ConcurrentHashMap<>();

	/**
	 * 嵌入存储工厂集合 按存储类型创建对应实例
	 */
	private final Map<String, EmbeddingStoreFactory> embeddingStoreFactoryMap;

	/**
	 * 嵌入存储配置数据访问层
	 */
	private final AiEmbedStoreMapper aiEmbedStoreMapper;

	/**
	 * 数据集数据访问层
	 */
	private final AiDatasetMapper aiDatasetMapper;

	/**
	 * 获取嵌入存储实例
	 * <p>
	 * 优先从缓存获取，缓存未命中则创建新实例 根据数据集配置选择对应的存储类型 无配置时默认使用内存存储
	 * @param collectionName 集合名称
	 * @return 嵌入存储实例
	 */
	@Override
	@SneakyThrows
	public EmbeddingStore<TextSegment> embeddingStore(String collectionName) {
		String tenantCollectionKey = tenantKey(collectionName);
		EmbeddingStore<TextSegment> embeddingStore = EMBEDDING_STORE_MAP.get(tenantCollectionKey);

		if (Objects.nonNull(embeddingStore)) {
			return embeddingStore;
		}

		// 根据ID 查询知识库
		AiDatasetEntity aiDatasetEntity = aiDatasetMapper.selectOne(
				Wrappers.<AiDatasetEntity>lambdaQuery().eq(AiDatasetEntity::getCollectionName, collectionName), false);

		// 知识库不存在
		AiEmbedStoreEntity embedStoreEntity = null;
		if (Objects.isNull(aiDatasetEntity)) {
			aiDatasetEntity = new AiDatasetEntity();
			aiDatasetEntity.setCollectionName(collectionName);
			// 使用第一个存储配置
			embedStoreEntity = aiEmbedStoreMapper.selectOne(
					Wrappers.<AiEmbedStoreEntity>lambdaQuery().orderByAsc(AiEmbedStoreEntity::getStoreId), false);
		}
		else {
			embedStoreEntity = aiEmbedStoreMapper.selectOne(Wrappers.<AiEmbedStoreEntity>lambdaQuery()
				.eq(AiEmbedStoreEntity::getStoreId, aiDatasetEntity.getStoreId()), false);

		}

		// 没有配置信息，使用内存存储
		if (Objects.isNull(embedStoreEntity)) {
			embeddingStore = new InMemoryEmbeddingStore<>();
			EMBEDDING_STORE_MAP.put(tenantCollectionKey, embeddingStore);
			return embeddingStore;
		}

		Long storeId = Objects.requireNonNullElse(embedStoreEntity.getStoreId(), Long.MIN_VALUE);
		String tenantStoreKey = tenantKey(String.valueOf(storeId));
		Object creationLock = STORE_CREATION_LOCKS.computeIfAbsent(tenantStoreKey, ignored -> new Object());
		synchronized (creationLock) {
			embeddingStore = EMBEDDING_STORE_MAP.get(tenantCollectionKey);
			if (Objects.nonNull(embeddingStore)) {
				return embeddingStore;
			}
			assertStoreRetryAllowed(tenantStoreKey);
			try {
				for (EmbeddingStoreFactory storeFactory : embeddingStoreFactoryMap.values()) {
					if (storeFactory.support(embedStoreEntity.getStoreType())) {
						embeddingStore = storeFactory.createEmbeddingStore(embedStoreEntity, aiDatasetEntity);
						break;
					}
				}
				if (embeddingStore == null) {
					throw new IllegalStateException("未找到可用的向量库工厂：" + embedStoreEntity.getStoreType());
				}
				STORE_RETRY_AFTER.remove(tenantStoreKey);
				EMBEDDING_STORE_MAP.put(tenantCollectionKey, embeddingStore);
				return embeddingStore;
			}
			catch (Exception ex) {
				STORE_RETRY_AFTER.put(tenantStoreKey, System.currentTimeMillis() + STORE_RETRY_COOLDOWN_MILLIS);
				throw ex;
			}
		}
	}

	/**
	 * 根据嵌入存储实体和模型实体获取对应的嵌入存储实例
	 * @param collectionName 集合名称
	 * @param embedStoreEntity 嵌入存储配置实体
	 * @param modelEntity AI模型配置实体
	 * @return 文本片段的嵌入存储实例
	 */
	@Override
	public EmbeddingStore<TextSegment> embeddingStore(String collectionName, AiEmbedStoreEntity embedStoreEntity,
			AiModelEntity modelEntity) {
		Long storeId = Objects.requireNonNullElse(embedStoreEntity.getStoreId(), Long.MIN_VALUE);
		return explicitStore(collectionName, embedStoreEntity, modelEntity.getDimensions(), tenantKey(collectionName),
				tenantKey(String.valueOf(storeId)));
	}

	@Override
	public EmbeddingStore<TextSegment> embeddingStoreFixed(String collectionName, AiEmbedStoreEntity embedStoreEntity,
			int dimensions, String configurationDigest) {
		validateFixedConfiguration(collectionName, embedStoreEntity, dimensions, configurationDigest);
		String key = tenantKey(collectionName) + "\0fixed:" + configurationDigest + ":" + dimensions;
		return explicitStore(collectionName, embedStoreEntity, dimensions, key, key);
	}

	public static void validateFixedConfiguration(String collectionName, AiEmbedStoreEntity embedStoreEntity,
			int dimensions, String configurationDigest) {
		if (collectionName == null || collectionName.isBlank() || collectionName.indexOf('\0') >= 0
				|| embedStoreEntity == null || embedStoreEntity.getStoreId() == null
				|| embedStoreEntity.getStoreId() <= 0 || embedStoreEntity.getStoreType() == null
				|| embedStoreEntity.getStoreType().isBlank() || dimensions <= 0 || configurationDigest == null
				|| !configurationDigest.matches("[a-f0-9]{64}"))
			throw new IllegalArgumentException("KNOWLEDGE_CONFIGURATION_UNVERIFIED");
	}

	@SneakyThrows
	private EmbeddingStore<TextSegment> explicitStore(String collectionName, AiEmbedStoreEntity embedStoreEntity,
			Integer dimensions, String tenantCollectionKey, String tenantStoreKey) {
		EmbeddingStore<TextSegment> embeddingStore = EMBEDDING_STORE_MAP.get(tenantCollectionKey);

		if (Objects.nonNull(embeddingStore)) {
			return embeddingStore;
		}

		// 使用工厂创建嵌入存储实例
		Object creationLock = STORE_CREATION_LOCKS.computeIfAbsent(tenantStoreKey, ignored -> new Object());
		synchronized (creationLock) {
			embeddingStore = EMBEDDING_STORE_MAP.get(tenantCollectionKey);
			if (Objects.nonNull(embeddingStore)) {
				return embeddingStore;
			}
			assertStoreRetryAllowed(tenantStoreKey);
			try {
				for (EmbeddingStoreFactory storeFactory : embeddingStoreFactoryMap.values()) {
					if (storeFactory.support(embedStoreEntity.getStoreType())) {
						embeddingStore = storeFactory.createEmbeddingStore(embedStoreEntity, collectionName,
								dimensions);
						break;
					}
				}
				if (embeddingStore == null) {
					throw new IllegalStateException("未找到可用的向量库工厂：" + embedStoreEntity.getStoreType());
				}
				STORE_RETRY_AFTER.remove(tenantStoreKey);
				EMBEDDING_STORE_MAP.put(tenantCollectionKey, embeddingStore);
				return embeddingStore;
			}
			catch (Exception ex) {
				STORE_RETRY_AFTER.put(tenantStoreKey, System.currentTimeMillis() + STORE_RETRY_COOLDOWN_MILLIS);
				throw ex;
			}
		}
	}

	/**
	 * 删除嵌入存储缓存
	 * <p>
	 * 从缓存中移除指定集合的存储实例 下次访问时将重新创建
	 * @param name 集合名称
	 */
	@Override
	public void deleteEmbeddingStore(String name) {
		String tenantCollectionKey = tenantKey(name);
		EMBEDDING_STORE_MAP.remove(tenantCollectionKey);
		EMBEDDING_STORE_MAP.keySet().removeIf(key -> key.startsWith(tenantCollectionKey + "\0fixed:"));
		String tenantPrefix = tenantCollectionKey.substring(0, tenantCollectionKey.indexOf(':') + 1);
		STORE_RETRY_AFTER.keySet().removeIf(key -> key.startsWith(tenantPrefix));
		STORE_CREATION_LOCKS.keySet().removeIf(key -> key.startsWith(tenantPrefix));
	}

	private void assertStoreRetryAllowed(String tenantStoreKey) {
		Long retryAfter = STORE_RETRY_AFTER.get(tenantStoreKey);
		if (retryAfter == null) {
			return;
		}
		long remaining = retryAfter - System.currentTimeMillis();
		if (remaining > 0) {
			throw new IllegalStateException("向量库连接暂不可用，请稍后重试");
		}
		STORE_RETRY_AFTER.remove(tenantStoreKey, retryAfter);
	}

	static String tenantKey(String value) {
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("向量库操作缺少租户上下文");
		}
		return tenantId + ":" + value;
	}

	/**
	 * 批量删除向量数据
	 * <p>
	 * 从指定集合中删除多个向量 根据向量ID列表执行批量删除操作
	 * @param collectionName 集合名称
	 * @param idList 要删除的向量ID列表
	 */
	@Override
	@SneakyThrows
	public void delete(String collectionName, List<String> idList) {
		if (CollUtil.isEmpty(idList)) {
			return;
		}
		this.embeddingStore(collectionName).removeAll(idList);
	}

}
