package com.deepdimen.matrix.common.milvus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.response.SearchResp;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE;
import static com.deepdimen.matrix.common.milvus.Generator.generateEmptyJsons;
import static com.deepdimen.matrix.common.milvus.Generator.generateEmptyScalars;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.stream.Collectors.toList;

class Mapper {

	private static final Gson GSON = new GsonBuilder().setObjectToNumberStrategy(LONG_OR_DOUBLE).create();

	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
	}.getType();

	static List<List<Float>> toVectors(List<Embedding> embeddings) {
		return embeddings.stream().map(Embedding::vectorAsList).collect(toList());
	}

	static List<String> toScalars(List<TextSegment> textSegments, int size) {
		return isNullOrEmpty(textSegments) ? generateEmptyScalars(size) : textSegmentsToScalars(textSegments);
	}

	static List<JsonObject> toMetadataJsons(List<TextSegment> textSegments, int size) {
		return isNullOrEmpty(textSegments) ? generateEmptyJsons(size)
				: textSegments.stream()
					.map(segment -> GSON.toJsonTree(segment.metadata().toMap()).getAsJsonObject())
					.collect(toList());
	}

	static JsonObject toMetadataJson(TextSegment textSegment) {
		return GSON.toJsonTree(textSegment.metadata().toMap()).getAsJsonObject();
	}

	static List<String> textSegmentsToScalars(List<TextSegment> textSegments) {
		return textSegments.stream().map(TextSegment::text).collect(toList());
	}

	static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(MilvusClientV2 milvusClient, SearchResp searchResp,
			String collectionName, FieldDefinition fieldDefinition, ConsistencyLevel consistencyLevel,
			boolean queryForVectorOnSearch) {
		List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

		List<SearchResp.SearchResult> searchResults = searchResp.getSearchResults().get(0);

		for (SearchResp.SearchResult searchResult : searchResults) {
			double score = searchResult.getScore();
			String rowId = searchResult.getId().toString();

			Object o = searchResult.getEntity().get(fieldDefinition.getVectorFieldName());
			// Embedding embedding = idToEmbedding.get(rowId);
			TextSegment textSegment = toTextSegment(searchResult, fieldDefinition);
			EmbeddingMatch<TextSegment> embeddingMatch = new EmbeddingMatch<>(
					RelevanceScore.fromCosineSimilarity(score), rowId, null, textSegment);
			matches.add(embeddingMatch);
		}

		return matches;
	}

	private static TextSegment toTextSegment(SearchResp.SearchResult searchResult, FieldDefinition fieldDefinition) {

		Map<String, Object> entity = searchResult.getEntity();
		String text = (String) entity.get(fieldDefinition.getTextFieldName());
		if (isNullOrBlank(text)) {
			return null;
		}

		if (!entity.containsKey(fieldDefinition.getMetadataFieldName())) {
			return TextSegment.from(text);
		}

		JsonObject metadata = (JsonObject) entity.get(fieldDefinition.getMetadataFieldName());
		return TextSegment.from(text, toMetadata(metadata));
	}

	private static Metadata toMetadata(JsonObject metadata) {
		Map<String, Object> metadataMap = GSON.fromJson(metadata, MAP_TYPE);
		metadataMap.forEach((key, value) -> {
			if (value instanceof BigDecimal) {
				// It is safe to convert. No information is lost, the "biggest" type
				// allowed in Metadata is double.
				metadataMap.put(key, ((BigDecimal) value).doubleValue());
			}
		});
		return Metadata.from(metadataMap);
	}

}
