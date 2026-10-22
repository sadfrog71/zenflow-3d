package com.deepdimen.matrix.knowledge.support.handler.rag.strategy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagHelperTest {

	@Test
	void shouldFuseRankedResultsAndRemoveDuplicates() {
		EmbeddingMatch<TextSegment> vectorOnly = match("vector-only");
		EmbeddingMatch<TextSegment> shared = match("shared");
		EmbeddingMatch<TextSegment> fullTextOnly = match("full-text-only");

		List<EmbeddingMatch<TextSegment>> result = RagHelper.mergeSearchResults(List.of(vectorOnly, shared),
				List.of(shared, fullTextOnly));

		assertThat(result).containsExactly(shared, vectorOnly, fullTextOnly);
	}

	@Test
	void shouldExpandCandidateCountWithinBounds() {
		assertThat(RagHelper.calculateCandidateLimit(2)).isEqualTo(20);
		assertThat(RagHelper.calculateCandidateLimit(5)).isEqualTo(25);
		assertThat(RagHelper.calculateCandidateLimit(20)).isEqualTo(50);
		assertThat(RagHelper.resolveFinalTopK(null, null)).isEqualTo(5);
	}

	@Test
	void shouldRerankOriginalMatchesAndApplyFinalTopK() {
		EmbeddingMatch<TextSegment> first = match("first");
		EmbeddingMatch<TextSegment> second = match("second");
		EmbeddingMatch<TextSegment> third = match("third");
		ScoringModel scoringModel = (segments, query) -> Response.from(List.of(0.2, 0.9, 0.5));

		List<EmbeddingMatch<TextSegment>> result = RagHelper.rerankWithModel(List.of(first, second, third), "query",
				scoringModel, 2);

		assertThat(result).containsExactly(second, third);
	}

	@Test
	void shouldKeepFusionOrderWhenRerankerFails() {
		EmbeddingMatch<TextSegment> first = match("first");
		EmbeddingMatch<TextSegment> second = match("second");
		EmbeddingMatch<TextSegment> third = match("third");
		ScoringModel failingModel = (segments, query) -> {
			throw new IllegalStateException("reranker unavailable");
		};

		List<EmbeddingMatch<TextSegment>> result = RagHelper.rerankWithModel(List.of(first, second, third), "query",
				failingModel, 2);

		assertThat(result).containsExactly(first, second);
	}

	@Test
	void fixedRerankerFailureAndMalformedScoresDoNotReturnFusionSuccess() {
		var matches = List.of(match("first"), match("second"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> RagHelper.rerankWithFixedModel(matches, "query", (segments, query) -> {
					throw new IllegalStateException("failed");
				}, 2));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> RagHelper
			.rerankWithFixedModel(matches, "query", (segments, query) -> Response.from(List.of(0.1)), 2));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> RagHelper
			.rerankWithFixedModel(matches, "query", (segments, query) -> Response.from(List.of(Double.NaN, 0.1)), 2));
		assertThat(RagHelper.rerankWithFixedModel(matches, "query", null, 1)).containsExactly(matches.get(0));
	}

	private EmbeddingMatch<TextSegment> match(String id) {
		return new EmbeddingMatch<>(0.8, id, null, TextSegment.from(id));
	}

}
