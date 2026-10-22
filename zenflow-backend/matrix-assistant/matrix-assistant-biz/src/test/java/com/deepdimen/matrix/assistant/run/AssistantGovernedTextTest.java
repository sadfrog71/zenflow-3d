package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.session.*;
import com.deepdimen.matrix.assistant.orchestrator.AssistantContextOrchestrator;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


class AssistantGovernedTextTest {
    static final ResolvedAssistantModel MODEL=new ResolvedAssistantModel("matrix.assistant-model/v1","fixed-model","fixed","fixture",new ModelCapabilities(true,8192,1024,true),new AssistantEgress(DeploymentDomain.INTERNAL,"fixture",true,DataClassification.SENSITIVE,"test"));
    static class Fixture implements AutoCloseable {
        final IdentityEnvelope identity;
        final InMemoryAssistantSessionRepository sessions=new InMemoryAssistantSessionRepository();
        final InMemoryAssistantRunStore runs=new InMemoryAssistantRunStore(sessions);
        final java.util.concurrent.atomic.AtomicInteger gatewayCalls=new java.util.concurrent.atomic.AtomicInteger();
        final AssistantModelGateway gateway=new AssistantModelGateway(){
            public ResolvedAssistantModel resolve(ModelResolutionRequest request){gatewayCalls.incrementAndGet();throw new IllegalStateException("Direct gateway forbidden");}
            public reactor.core.publisher.Flux<ModelChunk> stream(AssistantModelRequest request){gatewayCalls.incrementAndGet();throw new IllegalStateException("Direct gateway forbidden");}
        };
        final AssistantRunDispatchService dispatch=new AssistantRunDispatchService(){@Override public void dispatch(String id,java.util.function.Consumer<String> worker){}};
        final AssistantRunEngine engine=new AssistantRunEngine(runs,sessions,new AssistantRunEventService(runs),dispatch,null,null,null,gateway,null,null);
        final com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession session;
        Fixture(){this("APP_USER","TEXT_CONVERSATION");}
        Fixture(String subject,String purpose){identity=new IdentityEnvelope("1","42",null,List.of(),List.of(),List.of(),"test",purpose,subject);session=new AssistantSessionService(sessions,new AssistantContextOrchestrator(new AssistantProviderRegistry(List.of())),engine).create(identity,new CreateSessionRequest("Private",List.of(),null));}
        String start(){return engine.startOrGet(identity,session,UUID.randomUUID().toString(),"请理解我的感受",List.of(),true,null,List.of(),null,null,null).runId();}
        public void close(){dispatch.destroy();}
    }
    static class Adapter implements AssistantGovernedTextExecutor {
        String finish="STOP";boolean doubleInvoke,refuseCompletion,cancel;Fixture fixture;boolean committed;
        public boolean supports(IdentityEnvelope identity){return "APP_USER".equals(identity.subjectType());}
        public Outcome execute(AssistantRunRecord run,Lease lease,java.util.function.Consumer<ModelChunk> observer) {
            assertNotNull(lease.current().ownerInstanceId());assertNull(lease.current().modelInvokedAt());lease.beforeInvocation(MODEL);
            assertNotNull(lease.current().modelInvokedAt());if(doubleInvoke)lease.beforeInvocation(MODEL);
            observer.accept(new ModelChunk(run.runId(),0,"这里有一点空间。",false,""));
            if(cancel){fixture.runs.requestCancel(run.runId(),Instant.now());lease.current();}
            observer.accept(new ModelChunk(run.runId(),1,"",true,finish));
            return new Outcome(MODEL,new AssistantExecutionTrace(run.runId(),com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan.PRIMARY_ONLY,"NATIVE_MANAGED","fixed-release",null,null,null,List.of("managed"),null));
        }
        public boolean complete(AssistantRunRecord run,Lease lease,Supplier<Boolean> completion){lease.current();if(refuseCompletion)throw new AssistantModelException("PRIVATE_SOURCE_REVOKED","授权已撤回");committed=completion.get();return committed;}
    }
    @Test void missingGovernedExecutorFailsBeforeNativeInvocation(){try(var f=new Fixture()){String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.FAILED,f.runs.findById(id).orElseThrow().status());assertNull(f.runs.findById(id).orElseThrow().modelInvokedAt());assertEquals(0,f.gatewayCalls.get());}}
    @Test void successfulGovernedStreamCompletesThroughOriginalAtomicExchange(){try(var f=new Fixture()){var adapter=new Adapter();f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.COMPLETED,f.runs.findById(id).orElseThrow().status());assertTrue(adapter.committed);assertTrue(f.runs.findAfter(id,0,100).stream().anyMatch(e->e.type()==RunEventType.RUN_COMPLETED));assertEquals(0,f.gatewayCalls.get());}}
    @Test void modelInvocationIsSingleAndGuarded(){try(var f=new Fixture()){var adapter=new Adapter();adapter.doubleInvoke=true;f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.FAILED,f.runs.findById(id).orElseThrow().status());assertFalse(adapter.committed);}}
    @Test void lengthTerminalDoesNotCreateCompletedAnswer(){try(var f=new Fixture()){var adapter=new Adapter();adapter.finish="LENGTH";f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.FAILED,f.runs.findById(id).orElseThrow().status());assertFalse(adapter.committed);}}
    @Test void revokedCompletionCannotCreateCompletedAnswer(){try(var f=new Fixture()){var adapter=new Adapter();adapter.refuseCompletion=true;f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.FAILED,f.runs.findById(id).orElseThrow().status());assertFalse(f.runs.findAfter(id,0,100).stream().anyMatch(e->e.type()==RunEventType.RUN_COMPLETED));}}
    @Test void cancellationObservedFromDurableRunEndsCancelled(){try(var f=new Fixture()){var adapter=new Adapter();adapter.cancel=true;adapter.fixture=f;f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.CANCELLED,f.runs.findById(id).orElseThrow().status());assertFalse(adapter.committed);}}
    @Test void dedicatedAdministratorExecutorUsesRealLeaseAndAtomicCompletion(){try(var f=new Fixture("ADMIN_USER","COMPANION_PREVIEW")){var adapter=new Adapter(){public boolean supports(IdentityEnvelope identity){return "ADMIN_USER".equals(identity.subjectType())&&"COMPANION_PREVIEW".equals(identity.purpose());}};f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.COMPLETED,f.runs.findById(id).orElseThrow().status());assertNotNull(f.runs.findById(id).orElseThrow().modelInvokedAt());assertTrue(adapter.committed);assertEquals(0,f.gatewayCalls.get());}}
    @Test void ambiguousAdministratorExecutorsFailBeforeInvocation(){try(var f=new Fixture("ADMIN_USER","COMPANION_PREVIEW")){var adapter=new Adapter(){public boolean supports(IdentityEnvelope identity){return "ADMIN_USER".equals(identity.subjectType())&&"COMPANION_PREVIEW".equals(identity.purpose());}};f.engine.governedTextExecutors(List.of(adapter,adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.FAILED,f.runs.findById(id).orElseThrow().status());assertNull(f.runs.findById(id).orElseThrow().modelInvokedAt());assertFalse(adapter.committed);assertEquals(0,f.gatewayCalls.get());}}
    @Test void administratorPreviewCancellationDoesNotCompleteAnAnswer(){try(var f=new Fixture("ADMIN_USER","COMPANION_PREVIEW")){var adapter=new Adapter(){public boolean supports(IdentityEnvelope identity){return "ADMIN_USER".equals(identity.subjectType())&&"COMPANION_PREVIEW".equals(identity.purpose());}};adapter.cancel=true;adapter.fixture=f;f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);assertEquals(RunStatus.CANCELLED,f.runs.findById(id).orElseThrow().status());assertFalse(adapter.committed);assertFalse(f.runs.findAfter(id,0,100).stream().anyMatch(e->e.type()==RunEventType.RUN_COMPLETED));}}
    @Test void sameNumericAppIdentityCannotAccessAdministratorPreviewSession(){try(var f=new Fixture("ADMIN_USER","COMPANION_PREVIEW")){var sessions=new AssistantSessionService(f.sessions,new AssistantContextOrchestrator(new AssistantProviderRegistry(List.of())),f.engine);var app=new IdentityEnvelope("1","42",null,List.of(),List.of(),List.of(),"app","TEXT_CONVERSATION","APP_USER");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->sessions.get(app,f.session.assistantSessionId()));assertEquals(f.session.assistantSessionId(),sessions.get(f.identity,f.session.assistantSessionId()).assistantSessionId());}}
    @Test void upstreamFailureKeepsDurablePartialForBothGovernedPurposes() {
        for(String subject:List.of("APP_USER","ADMIN_USER"))try(var f=new Fixture(subject,subject.equals("APP_USER")?"TEXT_CONVERSATION":"COMPANION_PREVIEW")) {
            var adapter=new Adapter(){
                public boolean supports(IdentityEnvelope identity){return subject.equals(identity.subjectType());}
                public Outcome execute(AssistantRunRecord run,Lease lease,java.util.function.Consumer<ModelChunk> observer){
                    lease.beforeInvocation(MODEL);
                    observer.accept(new ModelChunk(run.runId(),0,"已验证的部分回答。".repeat(100),false,""));
                    throw new AssistantModelException("MODEL_UPSTREAM_UNAVAILABLE","供应商中断");
                }
            };
            f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);
            var run=f.runs.findById(id).orElseThrow();assertEquals(RunStatus.FAILED,run.status());
            var response=f.sessions.findMessage(run.sessionId(),run.responseMessageId()).orElseThrow();
            assertTrue(response.content().startsWith("已验证的部分回答。"));
            assertEquals("已验证的部分回答。".repeat(100),response.content());
            assertEquals("MODEL_UPSTREAM_UNAVAILABLE",run.errorCode());
            assertEquals(com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageStatus.FAILED,response.status());
            assertFalse(f.runs.findAfter(id,0,100).stream().anyMatch(e->e.type()==RunEventType.RUN_COMPLETED));
        }
    }
    @Test void revokedCompletionDoesNotRetainPrivatePartialInFailedMessage() {
        try(var f=new Fixture()) {
            var adapter=new Adapter(){public Outcome execute(AssistantRunRecord run,Lease lease,java.util.function.Consumer<ModelChunk> observer){
                lease.beforeInvocation(MODEL);
                observer.accept(new ModelChunk(run.runId(),0,"私人派生内容".repeat(100),false,""));
                throw new AssistantModelException("PRIVATE_SOURCE_REVOKED","授权已撤回");
            }};
            f.engine.governedTextExecutors(List.of(adapter));String id=f.start();f.engine.executePending(id);
            var run=f.runs.findById(id).orElseThrow();var response=f.sessions.findMessage(run.sessionId(),run.responseMessageId()).orElseThrow();
            assertFalse(response.content().contains("私人派生内容"));assertEquals(RunStatus.FAILED,run.status());
        }
    }
}
