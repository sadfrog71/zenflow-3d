package com.deepdimen.matrix.common.neo4j.embedding;

import dev.langchain4j.Internal;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.neo4j.cypherdsl.support.schema_name.SchemaNames.sanitize;

/**
 * Neo4j嵌入向量存储工具类
 * <p>
 * 提供Neo4j嵌入向量存储相关的工具方法，包括数据转换、记录处理、批量操作等功能。 该类为内部工具类，不建议外部直接使用。
 * </p>
 */
@Internal
class Neo4jEmbeddingUtils {

	/**
	 * 私有构造函数，防止实例化工具类
	 * @throws InstantiationException 当试图实例化时抛出异常
	 */
	private Neo4jEmbeddingUtils() throws InstantiationException {
		throw new InstantiationException("Can not instantiate utility class");
	}

	/*
	 * 内部使用的不可配置字符串，用于"UNWIND $rows ..."语句中
	 */
	/** 嵌入向量行键名 */
	static final String EMBEDDINGS_ROW_KEY = "embeddingRow";

	/* 默认配置常量 */
	/** 默认ID属性名 */
	static final String DEFAULT_ID_PROP = "id";

	/** 默认数据库名称 */
	static final String DEFAULT_DATABASE_NAME = "neo4j";

	/** 默认嵌入向量属性名 */
	static final String DEFAULT_EMBEDDING_PROP = "embedding";

	/** 属性键名 */
	static final String PROPS = "props";

	/** 默认向量索引名称 */
	static final String DEFAULT_IDX_NAME = "vector";

	/** 默认全文索引名称 */
	static final String DEFAULT_FULLTEXT_IDX_NAME = "fulltext";

	/** 默认节点标签 */
	static final String DEFAULT_LABEL = "Document";

	/** 默认文本属性名 */
	static final String DEFAULT_TEXT_PROP = "origin";

	/** 默认等待索引超时时间（秒） */
	static final long DEFAULT_AWAIT_INDEX_TIMEOUT = 60L;

	/** 元数据键名 */
	static final String METADATA = "metadata";

	/** 相似度分数键名 */
	static final String SCORE = "score";

	/**
	 * 将Neo4j记录转换为嵌入向量匹配对象
	 * @param store Neo4j嵌入向量存储实例
	 * @param neo4jRecord Neo4j查询记录
	 * @return 转换后的嵌入向量匹配对象
	 */
	static EmbeddingMatch<TextSegment> toEmbeddingMatch(Neo4jEmbeddingStore store, Record neo4jRecord) {
		Map<String, String> metaData = new HashMap<>();
		neo4jRecord.get(METADATA).asMap().forEach((key, value) -> {
			if (!store.getNotMetaKeys().contains(key)) {
				String stringValue = value == null ? null : value.toString();
				metaData.put(key.replace(store.getMetadataPrefix(), ""), stringValue);
			}
		});

		Metadata metadata = new Metadata(metaData);
		TextSegment textSegment = null;
		Value origin = neo4jRecord.get(METADATA).get("origin");
		if (!origin.isNull()) {
			textSegment = TextSegment.from(origin.asString(), metadata);
		}
		else {
			Value text = neo4jRecord.get(store.getTextProperty());
			textSegment = TextSegment.from(text.isNull() ? null : text.asString(), metadata);
		}

		Embedding embedding = null;
		final Value embeddingValue = neo4jRecord.get(store.getEmbeddingProperty());
		if (!embeddingValue.isNull()) {
			List<Float> embeddingList = embeddingValue.asList(Value::asFloat);
			embedding = Embedding.from(embeddingList);
		}

		return new EmbeddingMatch<>(neo4jRecord.get(SCORE).asDouble(),
				neo4jRecord.get(store.getIdProperty()).asString(), embedding, textSegment);
	}

	/**
	 * 将嵌入向量数据转换为Neo4j记录格式
	 * @param store Neo4j嵌入向量存储实例
	 * @param idx 当前处理的索引位置
	 * @param ids ID列表
	 * @param embeddings 嵌入向量列表
	 * @param embedded 文本段列表
	 * @return 转换后的记录Map
	 */
	static Map<String, Object> toRecord(Neo4jEmbeddingStore store, int idx, List<String> ids,
			List<Embedding> embeddings, List<TextSegment> embedded) {
		String id = ids.get(idx);
		Embedding embedding = embeddings.get(idx);

		Map<String, Object> row = new HashMap<>();
		row.put(store.getIdProperty(), id);

		Map<String, Object> properties = new HashMap<>();
		if (embedded != null) {
			TextSegment segment = embedded.get(idx);
			properties.put(store.getTextProperty(), segment.text());
			Map<String, Object> metadata = segment.metadata().toMap();
			metadata.forEach((k, v) -> {
				final String propKey = store.getMetadataPrefix() + k;
				final Value propValue = Values.value(String.valueOf(v));
				properties.put(propKey, propValue);
			});
		}

		row.put(EMBEDDINGS_ROW_KEY, Values.value(embedding.vector()));
		row.put(PROPS, properties);
		return row;
	}

	/**
	 * 获取分批处理的行数据流
	 * <p>
	 * 将大量数据按批次（每批10,000条）进行分组处理，以提高性能和避免内存溢出。
	 * </p>
	 * @param store Neo4j嵌入向量存储实例
	 * @param ids ID列表
	 * @param embeddings 嵌入向量列表
	 * @param embedded 文本段列表
	 * @return 分批处理的行数据流
	 */
	static Stream<List<Map<String, Object>>> getRowsBatched(Neo4jEmbeddingStore store, List<String> ids,
			List<Embedding> embeddings, List<TextSegment> embedded) {
		int batchSize = 10_000;
		AtomicInteger batchCounter = new AtomicInteger();
		int total = ids.size();
		int batchNumber = (int) Math.ceil((double) total / batchSize);
		return IntStream.range(0, batchNumber).mapToObj(part -> {
			List<Map<String, Object>> maps = ids
				.subList(Math.min(part * batchSize, total), Math.min((part + 1) * batchSize, total))
				.stream()
				.map(i -> toRecord(store, batchCounter.getAndIncrement(), ids, embeddings, embedded))
				.toList();
			return maps;
		});
	}

	/**
	 * 对字符串值进行清理以防止Cypher注入攻击
	 * <p>
	 * 使用Neo4j CypherDSL的sanitize方法对输入值进行安全处理。 如果值无法安全引用，则抛出运行时异常。
	 * </p>
	 * @param value 要清理的字符串值
	 * @param config 配置名称，用于错误信息
	 * @return 清理后的安全字符串
	 * @throws RuntimeException 如果值无法安全清理时抛出异常
	 */
	static String sanitizeOrThrows(String value, String config) {
		return sanitize(value).orElseThrow(() -> {
			String invalidSanitizeValue = String
				.format("The value %s, to assign to configuration %s, cannot be safely quoted", value, config);
			return new RuntimeException(invalidSanitizeValue);
		});
	}

}
