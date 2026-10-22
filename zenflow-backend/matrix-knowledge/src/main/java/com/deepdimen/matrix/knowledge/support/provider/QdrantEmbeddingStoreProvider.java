package com.deepdimen.matrix.knowledge.support.provider;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;

/**
 * Qdrant向量存储扩展实现
 * <p>
 * 扩展原生的QdrantEmbeddingStore，提供批量删除功能 Qdrant是一个高性能的向量搜索引擎，支持海量向量数据的存储和检索 本类主要增强了向量数据的管理功能
 *
 * @author caoyuan
 * @date 2025/2/22
 */
public class QdrantEmbeddingStoreProvider extends QdrantEmbeddingStore {

	/**
	 * Qdrant客户端实例
	 */
	private QdrantClient qdrantClient;

	/**
	 * 集合名称
	 */
	private String collectionName;

	/**
	 * 构造函数 - 通过连接参数创建
	 * @param collectionName Qdrant集合名称
	 * @param host Qdrant服务主机地址
	 * @param port Qdrant服务GRPC端口
	 * @param useTls 是否使用TLS(HTTPS)连接
	 * @param payloadTextKey 在Qdrant payload中存储文本段的字段名
	 * @param apiKey Qdrant API密钥（用于身份验证）
	 */
	public QdrantEmbeddingStoreProvider(String collectionName, String host, int port, boolean useTls,
			String payloadTextKey, @Nullable String apiKey) {
		super(collectionName, host, port, useTls, payloadTextKey, apiKey);
	}

	/**
	 * 构造函数 - 通过客户端实例创建
	 * @param client Qdrant客户端实例
	 * @param collectionName Qdrant集合名称
	 * @param payloadTextKey 在Qdrant payload中存储文本段的字段名
	 */
	public QdrantEmbeddingStoreProvider(QdrantClient client, String collectionName, String payloadTextKey) {
		super(client, collectionName, payloadTextKey);
		this.qdrantClient = client;
		this.collectionName = collectionName;
	}

	/**
	 * 批量删除向量数据
	 * <p>
	 * 根据ID列表批量删除存储在Qdrant中的向量数据
	 * @param idList 要删除的向量ID列表
	 */
	@SneakyThrows
	@Override
	public void removeAll(Collection<String> idList) {
		List<Points.PointId> ids = idList.stream().map(id -> id(UUID.fromString(id))).toList();
		this.qdrantClient.deleteAsync(collectionName, ids).get();
	}

}
