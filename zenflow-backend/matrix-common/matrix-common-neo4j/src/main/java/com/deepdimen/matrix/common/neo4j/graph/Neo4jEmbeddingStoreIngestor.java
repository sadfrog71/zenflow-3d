package com.deepdimen.matrix.common.neo4j.graph;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.spi.data.document.splitter.DocumentSplitterFactory;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * {@code Neo4jEmbeddingStoreIngestor} 代表一个摄取管道，负责将 {@link Document} 文档摄取到
 * {@link EmbeddingStore} 中。 <br>
 * <br>
 * 在最简单的配置中，{@code Neo4jEmbeddingStoreIngestor} 使用提供的 {@link EmbeddingModel} 对文档进行嵌入，
 * 并将它们及其 {@link Embedding} 存储在 {@code EmbeddingStore} 中。 <br>
 * <br>
 * 可选地，{@code Neo4jEmbeddingStoreIngestor} 可以使用提供的 {@link DocumentTransformer} 转换文档。
 * 如果你想在嵌入文档之前对其进行清理、丰富或格式化，这会很有用。 <br>
 * <br>
 * 可选地，{@code Neo4jEmbeddingStoreIngestor} 可以使用提供的 {@link DocumentSplitter} 将文档分割成
 * {@link TextSegment}。 如果文档很大，并且你想将它们分割成更小的片段以提高相似性搜索的质量并减少发送给 LLM 的提示的大小和成本，这会很有用。 <br>
 * <br>
 * 可选地，{@code Neo4jEmbeddingStoreIngestor} 可以使用 {@link TextSegmentTransformer} 转换
 * {@code TextSegment}。 如果你想在嵌入文本片段之前对其进行清理、丰富或格式化，这会很有用。 在每个 {@code TextSegment}
 * 中包含文档标题或简短摘要是提高相似性搜索质量的常见技术。
 *
 */
public class Neo4jEmbeddingStoreIngestor {

	private static final Logger log = LoggerFactory.getLogger(Neo4jEmbeddingStoreIngestor.class);

	/** 文档转换器，用于转换原始文档 */
	private final DocumentTransformer documentTransformer;

	/** 文档分割器，用于将文档分割成文本片段 */
	private final DocumentSplitter documentSplitter;

	/** 文本片段转换器，用于转换文本片段 */
	private final TextSegmentTransformer textSegmentTransformer;

	/** 嵌入模型，用于生成文本的向量嵌入 */
	private final EmbeddingModel embeddingModel;

	/** 嵌入存储，用于存储文本片段和嵌入向量 */
	private final EmbeddingStore<TextSegment> embeddingStore;

	/**
	 * 创建一个 {@code Neo4jEmbeddingStoreIngestor} 实例。
	 * @param documentTransformer 要使用的 {@link DocumentTransformer}。可选的。
	 * @param documentSplitter 要使用的 {@link DocumentSplitter}。可选的。如果未指定， 会尝试通过 SPI 加载一个（参见
	 * {@link DocumentSplitterFactory}）。
	 * @param textSegmentTransformer 要使用的 {@link TextSegmentTransformer}。可选的。
	 * @param embeddingModel 要使用的 {@link EmbeddingModel}。必需的。如果未指定， 会尝试通过 SPI 加载一个（参见
	 * {@link EmbeddingModelFactory}）。
	 * @param embeddingStore 要使用的 {@link EmbeddingStore}。必需的。
	 */
	public Neo4jEmbeddingStoreIngestor(DocumentTransformer documentTransformer, DocumentSplitter documentSplitter,
			TextSegmentTransformer textSegmentTransformer, EmbeddingModel embeddingModel,
			EmbeddingStore<TextSegment> embeddingStore) {
		this.documentTransformer = documentTransformer;
		this.documentSplitter = getOrDefault(documentSplitter, Neo4jEmbeddingStoreIngestor::loadDocumentSplitter);
		this.textSegmentTransformer = textSegmentTransformer;
		this.embeddingModel = ensureNotNull(
				getOrDefault(embeddingModel, Neo4jEmbeddingStoreIngestor::loadEmbeddingModel), "embeddingModel");
		this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
	}

	/**
	 * 通过 SPI 加载文档分割器。
	 * @return 加载的文档分割器，如果没有找到则返回 null
	 */
	private static DocumentSplitter loadDocumentSplitter() {
		Collection<DocumentSplitterFactory> factories = loadFactories(DocumentSplitterFactory.class);
		if (factories.size() > 1) {
			throw new RuntimeException("Conflict: multiple document splitters have been found in the classpath. "
					+ "Please explicitly specify the one you wish to use.");
		}

		for (DocumentSplitterFactory factory : factories) {
			DocumentSplitter documentSplitter = factory.create();
			log.debug("Loaded the following document splitter through SPI: {}", documentSplitter);
			return documentSplitter;
		}

		return null;
	}

	/**
	 * 通过 SPI 加载嵌入模型。
	 * @return 加载的嵌入模型，如果没有找到则返回 null
	 */
	private static EmbeddingModel loadEmbeddingModel() {
		Collection<EmbeddingModelFactory> factories = loadFactories(EmbeddingModelFactory.class);
		if (factories.size() > 1) {
			throw new RuntimeException("Conflict: multiple embedding models have been found in the classpath. "
					+ "Please explicitly specify the one you wish to use.");
		}

		for (EmbeddingModelFactory factory : factories) {
			EmbeddingModel embeddingModel = factory.create();
			log.debug("Loaded the following embedding model through SPI: {}", embeddingModel);
			return embeddingModel;
		}

		return null;
	}

	/**
	 * 将指定的 {@link Document} 摄取到指定的 {@link EmbeddingStore} 中。 使用通过 SPI 找到的
	 * {@link DocumentSplitter} 和 {@link EmbeddingModel} （参见
	 * {@link DocumentSplitterFactory} 和 {@link EmbeddingModelFactory}）。 对于 "Easy RAG"，导入
	 * {@code langchain4j-easy-rag} 模块，其中包含 {@code DocumentSplitterFactory} 和
	 * {@code EmbeddingModelFactory} 实现。
	 * @param document 要摄取的文档
	 * @param embeddingStore 嵌入存储
	 * @return 包含摄取过程相关信息的结果
	 */
	public static List<String> ingest(Document document, EmbeddingStore<TextSegment> embeddingStore) {
		return builder().embeddingStore(embeddingStore).build().ingest(document);
	}

	/**
	 * 将指定的 {@link Document} 列表摄取到指定的 {@link EmbeddingStore} 中。 使用通过 SPI 找到的
	 * {@link DocumentSplitter} 和 {@link EmbeddingModel} （参见
	 * {@link DocumentSplitterFactory} 和 {@link EmbeddingModelFactory}）。 对于 "Easy RAG"，导入
	 * {@code langchain4j-easy-rag} 模块，其中包含 {@code DocumentSplitterFactory} 和
	 * {@code EmbeddingModelFactory} 实现。
	 * @param documents 要摄取的文档列表
	 * @param embeddingStore 嵌入存储
	 * @return 包含摄取过程相关信息的结果
	 */
	public static List<String> ingest(List<Document> documents, EmbeddingStore<TextSegment> embeddingStore) {
		return builder().embeddingStore(embeddingStore).build().ingest(documents);
	}

	/**
	 * 将指定文档摄取到在创建此 {@code Neo4jEmbeddingStoreIngestor} 时指定的 {@link EmbeddingStore} 中。
	 * @param document 要摄取的文档
	 * @return 包含摄取过程相关信息的结果
	 */
	public List<String> ingest(Document document) {
		return ingest(singletonList(document));
	}

	/**
	 * 将指定文档数组摄取到在创建此 {@code Neo4jEmbeddingStoreIngestor} 时指定的 {@link EmbeddingStore} 中。
	 * @param documents 要摄取的文档数组
	 * @return 包含摄取过程相关信息的结果
	 */
	public List<String> ingest(Document... documents) {
		return ingest(asList(documents));
	}

	/**
	 * 将指定文档列表摄取到在创建此 {@code Neo4jEmbeddingStoreIngestor} 时指定的 {@link EmbeddingStore} 中。
	 * @param documents 要摄取的文档列表
	 * @return 包含摄取过程相关信息的结果
	 */
	public List<String> ingest(List<Document> documents) {

		log.debug("Starting to ingest {} documents", documents.size());

		if (documentTransformer != null) {
			documents = documentTransformer.transformAll(documents);
			log.debug("Documents were transformed into {} documents", documents.size());
		}
		List<TextSegment> segments;
		if (documentSplitter != null) {
			segments = documentSplitter.splitAll(documents);
			log.debug("Documents were split into {} text segments", segments.size());
		}
		else {
			segments = documents.stream().map(Document::toTextSegment).collect(toList());
		}
		if (textSegmentTransformer != null) {
			segments = textSegmentTransformer.transformAll(segments);
			log.debug("{} documents were transformed into {} text segments", documents.size(), segments.size());
		}

		log.debug("Starting to embed {} text segments", segments.size());
		Response<List<Embedding>> embeddingsResponse = embeddingModel.embedAll(segments);
		log.debug("Finished embedding {} text segments", segments.size());

		log.debug("Starting to store {} text segments into the embedding store", segments.size());
		return embeddingStore.addAll(embeddingsResponse.content(), segments);
	}

	/**
	 * 创建一个新的 Neo4jEmbeddingStoreIngestor 构建器。
	 * @return 构建器实例
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Neo4jEmbeddingStoreIngestor 构建器类。
	 */
	public static class Builder {

		/** 文档转换器 */
		private DocumentTransformer documentTransformer;

		/** 文档分割器 */
		private DocumentSplitter documentSplitter;

		/** 文本片段转换器 */
		private TextSegmentTransformer textSegmentTransformer;

		/** 嵌入模型 */
		private EmbeddingModel embeddingModel;

		/** 嵌入存储 */
		private EmbeddingStore<TextSegment> embeddingStore;

		/**
		 * 创建一个新的 Neo4jEmbeddingStoreIngestor 构建器。
		 */
		public Builder() {
		}

		/**
		 * 设置文档转换器。可选的。
		 * @param documentTransformer 文档转换器
		 * @return {@code this}
		 */
		public Builder documentTransformer(DocumentTransformer documentTransformer) {
			this.documentTransformer = documentTransformer;
			return this;
		}

		/**
		 * 设置文档分割器。可选的。如果未指定，会尝试通过 SPI 加载一个（参见 {@link DocumentSplitterFactory}）。
		 * 来自主模块（{@code langchain4j}）的 {@code DocumentSplitters.recursive()} 是一个很好的起点。
		 * @param documentSplitter 文档分割器
		 * @return {@code this}
		 */
		public Builder documentSplitter(DocumentSplitter documentSplitter) {
			this.documentSplitter = documentSplitter;
			return this;
		}

		/**
		 * 设置文本片段转换器。可选的。
		 * @param textSegmentTransformer 文本片段转换器
		 * @return {@code this}
		 */
		public Builder textSegmentTransformer(TextSegmentTransformer textSegmentTransformer) {
			this.textSegmentTransformer = textSegmentTransformer;
			return this;
		}

		/**
		 * 设置嵌入模型。必需的。如果未指定，会尝试通过 SPI 加载一个（参见 {@link EmbeddingModelFactory}）。
		 * @param embeddingModel 嵌入模型
		 * @return {@code this}
		 */
		public Builder embeddingModel(EmbeddingModel embeddingModel) {
			this.embeddingModel = embeddingModel;
			return this;
		}

		/**
		 * 设置嵌入存储。必需的。
		 * @param embeddingStore 嵌入存储
		 * @return {@code this}
		 */
		public Builder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
			this.embeddingStore = embeddingStore;
			return this;
		}

		/**
		 * 构建 Neo4jEmbeddingStoreIngestor 实例。
		 * @return Neo4jEmbeddingStoreIngestor 实例
		 */
		public Neo4jEmbeddingStoreIngestor build() {
			return new Neo4jEmbeddingStoreIngestor(documentTransformer, documentSplitter, textSegmentTransformer,
					embeddingModel, embeddingStore);
		}

	}

}
