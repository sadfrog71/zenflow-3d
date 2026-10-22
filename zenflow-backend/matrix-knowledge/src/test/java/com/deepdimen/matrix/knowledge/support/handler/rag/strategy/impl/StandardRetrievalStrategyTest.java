package com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.*;

class StandardRetrievalStrategyTest {

	@Test
	void fixedModelsBypassNameCacheAndReuseSearchAndDtoMapping() {
		var provider = mock(ModelProvider.class);
		var strategy = new StandardRetrievalStrategy(provider);
		var model = mock(DimensionAwareEmbeddingModel.class);
		when(model.embed("question")).thenReturn(Response.from(Embedding.from(new float[] { 1, 0 })));
		EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
		var first = new EmbeddingMatch<TextSegment>(0.9, "one", null,
				TextSegment.from("first", new Metadata().put("id", 1L)));
		var second = new EmbeddingMatch<TextSegment>(0.8, "two", null,
				TextSegment.from("second", new Metadata().put("id", 2L)));
		when(store.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(first, second)));
		var dataset = new AiDatasetEntity();
		dataset.setId(31L);
		dataset.setScore(30);
		dataset.setTopK(1);
		dataset.setRerankerModel("ranker");
		var request = new AiSliceReCallRequestDTO();
		request.setContent("question");
		request.setReRanking("1");
		request.setTopK(1);
		var result = strategy.performFixedRetrieval(request, dataset, store, model,
				(segments, query) -> Response.from(List.of(0.1, 0.9)));
		assertEquals(1, result.size());
		assertEquals(2L, result.get(0).getSliceId());
		assertEquals("second", result.get(0).getContent());
		verifyZeroInteractions(provider);
		assertThrows(IllegalArgumentException.class,
				() -> strategy.performFixedRetrieval(request, dataset, store, model, null));
	}

	@Test
	void ordinaryRetrievalStillUsesExistingModelSelection() {
		var provider = mock(ModelProvider.class);
		var model = mock(DimensionAwareEmbeddingModel.class);
		when(provider.getEmbeddingModel("original")).thenReturn(model);
		when(model.embed("question")).thenReturn(Response.from(Embedding.from(new float[] { 1, 0 })));
		EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
		when(store.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of()));
		var dataset = new AiDatasetEntity();
		dataset.setId(31L);
		dataset.setScore(30);
		dataset.setTopK(1);
		dataset.setEmbeddingModel("original");
		var request = new AiSliceReCallRequestDTO();
		request.setContent("question");
		assertTrue(new StandardRetrievalStrategy(provider).performRetrieval(request, dataset, store).isEmpty());
		verify(provider).getEmbeddingModel("original");
	}

}
