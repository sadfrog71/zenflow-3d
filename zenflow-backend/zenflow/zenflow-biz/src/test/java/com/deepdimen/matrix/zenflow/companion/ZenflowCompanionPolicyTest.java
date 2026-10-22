package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.zenflow.admin.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZenflowCompanionPolicyTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ZenflowAdminAccess admin=mock(ZenflowAdminAccess.class);
    private final ZenflowModelService models=mock(ZenflowModelService.class);
    private final MatrixUser user=mock(MatrixUser.class);
    private final ObjectMapper json=new ObjectMapper();
    @SuppressWarnings("unchecked") private final ObjectProvider<SkillPromptInvocationPort> ports=mock(ObjectProvider.class);
    private ZenflowCompanionSkillPolicy policy() {
        var p=new ZenflowProperties();p.setTenantId(1);when(admin.require()).thenReturn(user);when(user.getTenantId()).thenReturn(1L);when(user.getId()).thenReturn(42L);
        when(jdbc.queryForList(anyString(),anyString(),anyString())).thenReturn(List.of(Map.of("id",123L,"skill_binding_id",777L)));
        when(jdbc.queryForList(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(List.of(revision()));
        return new ZenflowCompanionSkillPolicy(jdbc,p,admin,models,json,ports);
    }
    private Map<String,Object> model(){return Map.of("modelName","fixture","provider","OpenAI","baseUrl","https://example.org/v1","temperature",0.6,"maxOutputTokens",2048,"allowPrivateContext",false,"version",1);}
    private Map<String,Object> revision(){return Map.of("state","READY","model_id","456","release_digest","a".repeat(64),"model_config_digest",new ZenflowCompanionPackage(json).modelDigest("456",model()));}
    private SkillConsumerPolicy.BindingConfiguration binding(String parameters){return new SkillConsumerPolicy.BindingConfiguration("999","888",true,"{}","ON_DEMAND",parameters,"{}");}
    @Test void adminActorTenantAndExactConsumerAreRequired() {
        var policy=policy();var consumer=ZenflowCompanionSkillPolicy.consumer("123");
        policy.authorizeEmptyBinding("1","42",consumer);
        assertThrows(IllegalArgumentException.class,()->policy.authorizeEmptyBinding("1","43",consumer));
        assertThrows(IllegalArgumentException.class,()->policy.authorizeEmptyBinding("2","42",consumer));
        assertThrows(IllegalArgumentException.class,()->policy.authorizeEmptyBinding("1","42",new Consumer("ZENFLOW_COMPANION","123","OTHER","default")));
    }
    @Test void rawPromptReferencesAndBackgroundExecutionCannotBeInjected() {
        var policy=policy();var consumer=ZenflowCompanionSkillPolicy.consumer("123");
        policy.authorizeBinding("1","42",consumer,binding("{\"companionRevisionId\":\"123\"}"));
        assertThrows(IllegalArgumentException.class,()->policy.authorizeBinding("1","42",consumer,binding("{\"companionRevisionId\":\"123\",\"systemPrompt\":\"private\"}")));
        var identity=new Identity("1","42","42","run","APP_USER");
        assertFalse(policy.permitsBackgroundExecution(identity,consumer,"run"));
        assertNull(policy.pinnedBindingRevision(identity,consumer,"run"));
        assertThrows(IllegalArgumentException.class,()->policy.authorizeExecution(identity,consumer,binding("{}"),null));
    }
    @Test void activationChecksFrozenModelAndActualRuntimeAvailability() {
        var policy=policy();when(models.publishedSnapshot("456")).thenReturn(model());when(ports.stream()).thenAnswer(i->java.util.stream.Stream.empty());
        var release=new ResolvedSkillSnapshot("7","888","a".repeat(64),"999",RuntimeKind.PROMPT,new ExecutorRef("PROMPT",null,"456",null),"{}","{}","instructions",List.of(),"{}",List.of("companionRevisionId"));
        var consumer=ZenflowCompanionSkillPolicy.consumer("123");var binding=binding("{\"companionRevisionId\":\"123\"}");
        var error=assertThrows(ResponseStatusException.class,()->policy.validateActivation("1","42",consumer,binding,release));
        assertEquals("COMPANION_RUNTIME_UNAVAILABLE",error.getReason());
        var changed=new HashMap<>(model());changed.put("version",2);when(models.publishedSnapshot("456")).thenReturn(changed);
        assertThrows(IllegalArgumentException.class,()->policy.validateActivation("1","42",consumer,binding,release));
    }
}
