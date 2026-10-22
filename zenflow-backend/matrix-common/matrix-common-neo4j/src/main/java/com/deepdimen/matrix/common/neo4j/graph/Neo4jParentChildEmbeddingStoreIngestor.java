//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.deepdimen.matrix.common.neo4j.graph;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.store.embedding.ParentChildEmbeddingStoreIngestor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Neo4j 父子文档嵌入存储处理器，用于处理文档的分层嵌入存储
 *
 * @author caoyuan
 * @date 2025/08/06
 * @experimental 实验性功能，API可能变更
 */
@Experimental
public class Neo4jParentChildEmbeddingStoreIngestor extends Neo4jEmbeddingStoreIngestor {

	private static final Logger log = LoggerFactory.getLogger(ParentChildEmbeddingStoreIngestor.class);

	private final DocumentTransformer documentTransformer;

	private final DocumentSplitter documentSplitter;

	protected TextSegmentTransformer textSegmentTransformer;

	protected TextSegmentTransformer childTextSegmentTransformer;

	private final EmbeddingModel embeddingModel;

	protected EmbeddingStore<TextSegment> embeddingStore;

	private final DocumentSplitter documentChildSplitter;

	public Neo4jParentChildEmbeddingStoreIngestor(DocumentTransformer documentTransformer,
			DocumentSplitter documentSplitter, TextSegmentTransformer textSegmentTransformer,
			TextSegmentTransformer childTextSegmentTransformer, EmbeddingModel embeddingModel,
			EmbeddingStore<TextSegment> embeddingStore, DocumentSplitter documentChildSplitter) {
		super(documentTransformer, documentSplitter, textSegmentTransformer, embeddingModel, embeddingStore);
		this.documentTransformer = documentTransformer;
		this.documentSplitter = documentSplitter;
		this.textSegmentTransformer = textSegmentTransformer;
		this.childTextSegmentTransformer = childTextSegmentTransformer;
		this.embeddingModel = embeddingModel;
		this.embeddingStore = embeddingStore;
		this.documentChildSplitter = documentChildSplitter;
	}

	public List<String> ingest(List<Document> documents) {
		log.debug("Starting to ingest {} documents", documents.size());
		if (this.documentTransformer != null) {
			documents = this.documentTransformer.transformAll(documents);
			log.debug("Documents were transformed into {} documents", documents.size());
		}

		List<TextSegment> segments;
		if (this.documentSplitter != null) {
			segments = this.documentSplitter.splitAll(documents);
			log.debug("Documents were split into {} text segments", segments.size());
		}
		else {
			segments = (List) documents.stream().map(Document::toTextSegment).collect(Collectors.toList());
		}

		if (this.textSegmentTransformer != null) {
			segments = this.textSegmentTransformer.transformAll(segments);
			log.debug("Text segments were transformed into {} text segments", documents.size());
		}

		if (this.documentChildSplitter == null) {
			log.debug("Starting to embed {} text segments", segments.size());
			Response<List<Embedding>> embeddingsResponse = this.embeddingModel.embedAll(segments);
			log.debug("Finished embedding {} text segments", segments.size());
			log.debug("Starting to store {} text segments into the embedding store", segments.size());
			return this.embeddingStore.addAll((List) embeddingsResponse.content(), segments);
		}
		else {
			TokenUsage totalUsage = new TokenUsage();

			List<String> ids = new ArrayList<>();
			for (TextSegment segment : segments) {
				Document parentDoc = Document.from(segment.text(), segment.metadata());
				List<TextSegment> childSegments = this.documentChildSplitter.split(parentDoc).stream().map((i) -> {
					assert this.childTextSegmentTransformer != null;

					return this.childTextSegmentTransformer.transform(i);
				}).toList();
				Response<List<Embedding>> embeddingsResponse = this.embeddingModel.embedAll(childSegments);
				ids.addAll(this.embeddingStore.addAll((List) embeddingsResponse.content(), childSegments));
			}
			return ids;
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder<B extends Builder<B>> extends Neo4jEmbeddingStoreIngestor.Builder {

		public DocumentTransformer documentTransformer;

		public DocumentSplitter documentSplitter;

		public DocumentSplitter documentChildSplitter;

		public TextSegmentTransformer textSegmentTransformer;

		public TextSegmentTransformer childTextSegmentTransformer;

		public EmbeddingModel embeddingModel;

		public EmbeddingStore<TextSegment> embeddingStore;

		protected B self() {
			return (B) this;
		}

		public B childTextSegmentTransformer(TextSegmentTransformer childTextSegmentTransformer) {
			this.childTextSegmentTransformer = childTextSegmentTransformer;
			return (B) this.self();
		}

		public B documentChildSplitter(DocumentSplitter documentChildSplitter) {
			this.documentChildSplitter = documentChildSplitter;
			return (B) this.self();
		}

		public B documentTransformer(DocumentTransformer documentTransformer) {
			this.documentTransformer = documentTransformer;
			return (B) this.self();
		}

		public B documentSplitter(DocumentSplitter documentSplitter) {
			this.documentSplitter = documentSplitter;
			return (B) this.self();
		}

		public B textSegmentTransformer(TextSegmentTransformer textSegmentTransformer) {
			this.textSegmentTransformer = textSegmentTransformer;
			return (B) this.self();
		}

		public B embeddingModel(EmbeddingModel embeddingModel) {
			this.embeddingModel = embeddingModel;
			return (B) this.self();
		}

		public B embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
			this.embeddingStore = embeddingStore;
			return (B) this.self();
		}

		public Neo4jParentChildEmbeddingStoreIngestor build() {
			return new Neo4jParentChildEmbeddingStoreIngestor(this.documentTransformer, this.documentSplitter,
					this.textSegmentTransformer, this.childTextSegmentTransformer, this.embeddingModel,
					this.embeddingStore, this.documentChildSplitter);
		}

	}

}
