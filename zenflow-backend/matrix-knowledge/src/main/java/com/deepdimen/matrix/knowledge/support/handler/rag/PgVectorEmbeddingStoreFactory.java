package com.deepdimen.matrix.knowledge.support.handler.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.support.constant.EmbedStoreSupportEnums;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PgVector向量数据库工厂类 负责创建和配置PostgreSQL的向量扩展PgVector存储实例
 * PgVector是PostgreSQL的扩展，提供向量存储和相似度搜索功能
 *
 * @author caoyuan
 * @date 2025/2/11
 */
@Service
@RequiredArgsConstructor
public class PgVectorEmbeddingStoreFactory implements EmbeddingStoreFactory {

	/**
	 * 判断是否支持PgVector类型的向量存储
	 * @param storeType 向量存储类型
	 * @return 如果是PGVECTOR类型返回true，否则返回false
	 */
	@Override
	public boolean support(String storeType) {
		return EmbedStoreSupportEnums.PGVECTOR.getType().equals(storeType);
	}

	/**
	 * 创建PgVector向量存储实例 从extData中解析数据库连接参数，并创建PgVector存储
	 * @param embedStoreEntity 向量存储配置实体，包含主机、端口和扩展配置
	 * @param aiDatasetEntity AI数据集实体，包含表名（集合名称）
	 * @return 配置好的PgVector向量存储实例
	 * @throws Exception 创建过程中可能发生的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity,
			AiDatasetEntity aiDatasetEntity) throws Exception {
		// 从 extData 中解析配置信息
		JSONObject config = new JSONObject();
		if (StrUtil.isNotBlank(embedStoreEntity.getExtData())) {
			config = JSONUtil.parseObj(embedStoreEntity.getExtData());
		}

		// 获取配置信息
		String username = config.getStr("username", "postgres");
		String password = config.getStr("password", "postgres");
		String database = config.getStr("database", "database");
		int dimension = config.getInt("dimension", 1024);

		String host = embedStoreEntity.getHost();
		Integer port = embedStoreEntity.getPort();

		// 构建 PgVector EmbeddingStore
		return PgVectorEmbeddingStore.builder()
			.host(host)
			.port(port)
			.database(database)
			.user(username)
			.password(password)
			.dimension(dimension)
			.table(aiDatasetEntity.getCollectionName())
			.createTable(true)
			.dropTableFirst(false)
			.build();
	}

	/**
	 * 创建PgVector向量存储实例 使用指定的集合名称和维度创建向量存储
	 * @param embedStoreEntity 向量存储配置实体，包含主机、端口和扩展配置
	 * @param collectionName 表名（集合名称）
	 * @param dimension 向量维度
	 * @return 配置好的PgVector向量存储实例
	 * @throws Exception 创建过程中可能发生的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception {
		// 从 extData 中解析配置信息
		JSONObject config = new JSONObject();
		if (StrUtil.isNotBlank(embedStoreEntity.getExtData())) {
			config = JSONUtil.parseObj(embedStoreEntity.getExtData());
		}

		// 获取配置信息
		String username = config.getStr("username", "postgres");
		String password = config.getStr("password", "postgres");
		String database = config.getStr("database", "database");

		String host = embedStoreEntity.getHost();
		Integer port = embedStoreEntity.getPort();

		// 构建 PgVector EmbeddingStore
		return PgVectorEmbeddingStore.builder()
			.host(host)
			.port(port)
			.database(database)
			.user(username)
			.password(password)
			.dimension(dimension)
			.table(collectionName)
			.createTable(true)
			.dropTableFirst(false)
			.build();
	}

}