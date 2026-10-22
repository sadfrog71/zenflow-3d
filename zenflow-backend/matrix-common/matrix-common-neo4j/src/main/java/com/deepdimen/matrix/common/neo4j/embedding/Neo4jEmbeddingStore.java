package com.deepdimen.matrix.common.neo4j.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.neo4j.cypherdsl.core.*;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.deepdimen.matrix.common.neo4j.embedding.Neo4jEmbeddingUtils.*;
import static com.deepdimen.matrix.common.neo4j.embedding.Neo4jFilterMapper.toCypherLiteral;
import static com.deepdimen.matrix.common.neo4j.embedding.Neo4jUtils.functionDef;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Collections.singletonList;
import static org.neo4j.cypherdsl.core.Cypher.*;

/**
 * Neo4j向量索引嵌入存储实现
 * <p>
 * 将<a href="https://neo4j.com/">Neo4j</a>向量索引作为嵌入存储的实现。 支持向量相似度搜索、全文搜索和混合搜索功能。
 * </p>
 * <p>
 * 使用构建器模式创建实例： <pre>{@code
 * EmbeddingStore<TextSegment> example() {
 *   return Neo4jEmbeddingStore.builder()
 *             .withBasicAuth("bolt://host:port", "username", "password")
 *             .dimension(384)
 *             .build();
 * }
 * }</pre>
 * </p>
 *
 */
public class Neo4jEmbeddingStore implements EmbeddingStore<TextSegment> {

	private static final Logger log = LoggerFactory.getLogger(Neo4jEmbeddingStore.class);

	/** 实体创建Cypher查询模板 */
	public static final String ENTITIES_CREATION = """
			UNWIND $rows AS row
			MERGE (u:%1$s {%2$s: row.%2$s})
			SET u += row.%3$s
			WITH row, u
			CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
			RETURN count(*)""";

	/** 索引已存在错误信息模板 */
	public static final String INDEX_ALREADY_EXISTS_ERROR = """
			It's not possible to create an index for the label `%s` and the property `%s`,
			as there is another index with name `%s` with different labels: `%s` and properties `%s`.
			Please provide another indexName to create the vector index, or delete the existing one""";

	/** 创建向量索引Cypher查询模板 */
	public static final String CREATE_VECTOR_INDEX = """
			CREATE VECTOR INDEX %s IF NOT EXISTS
			FOR (m:%s) ON m.%s
			OPTIONS { indexConfig: {
			    `vector.dimensions`: %s,
			    `vector.similarity_function`: 'cosine'
			}}
			""";

	/** 不允许的列名错误信息 */
	public static final String COLUMNS_NOT_ALLOWED_ERR = "There are columns not allowed in the search query: ";

	/* Neo4j Java Driver配置 */
	/** Neo4j驱动程序实例 */
	private final Driver driver;

	/** 会话配置 */
	private final SessionConfig config;

	/* Neo4j模式字段配置 */
	/** 向量维度 */
	private final int dimension;

	/** 等待索引创建完成的超时时间（秒） */
	private final long awaitIndexTimeout;

	/** 向量索引名称 */
	private final String indexName;

	/** 元数据属性前缀 */
	private final String metadataPrefix;

	/** 嵌入向量属性名称 */
	private final String embeddingProperty;

	/** 清理后的嵌入向量属性名称 */
	private final String sanitizedEmbeddingProperty;

	/** ID属性名称 */
	private final String idProperty;

	/** 清理后的ID属性名称 */
	private final String sanitizedIdProperty;

	/** 节点标签名称 */
	private final String label;

	/** 清理后的节点标签名称 */
	private final String sanitizedLabel;

	/** 文本属性名称 */
	private final String textProperty;

	/** 检索查询语句 */
	private final String retrievalQuery;

	/** 实体创建查询语句 */
	private String entityCreationQuery;

	/** 非元数据键集合 */
	private final Set<String> notMetaKeys;

	/** 附加参数映射 */
	private Map<String, Object> additionalParams;

	/** 全文索引名称 */
	private final String fullTextIndexName;

	/** 全文查询语句 */
	private final String fullTextQuery;

	/** 全文检索查询语句 */
	private final String fullTextRetrievalQuery;

	/** 是否自动创建全文索引 */
	private final boolean autoCreateFullText;

	/**
	 * 创建Neo4j嵌入存储实例
	 * @param config 会话配置（可选，默认为SessionConfig.forDatabase(databaseName)）
	 * @param driver Neo4j驱动程序（必需）
	 * @param dimension 向量维度（必需，范围0-4096）
	 * @param label 节点标签名称（可选，默认："Document"）
	 * @param embeddingProperty 嵌入向量属性名称（可选，默认："embedding"）
	 * @param idProperty ID属性名称（可选，默认："id"）
	 * @param metadataPrefix 元数据前缀（可选，默认：""）
	 * @param textProperty 文本属性名称（可选，默认："text"）
	 * @param indexName 索引名称（可选，默认："vector"）
	 * @param databaseName 数据库名称（可选，默认："neo4j"）
	 * @param retrievalQuery 检索查询语句（可选）
	 * @param awaitIndexTimeout 等待索引创建完成的超时时间，单位秒（可选，默认：60秒）
	 * @param fullTextIndexName 全文索引名称（可选，默认："fulltext"）
	 * @param fullTextQuery 全文索引查询语句（可选，混合搜索时需要）
	 * @param fullTextRetrievalQuery 全文检索查询语句（可选，默认使用retrievalQuery）
	 * @param autoCreateFullText 是否自动创建全文索引（可选，默认：false）
	 * @param entityCreationQuery 实体创建查询语句（可选，默认使用ENTITIES_CREATION）
	 * @param additionalParams 附加的实体创建参数（可选，默认为空Map）
	 */
	public Neo4jEmbeddingStore(SessionConfig config, Driver driver, int dimension, String label,
			String embeddingProperty, String idProperty, String metadataPrefix, String textProperty, String indexName,
			String databaseName, String retrievalQuery, long awaitIndexTimeout, String fullTextIndexName,
			String fullTextQuery, String fullTextRetrievalQuery, boolean autoCreateFullText, String entityCreationQuery,
			Map<String, Object> additionalParams) {

		/* required configs */
		this.driver = ensureNotNull(driver, "driver");
		this.dimension = ensureBetween(dimension, 0, 4096, "dimension");

		/* optional configs */
		String dbName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
		this.config = getOrDefault(config, SessionConfig.forDatabase(dbName));
		this.label = getOrDefault(label, DEFAULT_LABEL);
		this.embeddingProperty = getOrDefault(embeddingProperty, DEFAULT_EMBEDDING_PROP);
		this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
		this.indexName = getOrDefault(indexName, DEFAULT_IDX_NAME);
		this.metadataPrefix = getOrDefault(metadataPrefix, "");
		this.textProperty = getOrDefault(textProperty, DEFAULT_TEXT_PROP);
		this.awaitIndexTimeout = getOrDefault(awaitIndexTimeout, DEFAULT_AWAIT_INDEX_TIMEOUT);
		this.additionalParams = copy(additionalParams);

		/* sanitize labels and property names, to prevent from Cypher Injections */
		this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
		this.sanitizedEmbeddingProperty = sanitizeOrThrows(this.embeddingProperty, "embeddingProperty");
		this.sanitizedIdProperty = sanitizeOrThrows(this.idProperty, "idProperty");
		String sanitizedText = sanitizeOrThrows(this.textProperty, "textProperty");

		/*
		 * retrieval query: must necessarily return the following column: `metadata`,
		 * `score`, `this.idProperty (default "id")`, `this.textProperty (default
		 * "textProperty")`, `this.embeddingProperty (default "embedding")`
		 */
		String defaultRetrievalQuery = String.format(
				"RETURN properties(node) AS metadata, node.%1$s AS %1$s, node.%2$s AS %2$s, node.%3$s AS %3$s, score",
				this.sanitizedIdProperty, sanitizedText, sanitizedEmbeddingProperty);
		this.retrievalQuery = getOrDefault(retrievalQuery, defaultRetrievalQuery);

		this.notMetaKeys = new HashSet<>(Arrays.asList(this.idProperty, this.embeddingProperty, this.textProperty));

		/* optional full text index */
		this.autoCreateFullText = autoCreateFullText;
		this.fullTextIndexName = getOrDefault(fullTextIndexName, DEFAULT_FULLTEXT_IDX_NAME);
		this.fullTextQuery = fullTextQuery;
		this.fullTextRetrievalQuery = getOrDefault(fullTextRetrievalQuery, this.retrievalQuery);

		this.entityCreationQuery = getOrDefault(entityCreationQuery, ENTITIES_CREATION);

		/* auto-schema creation */
		createSchema();
	}

	/**
	 * 创建Builder实例
	 * @return 新的Builder实例，用于构建Neo4jEmbeddingStore
	 */
	public static Builder builder() {
		return new Builder();
	}

	/*
	 * Getter方法
	 */

	/**
	 * 获取非元数据键集合
	 * @return 包含ID、嵌入向量和文本属性的键集合
	 */
	public Set<String> getNotMetaKeys() {
		return notMetaKeys;
	}

	/**
	 * 获取元数据前缀
	 * @return 元数据属性前缀字符串
	 */
	public String getMetadataPrefix() {
		return metadataPrefix;
	}

	/**
	 * 获取文本属性名称
	 * @return 文本属性名称
	 */
	public String getTextProperty() {
		return textProperty;
	}

	/**
	 * 获取ID属性名称
	 * @return ID属性名称
	 */
	public String getIdProperty() {
		return idProperty;
	}

	/**
	 * 获取嵌入向量属性名称
	 * @return 嵌入向量属性名称
	 */
	public String getEmbeddingProperty() {
		return embeddingProperty;
	}

	/**
	 * 获取向量索引名称
	 * @return 向量索引名称
	 */
	public String getIndexName() {
		return indexName;
	}

	/**
	 * 获取检索查询语句
	 * @return 检索查询语句
	 */
	public String getRetrievalQuery() {
		return retrievalQuery;
	}

	/**
	 * 获取清理后的节点标签名称
	 * @return 清理后的节点标签名称
	 */
	public String getSanitizedLabel() {
		return sanitizedLabel;
	}

	/**
	 * 获取向量维度
	 * @return 向量维度
	 */
	public int getDimension() {
		return dimension;
	}

	/**
	 * 获取清理后的嵌入向量属性名称
	 * @return 清理后的嵌入向量属性名称
	 */
	public String getSanitizedEmbeddingProperty() {
		return sanitizedEmbeddingProperty;
	}

	/**
	 * 设置附加参数
	 * @param additionalParams 附加参数映射
	 */
	public void setAdditionalParams(final Map<String, Object> additionalParams) {
		this.additionalParams = additionalParams;
	}

	/**
	 * 设置实体创建查询语句
	 * @param entityCreationQuery 实体创建查询语句
	 */
	public void setEntityCreationQuery(final String entityCreationQuery) {
		this.entityCreationQuery = entityCreationQuery;
	}

	/*
	 * 实现EmbeddingStore接口的方法
	 */

	/**
	 * 添加嵌入向量并返回生成的ID
	 * @param embedding 要添加的嵌入向量
	 * @return 自动生成的随机UUID
	 */
	@Override
	public String add(Embedding embedding) {
		String id = randomUUID();
		add(id, embedding);
		return id;
	}

	/**
	 * 使用指定ID添加嵌入向量
	 * @param id 指定的ID
	 * @param embedding 要添加的嵌入向量
	 */
	@Override
	public void add(String id, Embedding embedding) {
		addInternal(id, embedding, null);
	}

	/**
	 * 添加嵌入向量和文本段，返回生成的ID
	 * @param embedding 要添加的嵌入向量
	 * @param textSegment 要添加的文本段
	 * @return 自动生成的随机UUID
	 */
	@Override
	public String add(Embedding embedding, TextSegment textSegment) {
		String id = randomUUID();
		addInternal(id, embedding, textSegment);
		return id;
	}

	/**
	 * 批量添加嵌入向量
	 * @param embeddings 要添加的嵌入向量列表
	 * @return 生成的ID列表
	 */
	@Override
	public List<String> addAll(List<Embedding> embeddings) {
		return addAll(embeddings, null);
	}

	/**
	 * 删除所有记录 使用事务批量删除指定标签的所有节点
	 */
	@Override
	public void removeAll() {
		try (Session session = session()) {
			// Build the MATCH and DETACH DELETE inside the subquery
			Node node = node(label).named("n");

			Statement innerQuery = match(node).detachDelete(node).build();

			// Wrap it in a subquery with "CALL { ... } IN TRANSACTIONS"
			Statement finalQuery = Statement.builder().callInTransactions(innerQuery).build();

			// Render Cypher
			String cypher = Renderer.getDefaultRenderer().render(finalQuery);

			// Execute using the Neo4j driver session
			session.run(cypher);
		}
	}

	@Override
	public void removeAll(Collection<String> ids) {
		ensureNotEmpty(ids, "ids");

		try (Session session = session()) {
			// build an `UNWIND $ids AS id MATCH (n:<label> {<idProperty>: idVar}) DETACH
			// DELETE n`
			SymbolicName idVar = name("id");

			// MATCH (n:Label {idProp: id})
			Node node = node(label).named("n");
			Statement match = unwind(parameter("ids")).as(idVar)
				.match(node)
				.where(node.property(idProperty).isEqualTo(idVar))
				.detachDelete(node)
				.build();

			Statement full = Statement.builder().callInTransactions(match).build();

			// Render Cypher
			String cypher = Renderer.getDefaultRenderer().render(full);
			Map<String, Object> params = Map.of("ids", ids);
			session.run(cypher, params);
		}
	}

	@Override
	public void removeAll(Filter filter) {
		ensureNotNull(filter, "filter");

		try (Session session = session()) {

			// Build a "CALL { MATCH (n:<label>) WHERE n.%2$s IS NOT NULL AND size(n.%2$s)
			// = toInteger(%3$s) AND %4$s
			// DETACH DELETE n } IN TRANSACTIONS ",
			Node node = node(label).named("node");
			Neo4jFilterMapper neo4jFilterMapper = new Neo4jFilterMapper(node);

			Statement match = match(node).where(neo4jFilterMapper.getCondition(filter)).detachDelete(node).build();

			// Wrap all in subquery and use IN TRANSACTIONS
			Statement full = Statement.builder().callInTransactions(match).build();

			// Render Cypher
			String cypher = Renderer.getDefaultRenderer().render(full);
			session.run(cypher);
		}
	}

	@Override
	public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {

		Value embeddingValue = Values.value(request.queryEmbedding().vector());

		try (Session session = session()) {
			Filter filter = request.filter();
			if (filter == null) {
				return getSearchResUsingVectorIndex(request, embeddingValue, session);
			}
			return getSearchResUsingVectorSimilarity(request, filter, embeddingValue, session);
		}
	}

	/*
	 * Private methods
	 */
	private EmbeddingSearchResult<TextSegment> getSearchResUsingVectorSimilarity(EmbeddingSearchRequest request,
			Filter filter, Value embeddingValue, Session session) {
		/*
		 * Build an CYPHER runtime = parallel parallelRuntimeSupport=all MATCH (n:%1$s)
		 * WHERE n.%2$s IS NOT NULL AND size(n.%2$s) = toInteger(%3$s) AND %4$s WITH n,
		 * vector.similarity.cosine(n.%2$s, %5$s) AS score WHERE score >= $minScore WITH n
		 * AS node, score ORDER BY score DESC LIMIT $maxResults
		 */

		// Match Clause
		Node node = node(this.label).named("node");

		Neo4jFilterMapper neo4jFilterMapper = new Neo4jFilterMapper(node);

		// WHERE conditions
		Condition condition = node.property(this.embeddingProperty)
			.isNotNull()
			.and(size(node.property(this.embeddingProperty)).eq(toCypherLiteral(this.dimension)))
			.and(neo4jFilterMapper.getCondition(filter));

		// Cosine similarity
		Expression similarity = FunctionInvocation.create(functionDef("vector.similarity.cosine"),
				node.property(this.embeddingProperty), toCypherLiteral(embeddingValue));

		// Filtering by score
		Condition scoreCondition = similarity.gte(parameter("minScore"));

		// Final query construction
		Statement statement = match(node).where(condition)
			.with(node.as("node"), similarity.as("score"))
			.where(scoreCondition)
			.orderBy(name("score"))
			.descending()
			.limit(parameter("maxResults"))
			.returning(raw(retrievalQuery))
			.build();

		// Render the Cypher query
		String cypherQuery = getRender(statement);

		Map<String, Object> params = new HashMap<>(); // entry.getValue();
		params.put("minScore", request.minScore());
		params.put("maxResults", request.maxResults());
		return getEmbeddingSearchResult(session, cypherQuery, params);
	}

	private EmbeddingSearchResult<TextSegment> getSearchResUsingVectorIndex(EmbeddingSearchRequest request,
			Value embeddingValue, Session session) {

		Map<String, Object> params = new HashMap<>(Map.of("indexName", indexName, "embeddingValue", embeddingValue,
				"minScore", request.minScore(), "maxResults", request.maxResults()));

		Parameter<Object> indexNameParam = parameter("indexName");
		Parameter<Object> maxResultsParam = parameter("maxResults");
		Parameter<Object> embeddingValueParam = parameter("embeddingValue");
		Parameter<Object> minScoreParam = parameter("minScore");

		// Build a "CALL db.index.vector.queryNodes($indexName, $maxResults,
		// $embeddingValue) YIELD node, score WHERE
		// score >= $minScore <retrievalQuery>"
		Statement vectorQuery = call("db.index.vector.queryNodes")
			.withArgs(indexNameParam, maxResultsParam, embeddingValueParam)
			.yield("node", "score")
			.where(name("score").gte(minScoreParam))
			.returning(raw(retrievalQuery))
			.build();

		Statement statement;

		// Full-text search condition
		if (fullTextQuery != null) {
			/*
			 * Build a UNION CALL db.index.fulltext.queryNodes($fullTextIndexName,
			 * $fullTextQuery, {limit: $maxResults}) YIELD node, score WHERE score >=
			 * $minScore
			 */
			Parameter<Object> fullTextIndexNameParam = parameter("fullTextIndexName");
			Parameter<Object> fullTextQueryParam = parameter("fullTextQuery");

			Statement fullTextSearch = call("db.index.fulltext.queryNodes")
				.withArgs(fullTextIndexNameParam, fullTextQueryParam, mapOf("limit", maxResultsParam))
				.yield("node", "score")
				.where(name("score").gte(minScoreParam))
				.returning(raw(fullTextRetrievalQuery))
				.build();

			// UNION with full-text search
			statement = Cypher.union(vectorQuery, fullTextSearch);

			params.putAll(Map.of("fullTextIndexName", fullTextIndexName, "fullTextQuery", fullTextQuery));
		}
		else {
			statement = vectorQuery;
		}

		// Render the Cypher query
		String cypherQuery = getRender(statement);

		Set<String> columns = getColumnNames(session, cypherQuery);
		Set<Object> allowedColumn = Set.of(textProperty, embeddingProperty, idProperty, SCORE, METADATA);

		if (!allowedColumn.containsAll(columns) || columns.size() > allowedColumn.size()) {
			throw new RuntimeException(COLUMNS_NOT_ALLOWED_ERR + columns);
		}

		return getEmbeddingSearchResult(session, cypherQuery, params);
	}

	/**
	 * The user can put a retrievalQuery / fullTextRetrievalQuery with a MATCH, CALL,
	 * WHERE and RETURN for example "MATCH (node)-[:REL]->(otherNode) RETURN
	 * properties(otherNode) AS metadata, score" Given that the `returning()` Cypher-DSL
	 * method prepend a "RETURN " to the statement, we sanitize the query by replacing
	 * "RETURN MATCH ... RETURN ..." with "MATCH ... RETURN ..." and so on. We just check
	 * MATCH, CALL, WHERE and RETURN since the other clauses (e.g. "SET") should not be
	 * passed as they perform write operations, not read operations
	 */
	private static String getRender(Statement statement) {
		final String input = Renderer.getDefaultRenderer().render(statement);
		return input.replaceAll("(?i)RETURN\\s+MATCH", "MATCH")
			.replaceAll("(?i)RETURN\\s+WHERE", "WHERE")
			.replaceAll("(?i)RETURN\\s+CALL", "CALL")
			.replaceAll("(?i)RETURN\\s+RETURN", "RETURN");
	}

	private EmbeddingSearchResult<TextSegment> getEmbeddingSearchResult(Session session, String query,
			Map<String, Object> params) {
		List<EmbeddingMatch<TextSegment>> matches = session
			.executeRead(tx -> tx.run(query, params).list(item -> toEmbeddingMatch(this, item)));

		return new EmbeddingSearchResult<>(matches);
	}

	private Set<String> getColumnNames(Session session, String query) {
		// retrieve column names
		List<String> keys = session.run("EXPLAIN " + query).keys();
		// when there are multiple variables with the same name, e.g. within a "UNION ALL"
		// Neo4j adds a suffix
		// "@<number>" to distinguish them,
		// so to check the correctness of the output parameters we must first remove this
		// suffix from the column names
		return keys.stream().map(i -> i.replaceFirst("@[0-9]+", "").trim()).collect(Collectors.toSet());
	}

	private void addInternal(String id, Embedding embedding, TextSegment embedded) {
		addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
	}

	@Override
	public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
		if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
			log.info("[do not add empty embeddings to neo4j]");
			return;
		}
		ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
		ensureTrue(embedded == null || embeddings.size() == embedded.size(),
				"embeddings size is not equal to embedded size");

		bulk(ids, embeddings, embedded);
	}

	private void bulk(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
		Stream<List<Map<String, Object>>> rowsBatched = getRowsBatched(this, ids, embeddings, embedded);

		try (Session session = session()) {
			rowsBatched.forEach(rows -> {
				String statement = String.format(this.entityCreationQuery, this.sanitizedLabel,
						this.sanitizedIdProperty, PROPS, EMBEDDINGS_ROW_KEY);

				Map<String, Object> params = new HashMap<>();
				params.put("rows", rows);
				params.put("embeddingProperty", this.embeddingProperty);
				params.putAll(additionalParams);

				session.executeWrite(tx -> tx.run(statement, params).consume());
			});
		}
	}

	private void createSchema() {
		if (!indexExists()) {
			createIndex();
		}
		createFullTextIndex();
		if (!constraintExist()) {
			createUniqueConstraint();
		}
	}

	private boolean constraintExist() {
		try (Session session = session()) {
			String query = """
					SHOW CONSTRAINTS
					WHERE $label IN labelsOrTypes
					AND $property IN properties
					AND type IN ['NODE_KEY', 'UNIQUENESS']
					""";
			Result result = session.run(query, Map.of("label", this.label, "property", this.idProperty));

			return !result.list().isEmpty();
		}
	}

	private void createUniqueConstraint() {
		try (Session session = session()) {
			String query = String.format("CREATE CONSTRAINT IF NOT EXISTS FOR (n:%s) REQUIRE n.%s IS UNIQUE",
					this.sanitizedLabel, this.sanitizedIdProperty);
			session.run(query);
		}
	}

	private boolean indexExists() {
		try (Session session = session()) {
			Map<String, Object> params = Map.of("name", this.indexName);
			Result resIndex = session.run("SHOW VECTOR INDEX WHERE name = $name", params);
			if (!resIndex.hasNext()) {
				return false;
			}
			Record singleRecord = resIndex.single();
			List<String> idxLabels = singleRecord.get("labelsOrTypes").asList(Value::asString);
			List<Object> idxProps = singleRecord.get("properties").asList();

			boolean isIndexDifferent = !idxLabels.equals(singletonList(this.label))
					|| !idxProps.equals(singletonList(this.embeddingProperty));
			if (isIndexDifferent) {
				String errMessage = String.format(INDEX_ALREADY_EXISTS_ERROR, this.label, this.embeddingProperty,
						this.indexName, idxLabels, idxProps);
				throw new RuntimeException(errMessage);
			}
			return true;
		}
	}

	private void createFullTextIndex() {
		if (!autoCreateFullText) {
			return;
		}

		try (Session session = session()) {
			String query = String.format("CREATE FULLTEXT INDEX %s IF NOT EXISTS FOR (n:%s) ON EACH [n.%s]",
					this.fullTextIndexName, this.sanitizedLabel, this.sanitizedIdProperty);
			session.run(query).consume();
		}
	}

	private void createIndex() {
		Map<String, Object> params = Map.of("indexName", this.indexName, "label", this.label, "embeddingProperty",
				this.embeddingProperty, "dimension", this.dimension);

		// create vector index
		try (Session session = session()) {
			String createIndexQuery = String.format(CREATE_VECTOR_INDEX, indexName, sanitizedLabel,
					sanitizedEmbeddingProperty, dimension);
			session.run(createIndexQuery, params);

			session.run("CALL db.awaitIndexes($timeout)", Map.of("timeout", awaitIndexTimeout)).consume();
		}
	}

	private Session session() {
		return this.driver.session(this.config);
	}

	public static class Builder {

		private String indexName;

		private String metadataPrefix;

		private String embeddingProperty;

		private String idProperty;

		private String label;

		private String textProperty;

		private String databaseName;

		private String retrievalQuery;

		private SessionConfig config;

		private Driver driver;

		private int dimension;

		private long awaitIndexTimeout;

		private String fullTextIndexName;

		private String fullTextQuery;

		private String fullTextRetrievalQuery;

		private boolean autoCreateFullText;

		private String entityCreationQuery;

		private Map<String, Object> additionalParams;

		/**
		 * @param indexName the optional index name (default: "vector")
		 */
		public Builder indexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		/**
		 * @param metadataPrefix the optional metadata prefix (default: "")
		 */
		public Builder metadataPrefix(String metadataPrefix) {
			this.metadataPrefix = metadataPrefix;
			return this;
		}

		/**
		 * @param embeddingProperty the optional embeddingProperty name (default:
		 * "embedding")
		 */
		public Builder embeddingProperty(String embeddingProperty) {
			this.embeddingProperty = embeddingProperty;
			return this;
		}

		/**
		 * @param idProperty the optional id property name (default: "id")
		 */
		public Builder idProperty(String idProperty) {
			this.idProperty = idProperty;
			return this;
		}

		/**
		 * @param label the optional label name (default: "Document")
		 */
		public Builder label(String label) {
			this.label = label;
			return this;
		}

		/**
		 * @param textProperty the optional textProperty property name (default: "text")
		 */
		public Builder textProperty(String textProperty) {
			this.textProperty = textProperty;
			return this;
		}

		/**
		 * @param databaseName the optional database name (default: "neo4j")
		 */
		public Builder databaseName(String databaseName) {
			this.databaseName = databaseName;
			return this;
		}

		/**
		 * @param retrievalQuery the optional retrieval query (default: "RETURN
		 * properties(node) AS metadata, node.`idProperty` AS `idProperty`,
		 * node.`textProperty` AS `textProperty`, node.`embeddingProperty` AS
		 * `embeddingProperty`, score")
		 */
		public Builder retrievalQuery(String retrievalQuery) {
			this.retrievalQuery = retrievalQuery;
			return this;
		}

		/**
		 * @param entityCreationQuery the optional entity creation query (default:
		 * {@link Neo4jEmbeddingStore#ENTITIES_CREATION})
		 */
		public Builder entityCreationQuery(String entityCreationQuery) {
			this.entityCreationQuery = entityCreationQuery;
			return this;
		}

		/**
		 * @param config the {@link SessionConfig} (optional, default is
		 * `SessionConfig.forDatabase(`databaseName`)`)
		 */
		public Builder config(SessionConfig config) {
			this.config = config;
			return this;
		}

		/**
		 * @param driver the {@link Driver} (required)
		 */
		public Builder driver(Driver driver) {
			this.driver = driver;
			return this;
		}

		/**
		 * @param dimension the dimension (required)
		 */
		public Builder dimension(int dimension) {
			this.dimension = dimension;
			return this;
		}

		/**
		 * @param awaitIndexTimeout the optional awaiting timeout for all indexes to come
		 * online, in seconds (default: 60s)
		 */
		public Builder awaitIndexTimeout(long awaitIndexTimeout) {
			this.awaitIndexTimeout = awaitIndexTimeout;
			return this;
		}

		/**
		 * @param fullTextIndexName the optional full-text index name, to perform a hybrid
		 * search (default: `fulltext`)
		 */
		public Builder fullTextIndexName(String fullTextIndexName) {
			this.fullTextIndexName = fullTextIndexName;
			return this;
		}

		/**
		 * @param fullTextQuery the optional full-text index query, required if we want to
		 * perform a hybrid search
		 */
		public Builder fullTextQuery(String fullTextQuery) {
			this.fullTextQuery = fullTextQuery;
			return this;
		}

		/**
		 * @param fullTextRetrievalQuery the optional full-text retrieval query (default:
		 * {@param retrievalQuery})
		 */
		public Builder fullTextRetrievalQuery(String fullTextRetrievalQuery) {
			this.fullTextRetrievalQuery = fullTextRetrievalQuery;
			return this;
		}

		/**
		 * @param autoCreateFullText if true, it will auto create the full-text index if
		 * not exists (default: false)
		 */
		public Builder autoCreateFullText(boolean autoCreateFullText) {
			this.autoCreateFullText = autoCreateFullText;
			return this;
		}

		/**
		 * @param additionalParams the additional entity creation parameters (default:
		 * empty maps)
		 */
		public Builder additionalParams(Map<String, Object> additionalParams) {
			this.additionalParams = additionalParams;
			return this;
		}

		/**
		 * Creates an instance a {@link Driver}, starting from uri, user and password
		 * @param uri the Bolt URI to a Neo4j instance
		 * @param user the Neo4j instance's username
		 * @param password the Neo4j instance's password
		 */
		public Builder withBasicAuth(String uri, String user, String password) {
			this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
			return this;
		}

		public Neo4jEmbeddingStore build() {
			return new Neo4jEmbeddingStore(config, driver, dimension, label, embeddingProperty, idProperty,
					metadataPrefix, textProperty, indexName, databaseName, retrievalQuery, awaitIndexTimeout,
					fullTextIndexName, fullTextQuery, fullTextRetrievalQuery, autoCreateFullText, entityCreationQuery,
					additionalParams);
		}

	}

}
