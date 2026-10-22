package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.skill.api.SkillConsumerPolicy;
import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowModelService;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The trial binding never activates or updates the online companion configuration. */
@Component
public class ZenflowPreviewSkillPolicy implements SkillConsumerPolicy {

	public static final String TYPE = "ZENFLOW_COMPANION_PREVIEW";

	public static final String PURPOSE = "COMPANION_PREVIEW";

	private final JdbcTemplate jdbc;

	private final ZenflowProperties product;

	private final ZenflowAdminAccess administrators;

	private final ZenflowModelService models;

	private final ObjectMapper json;

	private final ObjectProvider<SkillPromptInvocationPort> ports;

	private final ObjectProvider<ZenflowPreviewLease> leases;

	public ZenflowPreviewSkillPolicy(JdbcTemplate jdbc, ZenflowProperties product, ZenflowAdminAccess administrators,
			ZenflowModelService models, ObjectMapper json, ObjectProvider<SkillPromptInvocationPort> ports,
			ObjectProvider<ZenflowPreviewLease> leases) {
		this.jdbc = jdbc;
		this.product = product;
		this.administrators = administrators;
		this.models = models;
		this.json = json;
		this.ports = ports;
		this.leases = leases;
	}

	public String consumerType() {
		return TYPE;
	}

	public static Consumer consumer(String previewId) {
		return new Consumer(TYPE, previewId, "PREVIEW", "default");
	}

	private Map<String, Object> owned(String tenant, String actor, Consumer consumer) {
		var administrator = administrators.require();
		if (!Long.toString(product.getTenantId()).equals(tenant)
				|| !tenant.equals(administrator.getTenantId().toString())
				|| !actor.equals(administrator.getId().toString()) || consumer == null || !TYPE.equals(consumer.type())
				|| !"PREVIEW".equals(consumer.slotCode()) || !"default".equals(consumer.bindingKey())
				|| !consumer.id().matches("[1-9][0-9]{0,18}"))
			deny();
		var rows = jdbc.queryForList(
				"SELECT companion_revision_id,skill_release_id,release_digest,model_id,model_config_digest FROM zf_companion_preview WHERE tenant_id=? AND administrator_id=? AND id=? AND status IN ('CREATED','ACTIVE') AND expires_at>UTC_TIMESTAMP(3)",
				tenant, actor, consumer.id());
		if (rows.size() != 1)
			deny();
		return rows.get(0);
	}

	public void authorizeEmptyBinding(String tenant, String actor, Consumer consumer) {
		owned(tenant, actor, consumer);
	}

	public void authorizeBinding(String tenant, String actor, Consumer consumer, BindingConfiguration binding) {
		var preview = owned(tenant, actor, consumer);
		try {
			var parameters = json.readTree(binding.parameterJson());
			if (!binding.enabled() || !"ON_DEMAND".equals(binding.activationMode())
					|| !"{}".equals(binding.audienceJson()) || !"{}".equals(binding.fallbackPolicyJson())
					|| parameters == null || !parameters.isObject() || parameters.size() != 1
					|| !parameters.path("companionRevisionId").isTextual()
					|| !preview.get("companion_revision_id")
						.toString()
						.equals(parameters.path("companionRevisionId").asText())
					|| !preview.get("skill_release_id").toString().equals(binding.releaseId()))
				deny();
			var revisions = jdbc.queryForList(
					"SELECT r.skill_release_id,r.release_digest,r.model_id,r.model_config_digest FROM zf_companion_revision r JOIN zf_companion_config c ON c.id=r.config_id AND c.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.id=? AND c.config_key='default' AND r.state='READY'",
					tenant, preview.get("companion_revision_id").toString());
			if (revisions.size() != 1)
				deny();
			var revision = revisions.get(0);
			for (String field : java.util.List.of("skill_release_id", "release_digest", "model_id",
					"model_config_digest"))
				if (preview.get(field) == null || revision.get(field) == null
						|| !preview.get(field).toString().equals(revision.get(field).toString()))
					deny();
			var digest = new ZenflowCompanionPackage(json).modelDigest(preview.get("model_id").toString(),
					models.publishedSnapshot(preview.get("model_id").toString()));
			if (!digest.equals(preview.get("model_config_digest")))
				deny();
		}
		catch (java.io.IOException failure) {
			deny();
		}
	}

	public void validateActivation(String tenant, String actor, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		authorizeBinding(tenant, actor, consumer, binding);
		var preview = owned(tenant, actor, consumer);
		if (release.runtimeKind() != RuntimeKind.PROMPT || !"PROMPT".equals(release.executorRef().executorKey())
				|| release.executorRef().provider() != null || !release.resourceRefs().isEmpty()
				|| !preview.get("model_id").toString().equals(release.executorRef().resourceId())
				|| !preview.get("release_digest").equals(release.releaseDigest()))
			deny();
		var matching = ports.stream().filter(port -> TYPE.equals(port.consumerType())).toList();
		if (matching.size() != 1 || !matching.get(0).available(consumer, release))
			deny();
	}

	public boolean permitsBackgroundExecution(Identity identity, Consumer consumer, String runId) {
		if (!isPreviewIdentity(identity))
			return false;
		var matching = leases.stream().toList();
		return matching.size() == 1 && matching.get(0).permits(identity, consumer, runId);
	}

	public void authorizeExecution(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		if (!isPreviewIdentity(identity))
			deny();
		var matching = leases.stream().toList();
		if (matching.size() != 1)
			deny();
		matching.get(0).authorize(identity, consumer, binding, release);
	}

	private boolean isPreviewIdentity(Identity identity) {
		return "ADMIN_USER".equals(identity.actorType()) && "ADMIN_USER".equals(identity.subjectType())
				&& identity.actorId().equals(identity.subjectId())
				&& Long.toString(product.getTenantId()).equals(identity.tenantId());
	}

	private static void deny() {
		throw new IllegalArgumentException("PREVIEW_NOT_AUTHORIZED");
	}

}
