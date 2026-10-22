package com.deepdimen.matrix.common.neo4j.graph;

import com.deepdimen.matrix.common.neo4j.embedding.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Neo4j图嵌入存储处理器，用于处理文档并通过转换和嵌入管道将结果存储到Neo4j数据库中
 *
 * @author caoyuan
 * @date 2025/08/06
 */
public class Neo4jGraphEmbeddingStoreIngestor extends Neo4jParentChildEmbeddingStoreIngestor {

	public static final String DEFAULT_PARENT_ID_KEY = "parentId";

	protected final Driver driver;

	protected final String query;

	protected final String parentIdKey;

	protected final Map<String, Object> params;

	protected Neo4jEmbeddingStore neo4jEmbeddingStore;

	protected final String userPrompt;

	protected final String systemPrompt;

	protected final ChatModel questionModel;

	public Neo4jGraphEmbeddingStoreIngestor(Builder builder) {
		this(builder.documentTransformer, builder.documentSplitter, builder.textSegmentTransformer,
				builder.childTextSegmentTransformer, builder.embeddingModel,
				(Neo4jEmbeddingStore) builder.embeddingStore, builder.documentChildSplitter, builder.driver,
				builder.query, builder.parentIdKey, builder.params, builder.systemPrompt, builder.userPrompt,
				builder.questionModel);
	}

	/**
	 * Constructs a new {@code Neo4jGraphEmbeddingStoreIngestor}, which processes
	 * documents through a transformation and embedding pipeline and stores the results in
	 * a Neo4j database. This includes:
	 * <ul>
	 * <li>Document transformation</li>
	 * <li>Parent and child document splitting</li>
	 * <li>Optional manipulation of parent text segments using a {@link ChatModel}</li>
	 * <li>Embedding generation and storage of segments into Neo4j</li>
	 * </ul>
	 * @param documentTransformer The {@link DocumentTransformer} applied to the original
	 * documents.
	 * @param documentSplitter The {@link DocumentSplitter} used to split documents into
	 * parent segments.
	 * @param textSegmentTransformer The {@link TextSegmentTransformer} applied to parent
	 * segments.
	 * @param childTextSegmentTransformer The {@link TextSegmentTransformer} applied to
	 * child segments.
	 * @param embeddingModel The {@link EmbeddingModel} used to generate embeddings from
	 * text segments.
	 * @param embeddingStore The {@link EmbeddingStore} (specifically
	 * {@link Neo4jEmbeddingStore}) used to persist embeddings.
	 * @param documentChildSplitter The {@link DocumentSplitter} used to generate child
	 * segments from parent segments.
	 * @param driver The {@link Driver} used to execute Cypher queries against the Neo4j
	 * database.
	 * @param query The Cypher query used to insert processed segments and metadata into
	 * Neo4j.
	 * @param parentIdKey The metadata key used to extract the parent segment ID; if
	 * absent, a UUID will be generated.
	 * @param params Additional query parameters to include in the Cypher execution,
	 * beyond segment metadata and text.
	 * @param systemPrompt A system prompt for manipulating parent segment text via a
	 * {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
	 * @param userPrompt A user prompt for manipulating parent segment text via a
	 * {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
	 * @param questionModel A {@link ChatModel} used to further transform parent segment
	 * text based on provided prompts. If {@code null}, no chat-based manipulation occurs.
	 */
	public Neo4jGraphEmbeddingStoreIngestor(DocumentTransformer documentTransformer, DocumentSplitter documentSplitter,
			TextSegmentTransformer textSegmentTransformer, TextSegmentTransformer childTextSegmentTransformer,
			EmbeddingModel embeddingModel, Neo4jEmbeddingStore embeddingStore, DocumentSplitter documentChildSplitter,
			Driver driver, String query, String parentIdKey, Map<String, Object> params, String systemPrompt,
			String userPrompt, ChatModel questionModel) {
		super(documentTransformer, documentSplitter, textSegmentTransformer, childTextSegmentTransformer,
				embeddingModel, embeddingStore, documentChildSplitter);
		this.driver = ensureNotNull(driver, "driver");
		this.query = ensureNotNull(query, "query");
		this.params = copy(params);
		this.parentIdKey = getOrDefault(parentIdKey, DEFAULT_PARENT_ID_KEY);

		this.neo4jEmbeddingStore = embeddingStore;
		super.textSegmentTransformer = getOrDefault(textSegmentTransformer, getTextSegmentTransformer());
		super.childTextSegmentTransformer = getOrDefault(childTextSegmentTransformer,
				getDefaultChildTextSegmentTransformer());
		this.userPrompt = userPrompt;
		this.systemPrompt = systemPrompt;
		this.questionModel = questionModel;
	}

	public Neo4jGraphEmbeddingStoreIngestor(DocumentTransformer documentTransformer, DocumentSplitter documentSplitter,
			TextSegmentTransformer textSegmentTransformer, TextSegmentTransformer childTextSegmentTransformer,
			EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
			DocumentSplitter documentChildSplitter, Driver driver, String query, String parentIdKey,
			Map<String, Object> params, String userPrompt, String systemPrompt, ChatModel questionModel) {
		super(documentTransformer, documentSplitter, textSegmentTransformer, childTextSegmentTransformer,
				embeddingModel, embeddingStore, documentChildSplitter);
		this.driver = driver;
		this.query = query;
		this.parentIdKey = parentIdKey;
		this.params = params;
		this.userPrompt = userPrompt;
		this.systemPrompt = systemPrompt;
		this.questionModel = questionModel;
	}

	private TextSegmentTransformer getTextSegmentTransformer() {
		return segment -> {
			TextSegment parentSegment = getTextSegmentWithUniqueId(segment, neo4jEmbeddingStore.getIdProperty(), null);
			String textInput = parentSegment.text();
			Map<String, Object> metadataMap = segment.metadata().toMap();
			String parentId = "parent_" + UUID.randomUUID();
			metadataMap.put(parentIdKey, parentId);
			String text;
			if (this.questionModel != null) {
				if (systemPrompt == null || userPrompt == null) {
					throw new RuntimeException(
							"Prompts cannot be null: systemPrompt=" + systemPrompt + ", userPrompt=" + userPrompt);
				}
				SystemMessage systemMessage = Prompt.from(systemPrompt).toSystemMessage();
				PromptTemplate userTemplate = PromptTemplate.from(userPrompt);
				UserMessage userMessage = userTemplate.apply(Map.of("input", textInput)).toUserMessage();
				List<ChatMessage> chatMessages = List.of(systemMessage, userMessage);

				text = this.questionModel.chat(chatMessages).aiMessage().text();
			}
			else {
				text = textInput;
			}
			metadataMap.putIfAbsent("text", text);
			metadataMap.putIfAbsent("origin", textInput);

			Map<String, Object> params = new HashMap<>(Map.of("metadata", metadataMap));
			params.put(parentIdKey, parentId);
			getAdditionalParams(parentId);
			metadataMap.putAll(this.params);
			try (Session session = driver.session()) {
				session.run(this.query, params);
			}

			return segment;
		};
	}

	private TextSegmentTransformer getDefaultChildTextSegmentTransformer() {
		return segment -> getTextSegmentWithUniqueId(segment, neo4jEmbeddingStore.getIdProperty(), parentIdKey);
	}

	private void getAdditionalParams(String parentId) {
		Map<String, Object> params = new HashMap<>(this.params);
		params.put(parentIdKey, parentId);
		neo4jEmbeddingStore.setAdditionalParams(params);
	}

	public static TextSegment getTextSegmentWithUniqueId(TextSegment segment, String idProperty, String parentId) {
		Metadata metadata1 = segment.metadata();
		Object idMeta = metadata1.toMap().get(idProperty);
		String value = parentId == null ? randomUUID() : parentId + "_" + randomUUID();
		if (idMeta != null) {
			value = idMeta + "_" + value;
		}
		metadata1.put(idProperty, value);

		return segment;
	}

	public Neo4jEmbeddingStore getEmbeddingStore() {
		return neo4jEmbeddingStore;
	}

	public static Neo4jGraphEmbeddingStoreIngestor.Builder builder() {
		return new Neo4jGraphEmbeddingStoreIngestor.Builder();
	}

	public static class Builder extends Neo4jParentChildEmbeddingStoreIngestor.Builder<Builder> {

		protected Driver driver;

		protected String query;

		protected String parentIdKey;

		protected Map<String, Object> params;

		protected String systemPrompt;

		protected String userPrompt;

		protected ChatModel questionModel;

		/**
		 * Creates a new EmbeddingStoreIngestor builder.
		 */
		public Builder() {
		}

		/**
		 * @param driver The {@link Driver} used to execute Cypher queries against the
		 * Neo4j database.
		 */
		public Builder driver(Driver driver) {
			this.driver = driver;
			return self();
		}

		/**
		 * @param query The Cypher query used to insert processed segments and metadata
		 * into Neo4j.
		 */
		public Builder query(String query) {
			this.query = query;
			return self();
		}

		/**
		 * @param parentIdKey The metadata key used to extract the parent segment ID; if
		 * absent, a UUID will be generated.
		 */
		public Builder parentIdKey(String parentIdKey) {
			this.parentIdKey = parentIdKey;
			return self();
		}

		/**
		 * @param params Additional query parameters to include in the Cypher execution,
		 * beyond segment metadata and text.
		 */
		public Builder params(Map<String, Object> params) {
			this.params = params;
			return self();
		}

		/**
		 * @param systemPrompt A system prompt for manipulating parent segment text via a
		 * {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
		 */
		public Builder systemPrompt(String systemPrompt) {
			this.systemPrompt = systemPrompt;
			return self();
		}

		/**
		 * @param userPrompt A user prompt for manipulating parent segment text via a
		 * {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
		 */
		public Builder userPrompt(String userPrompt) {
			this.userPrompt = userPrompt;
			return self();
		}

		/**
		 * @param questionModel A {@link ChatModel} used to further transform parent
		 * segment text based on provided prompts. If {@code null}, no chat-based
		 * manipulation occurs.
		 */
		public Builder questionModel(ChatModel questionModel) {
			this.questionModel = questionModel;
			return self();
		}

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		public Neo4jGraphEmbeddingStoreIngestor build() {
			return new Neo4jGraphEmbeddingStoreIngestor(this);
		}

	}

}
