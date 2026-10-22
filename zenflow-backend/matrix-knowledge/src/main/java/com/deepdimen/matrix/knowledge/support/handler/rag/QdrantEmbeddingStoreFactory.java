package com.deepdimen.matrix.knowledge.support.handler.rag;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.support.constant.EmbedStoreSupportEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.provider.QdrantEmbeddingStoreProvider;
import com.deepdimen.matrix.knowledge.support.provider.QdrantDenseCompatibilityClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Qdrant向量数据库工厂类 负责创建和配置Qdrant向量存储实例 Qdrant是一个高性能的向量搜索引擎，支持过滤和负载优化
 *
 * @author caoyuan
 * @date 2025/2/11
 */
@Service
@RequiredArgsConstructor
public class QdrantEmbeddingStoreFactory implements EmbeddingStoreFactory {

	private final ModelProvider modelProvider;

	/**
	 * 判断是否支持Qdrant类型的向量存储
	 * @param storeType 向量存储类型
	 * @return 如果是QDRANT类型返回true，否则返回false
	 */
	@Override
	public boolean support(String storeType) {
		return EmbedStoreSupportEnums.QDRANT.getType().equals(storeType);
	}

	/**
	 * 创建Qdrant向量存储实例 配置gRPC客户端连接，创建集合并设置向量参数
	 * @param embedStoreEntity 向量存储配置实体，包含主机、端口、API密钥等信息
	 * @param aiDatasetEntity AI数据集实体，包含集合名称和嵌入模型信息
	 * @return 配置好的Qdrant向量存储实例
	 * @throws Exception 创建过程中可能出现的异常，如连接失败或集合创建失败
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity,
			AiDatasetEntity aiDatasetEntity) throws Exception {
		QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(embedStoreEntity.getHost(),
				embedStoreEntity.getPort(), BooleanUtil.toBoolean(embedStoreEntity.getUseTls()));
		if (StrUtil.isNotBlank(embedStoreEntity.getApiKey())) {
			grpcClientBuilder.withApiKey(embedStoreEntity.getApiKey());
		}
		QdrantClient qdrantClient = new QdrantClient(grpcClientBuilder.build());

		// 创建集合,使用知识库的ID作为名称
		DimensionAwareEmbeddingModel embeddingModel = modelProvider
			.getEmbeddingModel(aiDatasetEntity.getEmbeddingModel());
		var vectorParams = Collections.VectorParams.newBuilder()
			.setDistance(Collections.Distance.Cosine)
			.setSize(embeddingModel.dimension())
			.build();

		// 创建集合
		if (!qdrantClient.collectionExistsAsync(aiDatasetEntity.getCollectionName()).get()) {
			qdrantClient.createCollectionAsync(aiDatasetEntity.getCollectionName(), vectorParams).get();
		}

		return new QdrantEmbeddingStoreProvider(qdrantClient, aiDatasetEntity.getCollectionName(), "doc_content");
	}

	/**
	 * 创建Qdrant向量存储实例 使用指定的集合名称和维度创建向量存储
	 * @param embedStoreEntity 向量存储配置实体，包含主机、端口、API密钥等信息
	 * @param collectionName 集合名称
	 * @param dimension 向量维度
	 * @return 配置好的Qdrant向量存储实例
	 * @throws Exception 创建过程中可能出现的异常，如连接失败或集合创建失败
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception {
		return createFixedStore(embedStoreEntity, collectionName, dimension);
	}

	/**
	 * Explicit configured entry; does not load any default dataset/model or a legacy
	 * ModelProvider.
	 */
	public static EmbeddingStore<TextSegment> createFixedStore(AiEmbedStoreEntity embedStoreEntity,
			String collectionName, Integer dimension) throws Exception {
		return createFixedResource(embedStoreEntity, collectionName, dimension).store();
	}

	public record FixedResource(EmbeddingStore<TextSegment> store, AutoCloseable owner) implements AutoCloseable {
		@Override
		public void close() throws Exception {
			owner.close();
		}
	}

	public static FixedResource createFixedResource(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception {
		QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient
			.newBuilder(embedStoreEntity.getHost(), embedStoreEntity.getPort(),
					BooleanUtil.toBoolean(embedStoreEntity.getUseTls()))
			.withTimeout(java.time.Duration.ofSeconds(20));
		if (StrUtil.isNotBlank(embedStoreEntity.getApiKey())) {
			grpcClientBuilder.withApiKey(embedStoreEntity.getApiKey());
		}

		// 创建集合,使用传入的集合名称和维度
		var vectorParams = Collections.VectorParams.newBuilder()
			.setDistance(Collections.Distance.Cosine)
			.setSize(dimension)
			.build();

		QdrantClient qdrantClient = new QdrantDenseCompatibilityClient(grpcClientBuilder.build());

		try {
			if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
				qdrantClient.createCollectionAsync(collectionName, vectorParams).get();
			}
			return new FixedResource(new QdrantEmbeddingStoreProvider(qdrantClient, collectionName, "doc_content"),
					qdrantClient::close);
		}
		catch (Exception failure) {
			try {
				qdrantClient.close();
			}
			catch (Exception cleanup) {
				failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

}
