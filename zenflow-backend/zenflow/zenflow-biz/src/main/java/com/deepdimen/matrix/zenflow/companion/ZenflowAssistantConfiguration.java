package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.assistant.matrix.*;
import com.deepdimen.matrix.assistant.run.*;
import com.deepdimen.matrix.assistant.session.*;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.generation.AssistantNativeProfileResolver;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.orchestrator.AssistantContextOrchestrator;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.policy.AssistantIntentPolicy;
import com.deepdimen.matrix.common.sse.distribute.*;
import com.deepdimen.matrix.zenflow.admin.ZenflowModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

@Configuration(proxyBeanMethods=false)
public class ZenflowAssistantConfiguration {
    private final ObjectMapper storageJson=new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    @Bean public AssistantSessionRepository invelleSessions(JdbcTemplate jdbc,ObjectMapper json){return new JdbcAssistantSessionRepository(jdbc,storageJson);}
    @Bean public AssistantRunRepository invelleRuns(JdbcTemplate jdbc,ObjectMapper json,AssistantSessionRepository sessions){return new JdbcAssistantRunRepository(jdbc,storageJson,sessions);}
    @Bean public AssistantRunEventRepository invelleEvents(JdbcTemplate jdbc,ObjectMapper json){return new JdbcAssistantRunEventRepository(jdbc,storageJson);}
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(MessageDistributor.class)
    public MessageDistributor invelleMessages(){return new LocalMessageDistributor();}
    @Bean public AssistantRunEventBus invelleEventBus(MessageDistributor messages,ObjectMapper json){return new MatrixAssistantRunEventBus(messages,storageJson);}
    @Bean public AssistantRunEventService invelleEventService(AssistantRunEventRepository events,ObjectProvider<AssistantRunEventBus> bus){return new AssistantRunEventService(events,bus);}
    @Bean public AssistantRunDispatchService invelleDispatch(){return new AssistantRunDispatchService(2,32);}
    @Bean(destroyMethod="shutdown") public java.util.concurrent.ExecutorService invelleContextExecutor(){return java.util.concurrent.Executors.newFixedThreadPool(2,r->{var thread=new Thread(r,"invelle-context");thread.setDaemon(true);return thread;});}
    @Bean public AssistantContextOrchestrator invelleContext(java.util.concurrent.ExecutorService invelleContextExecutor){return new AssistantContextOrchestrator(new AssistantProviderRegistry(List.of()),invelleContextExecutor);}
    @Bean public AssistantPromptSkillCatalog invelleLegacyCatalog(){return new AssistantPromptSkillCatalog(){
        public List<PromptSkillDescriptor> listPromptSkills(IdentityEnvelope id){return List.of();}
        public PromptSkillSnapshot resolvePromptSkill(IdentityEnvelope id,String skill){throw new IllegalArgumentException("LEGACY_SKILL_NOT_AVAILABLE");}
        public void savePromptSkillPreference(IdentityEnvelope id,List<String> ids){if(!ids.isEmpty())throw new IllegalArgumentException("LEGACY_SKILL_NOT_AVAILABLE");}
    };}
    @Bean public AssistantNativeProfileResolver invelleProfile(ZenflowModelGateway gateway){return (run,id,stream,classification,business)->{
        var model=gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION,run,id,null,stream,classification,business));
        return new AssistantNativeProfileResolver.ResolvedNativeProfile(null,"Invelle",model.modelId(),model);
    };}
    @Bean public AssistantRunEngine invelleEngine(AssistantRunRepository runs,AssistantSessionRepository sessions,AssistantRunEventService events,AssistantRunDispatchService dispatch,AssistantContextOrchestrator context,AssistantPromptSkillCatalog catalog,AssistantNativeProfileResolver profile,ZenflowModelGateway gateway){return new AssistantRunEngine(runs,sessions,events,dispatch,context,catalog,profile,gateway,new AssistantIntentPolicy(),new AssistantPromptComposer());}
    @Bean public AssistantSessionService invelleSessionService(AssistantSessionRepository sessions,AssistantContextOrchestrator context,AssistantRunEngine engine){return new AssistantSessionService(sessions,context,engine);}
}
