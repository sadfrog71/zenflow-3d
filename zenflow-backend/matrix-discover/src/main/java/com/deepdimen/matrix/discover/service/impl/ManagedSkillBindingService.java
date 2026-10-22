package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy.BindingConfiguration;
import com.deepdimen.matrix.skill.api.SkillContract;
import com.deepdimen.matrix.discover.skill.ManagedSkillSchemaValidator;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each business adapter explicitly authorizes its consumer before any managed mutation.
 */
@Service
@RequiredArgsConstructor
public class ManagedSkillBindingService {

	private final AiSkillBindingMapper bindings;

	private final AiSkillBindingRevisionMapper revisions;

	private final AiSkillReleaseMapper releases;

	private final List<SkillConsumerPolicy> policies;

	private final ObjectMapper json;

	public List<SkillConsumerPolicy.BindingTarget> targets(String consumerType, String beforeId) {
		var tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant <= 0 || actor == null || "UNIFIED_ASSISTANT".equalsIgnoreCase(consumerType))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var matching = policies.stream().filter(p -> p.consumerType().equals(consumerType)).toList();
		if (matching.size() != 1)
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		return matching.get(0).bindingTargets(tenant.toString(), String.valueOf(actor.getId()), beforeId);
	}

	public void validateDraft(Consumer consumer, RevisionInput input) {
		var tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant <= 0 || actor == null || consumer == null || input == null
				|| input.releaseId() == null || input.activationMode() == null || input.activationMode().isBlank())
			throw new IllegalArgumentException("绑定预校验需要当前身份、消费者与完整修订");
		var binding = new AiSkillBindingEntity();
		binding.setTenantId(tenant.toString());
		binding.setConsumerType(consumer.type());
		binding.setConsumerId(consumer.id());
		binding.setSlotCode(consumer.slotCode());
		binding.setBindingKey(consumer.bindingKey());
		var revision = new AiSkillBindingRevisionEntity();
		revision.setId(0L);
		revision.setReleaseId(input.releaseId());
		revision.setEnabled(input.enabled() ? 1 : 0);
		revision.setAudienceJson(objectJson(input.audienceJson()));
		revision.setActivationMode(input.activationMode());
		revision.setParameterJson(objectJson(input.parameterJson()));
		revision.setFallbackPolicy(objectJson(input.fallbackPolicy()));
		validate(binding, revision, true);
	}

	@Transactional(rollbackFor = Exception.class)
	public Long createDraft(Consumer consumer, RevisionInput input) {
		var binding = newBinding(consumer);
		// Validate before inserting the identity. The surrounding transaction also
		// protects both writes.
		saveRevision(binding, input);
		if (bindings.insert(binding) != 1) {
			throw new IllegalStateException("草稿绑定保存失败");
		}
		return binding.getId();
	}


	/** Inactive identity for an authorized business editor; no executable revision exists yet. */
	@Transactional(rollbackFor = Exception.class)
	public Long createIdentityDraft(Consumer consumer) {
		var binding = newBinding(consumer);
		if ("UNIFIED_ASSISTANT".equalsIgnoreCase(consumer.type()))
			throw new IllegalArgumentException("FROZEN_CONSUMER");
		var matching = policies.stream().filter(p -> p.consumerType().equals(consumer.type())).toList();
		if (matching.size() != 1)
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		matching.get(0).authorizeEmptyBinding(binding.getTenantId(),
				String.valueOf(SecurityUtils.getUser().getId()), consumer);
		if (bindings.insert(binding) != 1)
			throw new IllegalStateException("绑定草稿保存失败");
		return binding.getId();
	}

	private AiSkillBindingEntity newBinding(Consumer consumer) {
		Long tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant == 0L || actor == null || consumer == null) {
			throw new IllegalArgumentException("创建绑定需要当前租户和已认证操作者");
		}
		var binding = new AiSkillBindingEntity();
		binding.setId(IdWorker.getId());
		binding.setTenantId(tenant.toString());
		binding.setConsumerType(consumer.type());
		binding.setConsumerId(consumer.id());
		binding.setSlotCode(consumer.slotCode());
		binding.setBindingKey(consumer.bindingKey());
		binding.setRevision(0L);
		binding.setCreatedBy(actor.getUsername());
		binding.setUpdatedBy(actor.getUsername());
		binding.setCreatedAt(java.time.LocalDateTime.now());
		binding.setUpdatedAt(binding.getCreatedAt());
		return binding;
	}

	@Transactional(rollbackFor = Exception.class)
	public Long revise(Long bindingId, long expectedRevision, RevisionInput input) {
		var binding = lock(bindingId, expectedRevision);
		return saveRevision(binding, input);
	}

	private Long saveRevision(AiSkillBindingEntity binding, RevisionInput input) {
		if (input == null || input.releaseId() == null || input.activationMode() == null
				|| input.activationMode().isBlank()) {
			throw new IllegalArgumentException("绑定版本和触发方式不能为空");
		}
		var revision = new AiSkillBindingRevisionEntity();
		revision.setId(IdWorker.getId());
		revision.setTenantId(binding.getTenantId());
		revision.setBindingId(binding.getId());
		revision.setReleaseId(input.releaseId());
		revision.setEnabled(input.enabled() ? 1 : 0);
		revision.setAudienceJson(objectJson(input.audienceJson()));
		revision.setParameterJson(objectJson(input.parameterJson()));
		revision.setFallbackPolicy(objectJson(input.fallbackPolicy()));
		revision.setActivationMode(input.activationMode());
		revision.setRevision(revisions.nextRevision(binding.getTenantId(), binding.getId()));
		revision.setCreatedBy(SecurityUtils.getUser().getUsername());
		validate(binding, revision, false);
		if (revisions.insertRevision(revision) != 1) {
			throw new IllegalStateException("绑定修订保存失败");
		}
		return revision.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void activate(Long bindingId, Long revisionId, long expectedRevision) {
		var binding = lock(bindingId, expectedRevision);
		var revision = revisions.find(binding.getTenantId(), bindingId, revisionId);
		if (revision == null) {
			throw new IllegalArgumentException("绑定修订不存在");
		}
		var activatedRelease = validate(binding, revision, true);
		if (bindings.activateRevision(binding.getTenantId(), bindingId, revisionId, expectedRevision,
				SecurityUtils.getUser().getUsername()) != 1) {
			throw new IllegalStateException("BINDING_REVISION_CONFLICT");
		}
		policies.stream()
			.filter(p -> p.consumerType().equals(binding.getConsumerType()))
			.findFirst()
			.orElseThrow()
			.bindingActivated(binding.getTenantId(), String.valueOf(SecurityUtils.getUser().getId()),
					new Consumer(binding.getConsumerType(), binding.getConsumerId(), binding.getSlotCode(),
							binding.getBindingKey()),
					configuration(revision), activatedRelease);
	}

	private AiSkillBindingEntity lock(Long id, long expectedRevision) {
		Long tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant == 0L || actor == null || id == null || expectedRevision < 0) {
			throw new IllegalArgumentException("绑定操作需要当前租户、身份和预期版本");
		}
		var binding = bindings.lockOwned(tenant.toString(), id);
		if (binding == null || !tenant.toString().equals(binding.getTenantId())) {
			throw new IllegalArgumentException("绑定不存在");
		}
		if (binding.getRevision() == null || binding.getRevision() != expectedRevision) {
			throw new IllegalStateException("BINDING_REVISION_CONFLICT");
		}
		return binding;
	}

	private SkillContract.ResolvedSkillSnapshot validate(AiSkillBindingEntity binding,
			AiSkillBindingRevisionEntity revision, boolean activating) {
		if ("UNIFIED_ASSISTANT".equalsIgnoreCase(binding.getConsumerType())) {
			throw new IllegalArgumentException("FROZEN_CONSUMER");
		}
		var matching = policies.stream().filter(p -> p.consumerType().equals(binding.getConsumerType())).toList();
		if (matching.size() != 1) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		var consumer = new Consumer(binding.getConsumerType(), binding.getConsumerId(), binding.getSlotCode(),
				binding.getBindingKey());
		var policy = matching.get(0);
		policy.authorizeBinding(binding.getTenantId(), String.valueOf(SecurityUtils.getUser().getId()), consumer,
				configuration(revision));
		var release = releases.findOwned(binding.getTenantId(), revision.getReleaseId());
		if (release == null || (activating && Integer.valueOf(1).equals(revision.getEnabled())
				&& !"PUBLISHED".equals(release.getStatus()))) {
			throw new IllegalArgumentException("RELEASE_NOT_AVAILABLE");
		}
		validateParameters(revision, release);
		var validatedRelease = activating ? snapshot(revision, release) : null;
		if (activating && Integer.valueOf(1).equals(revision.getEnabled())) {
			policy.validateActivation(binding.getTenantId(), String.valueOf(SecurityUtils.getUser().getId()), consumer,
					configuration(revision), validatedRelease);
		}
		return validatedRelease;
	}

	private BindingConfiguration configuration(AiSkillBindingRevisionEntity revision) {
		return new BindingConfiguration(String.valueOf(revision.getId()), String.valueOf(revision.getReleaseId()),
				Integer.valueOf(1).equals(revision.getEnabled()), revision.getAudienceJson(),
				revision.getActivationMode(), revision.getParameterJson(), revision.getFallbackPolicy());
	}

	private SkillContract.ResolvedSkillSnapshot snapshot(AiSkillBindingRevisionEntity revision,
			AiSkillReleaseEntity release) {
		return new com.deepdimen.matrix.discover.skill.ManagedSkillReleaseCodec(json)
			.decode(String.valueOf(revision.getId()), release);
	}

	private void validateParameters(AiSkillBindingRevisionEntity revision, AiSkillReleaseEntity release) {
		try {
			var parameters = json.readTree(objectJson(revision.getParameterJson()));
			var allowed = json.readTree(release.getAllowedParameters());
			var schema = json.readTree(release.getInputSchema());
			var validator = new ManagedSkillSchemaValidator();
			validator.validateSchema(schema);
			if (allowed == null || !allowed.isArray()) {
				throw new IllegalArgumentException("发布参数声明无效");
			}
			java.util.Set<String> names = new java.util.HashSet<>();
			allowed.forEach(name -> {
				if (name.isTextual())
					names.add(name.asText());
			});
			parameters.fields().forEachRemaining(field -> {
				var fieldSchema = schema.path("properties").get(field.getKey());
				if (!names.contains(field.getKey()) || fieldSchema == null) {
					throw new IllegalArgumentException("绑定参数不在发布白名单内");
				}
				validator.validateValue(fieldSchema, field.getValue());
			});
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("绑定参数或发布契约无效", ex);
		}
	}

	private String objectJson(String value) {
		try {
			var node = json.reader()
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(value);
			if (node == null || !node.isObject()) {
				throw new IllegalArgumentException("绑定配置必须为 JSON 对象");
			}
			return node.toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("绑定配置无效", ex);
		}
	}

	public record RevisionInput(Long releaseId, boolean enabled, String audienceJson, String activationMode,
			String parameterJson, String fallbackPolicy) {
	}

	public BindingView findConsumer(Consumer consumer) {
		var tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant <= 0 || actor == null || consumer == null
				|| "UNIFIED_ASSISTANT".equalsIgnoreCase(consumer.type())
				|| policies.stream().filter(p -> p.consumerType().equals(consumer.type())).count() != 1)
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var binding = bindings
			.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiSkillBindingEntity>lambdaQuery()
				.eq(AiSkillBindingEntity::getTenantId, tenant.toString())
				.eq(AiSkillBindingEntity::getConsumerType, consumer.type())
				.eq(AiSkillBindingEntity::getConsumerId, consumer.id())
				.eq(AiSkillBindingEntity::getSlotCode, consumer.slotCode())
				.eq(AiSkillBindingEntity::getBindingKey, consumer.bindingKey()));
		return binding == null ? null : detail(binding.getId(), null);
	}

	public BindingView detail(Long bindingId, Long beforeId) {
		Long tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant <= 0 || actor == null || bindingId == null)
			throw new IllegalArgumentException("绑定查询需要当前租户与认证身份");
		var binding = bindings
			.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiSkillBindingEntity>lambdaQuery()
				.eq(AiSkillBindingEntity::getTenantId, tenant.toString())
				.eq(AiSkillBindingEntity::getId, bindingId));
		if (binding == null || !tenant.toString().equals(binding.getTenantId()))
			throw new IllegalArgumentException("绑定不存在");
		var matching = policies.stream().filter(p -> p.consumerType().equals(binding.getConsumerType())).toList();
		if (matching.size() != 1 || "UNIFIED_ASSISTANT".equalsIgnoreCase(binding.getConsumerType()))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var history = revisions.listOwned(tenant.toString(), bindingId, beforeId);
		// Even an empty history page must pass the consumer's business authorization.
		var anchor = beforeId == null ? history : revisions.listOwned(tenant.toString(), bindingId, null);
		if (anchor.isEmpty())
			throw new IllegalArgumentException("绑定修订不存在");
		matching.get(0)
			.authorizeBinding(
					tenant.toString(), String.valueOf(actor.getId()), new Consumer(binding.getConsumerType(),
							binding.getConsumerId(), binding.getSlotCode(), binding.getBindingKey()),
					configuration(anchor.get(0)));
		return new BindingView(binding, history);
	}

	public record BindingView(AiSkillBindingEntity binding, List<AiSkillBindingRevisionEntity> revisions) {
	}

}
