package com.deepdimen.matrix.knowledge.skill;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.context.KnowledgeDatasetAccessService;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.mapper.AiEmbedStoreMapper;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.skill.api.KnowledgeSkillRelease;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.skill.api.SkillContract;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Captures current configuration fingerprints without persisting or returning model
 * credentials.
 */
@Component
public class KnowledgeSkillResources {

	private final KnowledgeMarketConfigurationReader configurations;

	private final KnowledgeDatasetAccessService access;

	private final AiModelMapper models;

	private final AiEmbedStoreMapper stores;

	private final ObjectProvider<AssistantIdentityResolver> identities;

	private final ObjectMapper json;

	public KnowledgeSkillResources(KnowledgeMarketConfigurationReader configurations,
			KnowledgeDatasetAccessService access, AiModelMapper models, AiEmbedStoreMapper stores,
			ObjectProvider<AssistantIdentityResolver> identities, ObjectMapper json) {
		this.configurations = configurations;
		this.access = access;
		this.models = models;
		this.stores = stores;
		this.identities = identities;
		this.json = json;
	}

	public record Capture(IdentityEnvelope identity, KnowledgeMarketConfigurationSnapshot configuration, String modelId,
			List<ResourceRef> resources, String digest) {
		public Capture {
			resources = List.copyOf(resources);
		}
	}

	/** Resolve the agent's declared chat model without default or ambiguous selection. */
	public Capture captureConfigured(SkillContract.Consumer consumer) {
		var config = configurations.read(consumer);
		var matches = models.selectList(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelType, "CHAT")
			.eq(AiModelEntity::getProvider, config.modelProvider())
			.eq(AiModelEntity::getModelName, config.modelName()));
		if (matches == null || matches.size() != 1 || matches.get(0) == null || matches.get(0).getId() == null)
			throw invalid();
		return capture(consumer, matches.get(0).getId().toString());
	}

	public Capture capture(SkillContract.Consumer consumer, String modelId) {
		var resolver = identities.getIfAvailable();
		if (resolver == null)
			throw new IllegalStateException("AGENT_ADAPTER_UNAVAILABLE");
		var current = resolver.currentIdentity();
		Long tenant = TenantContextHolder.getTenantId();
		if (current == null || tenant == null || !tenant.toString().equals(current.tenantId())
				|| current.userId() == null)
			throw denied();
		var identity = new IdentityEnvelope(current.tenantId(), current.userId(), current.currentOrgId(),
				current.accessibleOrgIds(), current.roleIds(), current.deptIds(), current.correlationId(),
				"MARKET_SKILL");
		var config = configurations.read(consumer);
		Long id;
		try {
			if (modelId == null || !modelId.matches("[1-9][0-9]{0,18}"))
				throw invalid();
			id = Long.valueOf(modelId);
		}
		catch (NumberFormatException failure) {
			throw invalid();
		}
		var model = models.selectById(id);
		if (model == null || !id.equals(model.getId()) || !"CHAT".equalsIgnoreCase(model.getModelType())
				|| !config.modelProvider().equals(model.getProvider())
				|| !config.modelName().equals(model.getModelName()))
			throw invalid();
		List<Long> requested = config.datasetIds().stream().map(Long::valueOf).toList();
		var datasets = access.accessibleDatasets(identity, requested);
		if (datasets == null || datasets.size() != requested.size()
				|| datasets.stream()
					.anyMatch(dataset -> dataset == null || !requested.contains(dataset.getId())
							|| !identity.tenantId().equals(dataset.getTenantId()))
				|| datasets.stream().map(AiDatasetEntity::getId).distinct().count() != requested.size())
			throw denied();
		var refs = new ArrayList<ResourceRef>();
		refs.add(new ResourceRef("agent:" + config.agentId(), config.digest(), config.digest()));
		refs.add(modelReference(model));
		for (var dataset : datasets) {
			var embedding = resolveRetrievalModel(dataset.getEmbeddingModel(), "Embedding");
			var reranker = dataset.getRerankerModel() == null || dataset.getRerankerModel().isBlank() ? null
					: resolveRetrievalModel(dataset.getRerankerModel(), "Reranker");
			refs.add(datasetReference(dataset, embedding, reranker, resolveStore(dataset)));
		}
		config.requireSame(configurations.read(consumer));
		refs.sort(Comparator.comparing(ResourceRef::resourceId));
		return new Capture(identity, config, modelId, refs, KnowledgeSkillRelease.configurationDigest(refs));
	}

	public Capture verify(SkillContract.Identity identity, SkillContract.Consumer consumer,
			SkillContract.ResolvedSkillSnapshot release) {
		var fixed = KnowledgeSkillRelease.from(release);
		if (identity == null || consumer == null || !Objects.equals(identity.actorId(), identity.subjectId())
				|| !fixed.agentId().equals(consumer.id()))
			throw denied();
		var current = capture(consumer, fixed.modelId());
		if (!current.identity().tenantId().equals(identity.tenantId())
				|| !current.identity().userId().equals(identity.actorId()))
			throw denied();
		if (!fixed.digest().equals(current.digest()))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
		var actor = current.identity();
		return new Capture(
				new IdentityEnvelope(actor.tenantId(), actor.userId(), actor.currentOrgId(), actor.accessibleOrgIds(),
						actor.roleIds(), actor.deptIds(), identity.correlationId(), "MARKET_SKILL"),
				current.configuration(), current.modelId(), current.resources(), current.digest());
	}

	/** Internal resolved inputs; credentials must never appear in diagnostics. */
	record Retrieval(AiDatasetEntity dataset, AiModelEntity embedding, AiModelEntity reranker, AiEmbedStoreEntity store,
			String configurationDigest) {
		@Override
		public String toString() {
			return "ResolvedKnowledgeRetrieval";
		}
	}

	Retrieval resolveRetrieval(SkillContract.Identity identity, SkillContract.Consumer consumer,
			SkillContract.ResolvedSkillSnapshot release, Long datasetId) {
		var verified = verify(identity, consumer, release);
		if (datasetId == null || !verified.configuration().datasetIds().contains(datasetId.toString()))
			throw denied();
		var datasets = access.accessibleDatasets(verified.identity(), List.of(datasetId));
		if (datasets == null || datasets.size() != 1)
			throw denied();
		var dataset = datasets.get(0);
		if (dataset == null || !datasetId.equals(dataset.getId()) || !identity.tenantId().equals(dataset.getTenantId()))
			throw denied();
		var embedding = resolveRetrievalModel(dataset.getEmbeddingModel(), "Embedding");
		var reranker = dataset.getRerankerModel() == null || dataset.getRerankerModel().isBlank() ? null
				: resolveRetrievalModel(dataset.getRerankerModel(), "Reranker");
		var store = resolveStore(dataset);
		var actual = datasetReference(dataset, embedding, reranker, store);
		if (!verified.resources().contains(actual))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
		return new Retrieval(dataset, embedding, reranker, store, actual.digest());
	}

	private AiEmbedStoreEntity resolveStore(AiDatasetEntity dataset) {
		if (dataset.getStoreId() == null || dataset.getStoreId() <= 0 || dataset.getCollectionName() == null
				|| dataset.getCollectionName().isBlank())
			throw new IllegalArgumentException("KNOWLEDGE_CONFIGURATION_UNVERIFIED");
		var store = stores.selectById(dataset.getStoreId());
		if (store == null || !dataset.getStoreId().equals(store.getStoreId()) || store.getStoreType() == null
				|| store.getStoreType().isBlank())
			throw new IllegalArgumentException("KNOWLEDGE_CONFIGURATION_UNVERIFIED");
		return store;
	}

	private ResourceRef datasetReference(AiDatasetEntity dataset, AiModelEntity embedding, AiModelEntity reranker,
			AiEmbedStoreEntity store) {
		return reference("dataset:" + dataset.getId(), values -> {
			values.put("id", dataset.getId());
			values.put("tenantId", dataset.getTenantId());
			values.put("collectionName", dataset.getCollectionName());
			values.put("storeId", dataset.getStoreId());
			values.put("storeConfiguration", reference("store:" + store.getStoreId(), config -> {
				config.put("storeId", store.getStoreId());
				config.put("storeType", store.getStoreType());
				config.put("uri", store.getUri());
				config.put("host", store.getHost());
				config.put("port", store.getPort());
				config.put("apiKey", store.getApiKey());
				config.put("useTls", store.getUseTls());
				config.put("extData", store.getExtData());
			}).digest());
			values.put("score", dataset.getScore());
			values.put("topK", dataset.getTopK());
			values.put("standardFlag", dataset.getStandardFlag());
			values.put("embeddingModel", dataset.getEmbeddingModel());
			values.put("embeddingConfiguration", modelReference(embedding).digest());
			values.put("rerankerConfiguration", reranker == null ? null : modelReference(reranker).digest());
			values.put("summaryModel", dataset.getSummaryModel());
			values.put("rerankerModel", dataset.getRerankerModel());
			values.put("preSummary", dataset.getPreSummary());
			values.put("preCompress", dataset.getPreCompress());
			values.put("multiRound", dataset.getMultiRound());
			values.put("emptyLlmFlag", dataset.getEmptyLlmFlag());
			values.put("emptyDesc", dataset.getEmptyDesc());
			values.put("sensitiveFlag", dataset.getSensitiveFlag());
			values.put("sensitiveMsg", dataset.getSensitiveMsg());

		});
	}

	/** Resolve the existing dataset model name without default or ambiguous selection. */
	AiModelEntity resolveRetrievalModel(String name, String type) {
		if (name == null || name.isBlank())
			throw invalid();
		var matches = models.selectList(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getName, name)
			.eq(AiModelEntity::getModelType, type));
		if (matches == null || matches.size() != 1)
			throw invalid();
		var model = matches.get(0);
		if (model == null || model.getId() == null || model.getId() <= 0 || !name.equals(model.getName())
				|| !type.equalsIgnoreCase(model.getModelType()))
			throw invalid();
		return model;
	}

	private ResourceRef modelReference(AiModelEntity model) {
		return reference("model:" + model.getId(), values -> {
			values.put("id", model.getId());
			values.put("modelType", model.getModelType());
			values.put("provider", model.getProvider());
			values.put("modelName", model.getModelName());
			values.put("responseLimit", model.getResponseLimit());
			values.put("temperature", model.getTemperature());
			values.put("topP", model.getTopP());
			values.put("baseUrl", model.getBaseUrl());
			values.put("endpoint", model.getEndpoint());
			values.put("azureDeploymentName", model.getAzureDeploymentName());
			values.put("geminiProject", model.getGeminiProject());
			values.put("geminiLocation", model.getGeminiLocation());
			values.put("extData", model.getExtData());
			values.put("apiKey", model.getApiKey());
			values.put("secretKey", model.getSecretKey());

			values.put("dimensions", model.getDimensions());
		});
	}

	private ResourceRef reference(String id, Consumer<TreeMap<String, Object>> populate) {
		var values = new TreeMap<String, Object>();
		populate.accept(values);
		try {
			String digest = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(values)));
			return new ResourceRef(id, digest, digest);
		}
		catch (Exception failure) {
			throw new IllegalStateException("KNOWLEDGE_CONFIGURATION_UNVERIFIED");
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("MODEL_BINDING_CHANGED");
	}

	private static org.springframework.security.access.AccessDeniedException denied() {
		return new org.springframework.security.access.AccessDeniedException("CONSUMER_NOT_AUTHORIZED");
	}

}
