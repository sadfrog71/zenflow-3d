package com.deepdimen.matrix.knowledge.skill;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.*;
import com.deepdimen.matrix.knowledge.context.KnowledgeAssistantContextProvider;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl.StandardRetrievalStrategy;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.skill.api.SkillContract;

/**
 * Internal market retrieval orchestration; does not enable a market capability by itself.
 */
@Component
public class KnowledgeSkillRetrieval {

	private final KnowledgeSkillResources resources;

	private final KnowledgeAssistantContextProvider context;

	private final ModelProvider models;

	private final EmbeddingStoreService stores;

	private final StandardRetrievalStrategy retrieval;

	public KnowledgeSkillRetrieval(KnowledgeSkillResources resources, KnowledgeAssistantContextProvider context,
			ModelProvider models, EmbeddingStoreService stores, StandardRetrievalStrategy retrieval) {
		this.resources = resources;
		this.context = context;
		this.models = models;
		this.stores = stores;
		this.retrieval = retrieval;
	}

	public ProviderResult query(SkillContract.Identity identity, SkillContract.Consumer consumer,
			SkillContract.ResolvedSkillSnapshot release, String question) {
		var fixed = resources.verify(identity, consumer, release);
		var request = new ProviderQuery(AssistantContextContract.VERSION, UUID.randomUUID().toString(),
				identity.correlationId(), fixed.identity(), question,
				new ProviderScope(null, null, fixed.configuration().datasetIds(), "market", Map.of()),
				new ProviderLimits(20, 12000, 10000));
		var evidence = context.queryMarketSkill(request, (dataset, recall) -> {
			var resolved = resources.resolveRetrieval(identity, consumer, release, dataset.getId());
			var embedding = models.buildEmbeddingModel(resolved.embedding());
			var reranker = resolved.reranker() == null ? null : models.buildScoringModel(resolved.reranker());
			var store = stores.embeddingStoreFixed(resolved.dataset().getCollectionName(), resolved.store(),
					embedding.dimension(), resolved.configurationDigest());
			resources.verify(identity, consumer, release);
			return retrieval.performFixedRetrieval(recall, resolved.dataset(), store, embedding, reranker);
		});
		resources.verify(identity, consumer, release);
		return evidence;
	}

}
