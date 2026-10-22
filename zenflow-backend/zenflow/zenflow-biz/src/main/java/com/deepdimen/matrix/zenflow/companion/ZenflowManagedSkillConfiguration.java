package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.discover.service.impl.*;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.discover.skill.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/** Explicitly assemble managed services without enterprise controllers or legacy executors. */
@Configuration(proxyBeanMethods=false)
public class ZenflowManagedSkillConfiguration {
    @Bean public AgentSkillPackageParser invelleSkillParser(ObjectMapper json){return new AgentSkillPackageParser(json);}
    @Bean public ManagedSkillPackageService invelleSkillPackages(AiSkillMapper skills,AiSkillPackageMapper packages,AgentSkillPackageParser parser,ObjectMapper json){return new ManagedSkillPackageService(skills,packages,parser,json);}
    @Bean public ManagedSkillReleaseService invelleSkillReleases(AiSkillPackageMapper packages,AiSkillReleaseMapper releases,AgentSkillPackageParser parser,ObjectMapper json){return new ManagedSkillReleaseService(packages,releases,parser,json);}
    @Bean public ManagedSkillBindingService invelleSkillBindings(AiSkillBindingMapper bindings,AiSkillBindingRevisionMapper revisions,AiSkillReleaseMapper releases,List<SkillConsumerPolicy> policies,ObjectMapper json){return new ManagedSkillBindingService(bindings,revisions,releases,policies,json);}
    @Bean public SkillResolutionTokenStore invelleSkillTokens(StringRedisTemplate redis,ObjectMapper json){return new SkillResolutionTokenStore(redis,json);}
    @Bean public ManagedSkillBindingResolver invelleSkillResolver(AiSkillBindingMapper bindings,AiSkillBindingRevisionMapper revisions,AiSkillReleaseMapper releases,List<SkillConsumerPolicy> policies,SkillResolutionTokenStore tokens,ObjectMapper json){return new ManagedSkillBindingResolver(bindings,revisions,releases,policies,tokens,json);}
    @Bean public ManagedSkillRunIndex invelleSkillIndex(AiSkillRunMapper runs,ObjectMapper json){return new ManagedSkillRunIndex(runs,json);}
    @Bean public ManagedPromptSkillExecutor invellePromptExecutor(ObjectProvider<AssistantModelGateway> gateways,ObjectMapper json,List<SkillPromptInvocationPort> ports){return new ManagedPromptSkillExecutor(gateways,null,json,List.of(),ports);}
    @Bean public ManagedSkillExecutionService invelleSkillExecution(SkillResolutionTokenStore tokens,ManagedSkillBindingResolver resolver,ManagedSkillRunIndex index,ManagedPromptSkillExecutor prompt,List<SkillResultStore> stores,ObjectMapper json){return new ManagedSkillExecutionService(tokens,resolver,index,prompt,stores,json);}

}
