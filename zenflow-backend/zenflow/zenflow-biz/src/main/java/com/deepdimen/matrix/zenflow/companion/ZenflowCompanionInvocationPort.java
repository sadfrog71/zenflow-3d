package com.deepdimen.matrix.zenflow.companion;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import org.springframework.stereotype.Component;
@Component
public class ZenflowCompanionInvocationPort implements SkillPromptInvocationPort {
    private final ZenflowCompanionRuntime runtime;
    public ZenflowCompanionInvocationPort(ZenflowCompanionRuntime runtime){this.runtime=runtime;}
    public String consumerType(){return ZenflowCompanionSkillPolicy.TYPE;}
    public boolean available(Consumer consumer,ResolvedSkillSnapshot snapshot){return runtime.available(consumer,snapshot);}
    public Output execute(String runId,Identity identity,Consumer consumer,ResolvedSkillSnapshot snapshot,String inputJson,Runnable beforeInvocation){return runtime.invoke(runId,identity,consumer,snapshot,inputJson,beforeInvocation);}
}
