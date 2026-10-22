package com.deepdimen.matrix.knowledge.support.handler.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.neo4j.embedding.Neo4jEmbeddingStore;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.support.constant.EmbedStoreSupportEnums;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class Neo4jEmbeddingStoreFactory implements EmbeddingStoreFactory {

	public static Map<String, Driver> DRIVER_MAP = new HashMap<>();

	/** 默认检索查询语句 */
	private static final String DEFAULT_RETRIEVAL = """
			MATCH (node)<-[:HAS_SUMMARY]-(parent)
			WITH parent, max(score) AS score, node // deduplicate parents
			RETURN parent.text AS text, score, properties(node) AS metadata
			ORDER BY score DESC
			LIMIT $maxResults""";

	/** 默认父节点查询语句 */
	private static final String DEFAULT_PARENT_QUERY = """
			UNWIND $rows AS row
			MATCH (p:SummaryChunk {parentId: $parentId})
			CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
			SET u += row.%3$s
			WITH row, u
			CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
			RETURN count(*)""";

	/**
	 * 判断是否支持指定的存储类型
	 * @param storeType 存储类型
	 * @return 如果支持指定的存储类型则返回true，否则返回false
	 */
	@Override
	public boolean support(String storeType) {
		return EmbedStoreSupportEnums.NEO4J.getType().equals(storeType);
	}

	/**
	 * 创建Neo4j向量存储
	 * @param embedStoreEntity 向量存储实体，包含扩展配置数据
	 * @param aiDatasetEntity 数据集实体
	 * @return 构建好的Neo4j向量存储实例
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity,
			AiDatasetEntity aiDatasetEntity) {

		// 从 extData 中解析配置信息
		JSONObject config = new JSONObject();
		if (StrUtil.isNotBlank(embedStoreEntity.getExtData())) {
			config = JSONUtil.parseObj(embedStoreEntity.getExtData());
		}

		String username = config.getStr("username", "neo4j");
		String password = config.getStr("password", "neo4jneo4j");

		Driver driver = GraphDatabase.driver(embedStoreEntity.getUri(), AuthTokens.basic(username, password));

		int dimension = config.getInt("dimension", 1024);
		String label = config.getStr("label", "Summary");
		String database = config.getStr("database", "neo4j");
		String indexName = config.getStr("indexName", aiDatasetEntity.getCollectionName());

		DRIVER_MAP.put(aiDatasetEntity.getCollectionName(), driver);
		return Neo4jEmbeddingStore.builder()
			.driver(driver)
			.retrievalQuery(DEFAULT_RETRIEVAL)
			.entityCreationQuery(DEFAULT_PARENT_QUERY)
			.label(label)
			.databaseName(database)
			.indexName(indexName)
			.dimension(dimension)
			.build();
	}

	/**
	 * 创建Neo4j向量存储实例 使用指定的集合名称和维度创建向量存储
	 * @param embedStoreEntity 向量存储配置实体，包含URI和扩展配置
	 * @param collectionName 集合名称（用作索引名称）
	 * @param dimension 向量维度
	 * @return 配置好的Neo4j向量存储实例
	 * @throws Exception 创建过程中可能出现的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception {
		// 从 extData 中解析配置信息
		JSONObject config = new JSONObject();
		if (StrUtil.isNotBlank(embedStoreEntity.getExtData())) {
			config = JSONUtil.parseObj(embedStoreEntity.getExtData());
		}

		String username = config.getStr("username", "neo4j");
		String password = config.getStr("password", "neo4jneo4j");

		Driver driver = GraphDatabase.driver(embedStoreEntity.getUri(), AuthTokens.basic(username, password));

		String label = config.getStr("label", "Summary");
		String database = config.getStr("database", "neo4j");
		String indexName = config.getStr("indexName", collectionName);

		DRIVER_MAP.put(collectionName, driver);
		return Neo4jEmbeddingStore.builder()
			.driver(driver)
			.retrievalQuery(DEFAULT_RETRIEVAL)
			.entityCreationQuery(DEFAULT_PARENT_QUERY)
			.label(label)
			.databaseName(database)
			.indexName(indexName)
			.dimension(dimension)
			.build();
	}

	/**
	 * 根据集合名称获取对应的Driver实例
	 * @param collectionName 集合名称
	 * @return 对应的Driver实例
	 */
	public Driver getDriver(String collectionName) {
		Driver driver = DRIVER_MAP.get(collectionName);
		if (Objects.nonNull(driver) && Objects.nonNull(driver.session())) {
			return driver;
		}

		AiDatasetMapper datasetMapper = SpringUtil.getBean(AiDatasetMapper.class);
		AiDatasetEntity datasetEntity = datasetMapper
			.selectOne(Wrappers.<AiDatasetEntity>lambdaQuery().eq(AiDatasetEntity::getCollectionName, collectionName));
		Long storeId = datasetEntity.getStoreId();
		AiEmbedStoreMapper embedStoreMapper = SpringUtil.getBean(AiEmbedStoreMapper.class);
		AiEmbedStoreEntity embedStoreEntity = embedStoreMapper.selectById(storeId);

		// 从 extData 中解析配置信息
		JSONObject config = new JSONObject();
		if (StrUtil.isNotBlank(embedStoreEntity.getExtData())) {
			config = JSONUtil.parseObj(embedStoreEntity.getExtData());
		}

		String username = config.getStr("username", "neo4j");
		String password = config.getStr("password", "neo4jneo4j");

		Driver drivered = GraphDatabase.driver(embedStoreEntity.getUri(), AuthTokens.basic(username, password));
		DRIVER_MAP.put(collectionName, drivered);
		return drivered;
	}

}
