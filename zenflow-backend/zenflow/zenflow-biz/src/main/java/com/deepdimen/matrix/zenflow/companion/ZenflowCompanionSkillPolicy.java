package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowModelService;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

/** Product management authority. Worker execution remains denied until the lease adapter opts in. */
@Component
public class ZenflowCompanionSkillPolicy implements SkillConsumerPolicy {
    public static final String TYPE="ZENFLOW_COMPANION";
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    private final ZenflowAdminAccess access;
    private final ZenflowModelService models;
    private final ObjectMapper json;
    private final ObjectProvider<SkillPromptInvocationPort> invocations;
    public ZenflowCompanionSkillPolicy(JdbcTemplate jdbc,ZenflowProperties product,ZenflowAdminAccess access,
            ZenflowModelService models,ObjectMapper json,ObjectProvider<SkillPromptInvocationPort> invocations) {
        this.jdbc=jdbc;this.product=product;this.access=access;this.models=models;this.json=json;this.invocations=invocations;
    }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private ObjectProvider<ZenflowCompanionRuntime> runtime;
    @Override public String consumerType(){return TYPE;}
    public static Consumer consumer(String id){return new Consumer(TYPE,id,"COMPANION","default");}
    private void administrator(String tenant,String actor) {
        var user=access.require();
        if(!Long.toString(product.getTenantId()).equals(tenant) || !tenant.equals(user.getTenantId().toString())
                || !actor.equals(user.getId().toString()))deny();
    }
    private Map<String,Object> config(String tenant,Consumer consumer) {
        if(!TYPE.equals(consumer.type()) || !"COMPANION".equals(consumer.slotCode()) || !"default".equals(consumer.bindingKey())
                || !consumer.id().matches("[1-9][0-9]{0,18}") || !Long.toString(product.getTenantId()).equals(tenant))deny();
        var rows=jdbc.queryForList("SELECT * FROM zf_companion_config WHERE tenant_id=? AND id=? AND config_key='default'",tenant,consumer.id());
        if(rows.size()!=1)deny();return rows.get(0);
    }
    @Override public void authorizeEmptyBinding(String tenant,String actor,Consumer consumer) {
        administrator(tenant,actor);config(tenant,consumer);
    }
    @Override public void authorizeBinding(String tenant,String actor,Consumer consumer,BindingConfiguration binding) {
        administrator(tenant,actor);config(tenant,consumer);revision(tenant,consumer,binding);
    }
    private Map<String,Object> revision(String tenant,Consumer consumer,BindingConfiguration binding) {
        try {
            var parameters=json.readTree(binding.parameterJson());
            if(parameters==null || !parameters.isObject() || parameters.size()!=1 || !parameters.path("companionRevisionId").isTextual()
                    || !parameters.path("companionRevisionId").asText().matches("[1-9][0-9]{0,18}")
                    || !"ON_DEMAND".equals(binding.activationMode()) || !"{}".equals(binding.audienceJson())
                    || !"{}".equals(binding.fallbackPolicyJson()))deny();
            var rows=jdbc.queryForList("SELECT * FROM zf_companion_revision WHERE tenant_id=? AND config_id=? AND id=? AND skill_release_id=?",
                    tenant,consumer.id(),parameters.path("companionRevisionId").asText(),binding.releaseId());
            if(rows.size()!=1 || (binding.enabled() && !"READY".equals(rows.get(0).get("state"))))deny();
            return rows.get(0);
        } catch(java.io.IOException error){deny();return null;}
    }
    @Override public void validateActivation(String tenant,String actor,Consumer consumer,BindingConfiguration binding,ResolvedSkillSnapshot release) {
        authorizeBinding(tenant,actor,consumer,binding);var revision=revision(tenant,consumer,binding);
        String modelId=revision.get("model_id").toString();
        if(release.runtimeKind()!=RuntimeKind.PROMPT || !"PROMPT".equals(release.executorRef().executorKey())
                || !modelId.equals(release.executorRef().resourceId()) || !release.resourceRefs().isEmpty()
                || !release.releaseDigest().equals(revision.get("release_digest")))deny();
        String digest=new ZenflowCompanionPackage(json).modelDigest(modelId,models.publishedSnapshot(modelId));
        if(!digest.equals(revision.get("model_config_digest")))deny();
        var matching=invocations.stream().filter(p->TYPE.equals(p.consumerType())).toList();
        if(matching.size()!=1 || !matching.get(0).available(consumer,release))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"COMPANION_RUNTIME_UNAVAILABLE");
    }
    @Override public boolean permitsBackgroundExecution(Identity identity,Consumer consumer,String businessRunId) {
        return runtime!=null && runtime.getIfAvailable()!=null && runtime.getObject().permits(identity,consumer,businessRunId);
    }
    @Override public String pinnedBindingRevision(Identity identity,Consumer consumer,String businessRunId) {
        return permitsBackgroundExecution(identity,consumer,businessRunId)?runtime.getObject().pinned(identity,consumer,businessRunId):null;
    }
    @Override public void authorizeExecution(Identity identity,Consumer consumer,BindingConfiguration binding,ResolvedSkillSnapshot release) {
        if(runtime==null || runtime.getIfAvailable()==null)deny();
        runtime.getObject().authorize(identity,consumer,binding,release);
    }
    @Override public void bindingActivated(String tenant,String actor,Consumer consumer,BindingConfiguration binding,ResolvedSkillSnapshot release) {
        administrator(tenant,actor);config(tenant,consumer);revision(tenant,consumer,binding);
        if(jdbc.update("UPDATE zf_companion_config SET status=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
                binding.enabled()?"ENABLED":"DISABLED",tenant,consumer.id())!=1)deny();
    }
    private static void deny(){throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");}
}
