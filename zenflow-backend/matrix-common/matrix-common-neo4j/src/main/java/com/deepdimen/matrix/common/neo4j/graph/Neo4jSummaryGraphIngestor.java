package com.deepdimen.matrix.common.neo4j.graph;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Neo4j摘要图数据导入器
 *
 * @author caoyuan
 * @date 2025/08/06
 */
public class Neo4jSummaryGraphIngestor extends Neo4jGraphEmbeddingStoreIngestor {

	public Neo4jSummaryGraphIngestor(final Builder builder) {
		super(builder);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends Neo4jGraphEmbeddingStoreIngestor.Builder {

		private static final String DEFAULT_RETRIEVAL = """
				MATCH (node)<-[:HAS_SUMMARY]-(parent)
				WITH parent, max(score) AS score, node // deduplicate parents
				RETURN parent.text AS text, score, properties(node) AS metadata
				ORDER BY score DESC
				LIMIT $maxResults""";

		private static final String DEFAULT_SYSTEM_PROMPT = """
				您正在根据文本中的信息生成简洁准确的摘要，摘要结果和输入语言保持一致。
				""";

		private static final String DEFAULT_USER_PROMPT = """
				生成以下输入的摘要：
				{{input}}

				摘要:
				""";

		private static final String DEFAULT_PARENT_QUERY = """
				UNWIND $rows AS row
				MATCH (p:SummaryChunk {parentId: $parentId})
				CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
				SET u += row.%3$s
				WITH row, u
				CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
				RETURN count(*)""";

		private static final String DEFAULT_CHUNK_CREATION_QUERY = "CREATE (:SummaryChunk $metadata)";

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		public Neo4jSummaryGraphIngestor build() {
			systemPrompt = getOrDefault(systemPrompt, DEFAULT_SYSTEM_PROMPT);
			userPrompt = getOrDefault(userPrompt, DEFAULT_USER_PROMPT);
			query = getOrDefault(query, DEFAULT_CHUNK_CREATION_QUERY);
			embeddingStore = getOrDefault(embeddingStore, null);
			return new Neo4jSummaryGraphIngestor(this);
		}

	}

}
