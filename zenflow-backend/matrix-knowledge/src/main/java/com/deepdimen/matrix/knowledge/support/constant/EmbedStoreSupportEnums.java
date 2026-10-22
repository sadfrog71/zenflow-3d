package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 向量数据库支持枚举
 * <p>
 * 定义了系统支持的各种向量数据库类型，用于存储和检索向量化的数据
 *
 * @author caoyuan
 * @date 2025/2/11
 */
@RequiredArgsConstructor
public enum EmbedStoreSupportEnums {

	/**
	 * 内存向量库
	 * <p>
	 * 数据存储在内存中，重启后数据会丢失，适合开发测试环境
	 */
	MEMORY("memory", "Memory"),

	/**
	 * Qdrant向量数据库
	 * <p>
	 * 高性能的向量搜索引擎，支持过滤和有效负载存储
	 */
	QDRANT("qdrant", "Qdrant"),

	/**
	 * Chroma向量数据库
	 * <p>
	 * 开源的嵌入式向量数据库，易于使用和部署
	 */
	CHROMA("chroma", "Chroma"),

	/**
	 * Milvus向量数据库
	 * <p>
	 * 开源的分布式向量数据库，支持十亿级向量搜索
	 */
	MILVUS("milvus", "Milvus"),

	/**
	 * PostgreSQL向量扩展
	 * <p>
	 * 基于PostgreSQL的向量扩展pgvector，支持向量相似度搜索
	 */
	PGVECTOR("pgvector", "PgVector"),

	/** Neo4j数据库类型标识 */
	NEO4J("neo4j", "neo4j");

	/**
	 * 向量库类型标识
	 */
	@Getter
	private final String type;

	/**
	 * 向量库显示名称
	 */
	private final String desc;

}
