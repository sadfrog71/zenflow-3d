package com.deepdimen.matrix.zenflow.companion;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import org.springframework.stereotype.Component;
@Component
public class ZenflowCompanionResultStore implements SkillResultStore {
    private final ZenflowCompanionRuntime runtime;
    public ZenflowCompanionResultStore(ZenflowCompanionRuntime runtime){this.runtime=runtime;}
    public String consumerType(){return ZenflowCompanionSkillPolicy.TYPE;}
    public String authorizeInput(Identity identity,Consumer consumer,String runId,String inputJson){return runtime.authorizeInput(identity,consumer,runId,inputJson);}
    public String save(Identity identity,Consumer consumer,String runId,String skillRunId,String output){return runtime.save(identity,consumer,runId,skillRunId,output);}
    public String read(Identity identity,Consumer consumer,String runId,String skillRunId,String ref){return runtime.read(identity,consumer,runId,skillRunId,ref);}
}
