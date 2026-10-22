package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.*;
import com.deepdimen.matrix.knowledge.context.KnowledgeAssistantContextProvider;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.entity.*;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl.StandardRetrievalStrategy;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.skill.api.AgentSkillTargetReader;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

class KnowledgeSkillRetrievalTest {

	@Test
	void usesAuthorizedScopeAndResolvedModelsThenRechecksBeforeReturningEvidence() {
		var resources = mock(KnowledgeSkillResources.class);
		var context = mock(KnowledgeAssistantContextProvider.class);
		var models = mock(ModelProvider.class);
		var stores = mock(EmbeddingStoreService.class);
		var strategy = mock(StandardRetrievalStrategy.class);
		var service = new KnowledgeSkillRetrieval(resources, context, models, stores, strategy);
		var identity = new Identity("7", "3", "3", "run-9");
		var consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");
		var release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", "KNOWLEDGE", "77", "fixed"), "{}", "{}", "rules", List.of(), "{}", List.of());
		var config = KnowledgeMarketConfigurationSnapshot.capture(
				new AgentSkillTargetReader.Target("88", "knowledge",
						"{\"datasetIds\":[\"31\"],\"modelConfig\":{\"provider\":\"local\",\"modelName\":\"qwen\"}}"),
				new ObjectMapper());
		var actor = new IdentityEnvelope("7", "3", null, List.of(), List.of("9"), List.of(), "run-9", "MARKET_SKILL");
		var capture = new KnowledgeSkillResources.Capture(actor, config, "77", List.of(), "fixed");
		when(resources.verify(identity, consumer, release)).thenReturn(capture);
		var dataset = new AiDatasetEntity();
		dataset.setId(31L);
		dataset.setCollectionName("kb7");
		var embeddingEntity = new AiModelEntity();
		var rerankerEntity = new AiModelEntity();
		when(resources.resolveRetrieval(identity, consumer, release, 31L)).thenReturn(
				new KnowledgeSkillResources.Retrieval(dataset, embeddingEntity, rerankerEntity, null, "a".repeat(64)));
		var embedding = mock(DimensionAwareEmbeddingModel.class);
		var reranker = mock(ScoringModel.class);
		var store = mock(EmbeddingStore.class);
		when(models.buildEmbeddingModel(embeddingEntity)).thenReturn(embedding);
		when(models.buildScoringModel(rerankerEntity)).thenReturn(reranker);
		when(embedding.dimension()).thenReturn(3);
		when(stores.embeddingStoreFixed("kb7", null, 3, "a".repeat(64))).thenReturn(store);
		var request = new AiSliceReCallRequestDTO();
		request.setDatasetId(31L);
		when(context.queryMarketSkill(any(), any())).thenAnswer(call -> {
			ProviderQuery query = call.getArgument(0);
			assertEquals(actor, query.identity());
			assertEquals(List.of("31"), query.scope().explicitObjectIds());
			BiFunction<AiDatasetEntity, AiSliceReCallRequestDTO, List<AiSliceReCallDTO>> invoke = call.getArgument(1);
			invoke.apply(dataset, request);
			return ProviderResult.unavailable(ProviderId.KNOWLEDGE, "fixture");
		});
		service.query(identity, consumer, release, "question");
		verify(stores, never()).embeddingStore(anyString());
		verify(strategy).performFixedRetrieval(request, dataset, store, embedding, reranker);
		verify(models, never()).getEmbeddingModel(any());
		verify(models, never()).getRerankerModel(any());
		verify(resources, times(3)).verify(identity, consumer, release);
		when(resources.verify(identity, consumer, release)).thenReturn(capture, capture)
			.thenThrow(new IllegalArgumentException("EXECUTOR_VERSION_DRIFT"));
		assertThrows(IllegalArgumentException.class, () -> service.query(identity, consumer, release, "question"));
	}

}
