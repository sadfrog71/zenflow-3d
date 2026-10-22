package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.AiSkillBindingEntity;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.discover.skill.*;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSkillBindingResolver implements SkillBindingResolver {

	private final AiSkillBindingMapper bindings;

	private final AiSkillBindingRevisionMapper revisions;

	private final AiSkillReleaseMapper releases;

	private final List<SkillConsumerPolicy> policies;

	private final SkillResolutionTokenStore tokens;

	private final ObjectMapper json;

	@Override
	public Resolution resolve(ResolveRequest request) {
		var prepared = prepare(request, false).resolution();
		return tokens.issue(prepared.identity(), prepared.consumer(), prepared.businessRunId(),
				prepared.parametersJson(), prepared.snapshot());
	}

	@Override
	public ResolvedSkillSnapshot inspect(Identity identity, Consumer consumer) {
		return prepare(new ResolveRequest(identity, consumer, null, "capability-inspection", "{}"), true).resolution()
			.snapshot();
	}

	public SkillConsumerPolicy.BindingConfiguration revalidate(SkillResolutionTokenStore.StoredResolution prior) {
		var current = prepare(new ResolveRequest(prior.identity(), prior.consumer(), null, prior.businessRunId(),
				prior.parametersJson()), false);
		if (!current.resolution().snapshot().equals(prior.snapshot())) {
            var left=json.valueToTree(current.resolution().snapshot());var right=json.valueToTree(prior.snapshot());
            var fields=new java.util.ArrayList<String>();left.fieldNames().forEachRemaining(name->{if(!java.util.Objects.equals(left.get(name),right.get(name)))fields.add(name);});
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Managed snapshot mismatch; fields={}",fields);
            throw new IllegalArgumentException("BINDING_REVISION_CONFLICT");
        }
		return current.configuration();
	}

	private Prepared prepare(ResolveRequest request, boolean inspection) {
		var user = SecurityUtils.getUser();
		var identity = request.identity();
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
			.getAuthentication();
		Long tenant = TenantContextHolder.getTenantId();
		var consumer = request.consumer();
		if ("UNIFIED_ASSISTANT".equalsIgnoreCase(consumer.type()))
			throw new IllegalArgumentException("FROZEN_CONSUMER");
		var matching = policies.stream().filter(p -> p.consumerType().equals(consumer.type())).toList();
		if (matching.size() != 1 || tenant == null || tenant == 0L || !tenant.toString().equals(identity.tenantId()))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		if (authentication == null && user == null) {
			if (inspection || !matching.get(0).permitsBackgroundExecution(identity, consumer, request.businessRunId()))
				throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		else if (authentication == null || !authentication.isAuthenticated() || user == null || user.getId() == null
				|| !user.getId().toString().equals(identity.actorId())
				|| ("APP_USER".equals(identity.actorType()) && !"1".equals(user.getUserType()))
				|| ("ADMIN_USER".equals(identity.actorType()) && "1".equals(user.getUserType()))) {
			// Never turn a mismatched, anonymous or unauthenticated user session into
			// a background service caller.
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}

		var query = Wrappers.<AiSkillBindingEntity>lambdaQuery()
			.eq(AiSkillBindingEntity::getTenantId, identity.tenantId())
			.eq(AiSkillBindingEntity::getConsumerType, consumer.type())
			.eq(AiSkillBindingEntity::getConsumerId, consumer.id())
			.eq(AiSkillBindingEntity::getSlotCode, consumer.slotCode())
			.eq(AiSkillBindingEntity::getBindingKey, consumer.bindingKey());
		if (request.bindingId() != null)
			query.eq(AiSkillBindingEntity::getId, request.bindingId());
		var binding = bindings.selectOne(query);
		if (binding == null || binding.getActiveRevisionId() == null)
			throw new IllegalArgumentException("BINDING_NOT_ACTIVE");
		var revision = revisions.find(identity.tenantId(), binding.getId(), binding.getActiveRevisionId());
		if (revision == null || !Integer.valueOf(1).equals(revision.getEnabled()))
			throw new IllegalArgumentException("BINDING_NOT_ACTIVE");
		// Ordinary consumers retain current-pointer semantics. Product workers may pin only registered leased runs.
		String pinned = inspection ? null : matching.get(0).pinnedBindingRevision(identity,consumer,request.businessRunId());
		if (pinned != null) {
			if (!"ZENFLOW_COMPANION".equals(consumer.type()) || !pinned.matches("[0-9]+"))
				throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
			revision=revisions.find(identity.tenantId(),binding.getId(),Long.valueOf(pinned));
			if (revision==null || !Integer.valueOf(1).equals(revision.getEnabled())) throw new IllegalArgumentException("BINDING_NOT_ACTIVE");
		}
		var release = releases.findOwned(identity.tenantId(), revision.getReleaseId());
		if (release == null || !"PUBLISHED".equals(release.getStatus()))
			throw new IllegalArgumentException("RELEASE_NOT_AVAILABLE");
		new ManagedSkillReleaseSnapshot(json).verify(release);
		var snapshot = new ManagedSkillReleaseCodec(json).decode(String.valueOf(revision.getId()), release);

		var config = new SkillConsumerPolicy.BindingConfiguration(String.valueOf(revision.getId()),
				String.valueOf(revision.getReleaseId()), true, revision.getAudienceJson(), revision.getActivationMode(),
				revision.getParameterJson(), revision.getFallbackPolicy());
		if (inspection)
			matching.get(0).authorizeInspection(identity, consumer, config, snapshot);
		else
			matching.get(0).authorizeExecution(identity, consumer, config, snapshot);
		try {
			var merged = json.readTree(revision.getParameterJson());
			var overrides = json
				.readTree(request.requestedParametersJson() == null ? "{}" : request.requestedParametersJson());
			if (merged == null || !merged.isObject() || overrides == null || !overrides.isObject())
				throw new IllegalArgumentException("INPUT_INVALID");
			var effective = ((com.fasterxml.jackson.databind.node.ObjectNode) merged).deepCopy();
			overrides.fields().forEachRemaining(f -> effective.set(f.getKey(), f.getValue()));
			var schema = json.readTree(snapshot.inputSchemaJson());
			effective.fields().forEachRemaining(f -> {
				var fieldSchema = schema.path("properties").get(f.getKey());
				if (!snapshot.allowedParameters().contains(f.getKey()) || fieldSchema == null)
					throw new IllegalArgumentException("INPUT_INVALID");
				new ManagedSkillSchemaValidator().validateValue(fieldSchema, f.getValue());
			});
			return new Prepared(new SkillResolutionTokenStore.StoredResolution(identity, consumer,
					request.businessRunId(), effective.toString(), snapshot, 0), config);
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("INPUT_INVALID", ex);
		}
	}

	private record Prepared(SkillResolutionTokenStore.StoredResolution resolution,
			SkillConsumerPolicy.BindingConfiguration configuration) {
	}

}
