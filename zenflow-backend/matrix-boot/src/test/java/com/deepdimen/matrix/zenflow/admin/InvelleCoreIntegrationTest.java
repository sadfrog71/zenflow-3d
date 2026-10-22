package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.MatrixBootApplication;
import com.deepdimen.matrix.zenflow.conversation.ZenflowConversationFacade;
import com.deepdimen.matrix.zenflow.identity.*;
import com.deepdimen.matrix.zenflow.capsule.ZenflowCapsuleFacade;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

/** Optional integration against a dedicated SSH-forwarded candidate schema; transport exists only in test classes. */
@SpringBootTest(classes=MatrixBootApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"zenflow.core.cleanup-enabled=false","spring.data.redis.database=2","matrix.mybatis.show-sql=false"})
@Import(InvelleCoreIntegrationTest.Controlled.class)
@EnabledIfEnvironmentVariable(named="INVELLE_CORE_INTEGRATION",matches="1")
class InvelleCoreIntegrationTest {
    static final AtomicInteger invocations=new AtomicInteger();
    static final String ANSWER="受控验证：圆融与内在空间。\n";
    @TestConfiguration(proxyBeanMethods=false) static class Controlled {
        @Bean @Primary @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"zenflow.preview-qa","zenflow.preview-http-qa"},havingValue="false",matchIfMissing=true) ZenflowModelGateway controlledTransport(ZenflowModelService models,ZenflowProperties product,ObjectMapper json) {
            return new ZenflowModelGateway(models,product,json,(uri,key,body,sink,data)->{
                invocations.incrementAndGet();
                var marker=java.util.regex.Pattern.compile("citationKey=([A-Za-z0-9_-]+)").matcher(body);
                String response=marker.find()?ANSWER+"["+marker.group(1)+"] [UNKNOWN_SOURCE]\n":ANSWER;
                data.accept(json.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of("content",response))))));
                data.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");data.accept("[DONE]");
            });
        }
    }
    @Autowired JdbcTemplate jdbc;@Autowired ZenflowConversationFacade conversations;@Autowired ZenflowCapsuleFacade notes;@Autowired ZenflowUserDetailsService users;
    @Autowired ZenflowAdminUserDetailsService administrators;
    @Autowired ZenflowModelService models;
    @Autowired ZenflowModelGateway gateway;
    @Autowired com.deepdimen.matrix.zenflow.companion.ZenflowCompanionService companions;
    @Autowired com.deepdimen.matrix.zenflow.conversation.ZenflowCitations citations;
    @Autowired com.deepdimen.matrix.zenflow.companion.ZenflowCompanionPrepareWorker preparation;
    @Test void realManagedSkillAndEnginePersistCompleteWhileDeniedPrivateContextNeverInvokesTransport() throws Exception {
        String schema=jdbc.queryForObject("SELECT DATABASE()",String.class);
        assertNotNull(schema);assertTrue(schema.startsWith("zenflow_core_rehearsal_"),"Dedicated candidate database is mandatory");
        TenantContextHolder.setTenantId(1L);
        try {
            var user=(MatrixUser)users.loadUserByUsername(System.getenv("INVELLE_CORE_FIXTURE_EMAIL"));user.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-ios");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"",user.getAuthorities()));
            configure(user,false);
            var session=conversations.create(Map.of("title","Controlled complete-chain fixture","locale","zh-CN"));String sid=session.get("id").toString();
            int before=invocations.get();var accepted=conversations.send(sid,Map.of("clientMessageId",UUID.randomUUID().toString(),"text","此刻的空间","context",Map.of("capsules",List.of())));String rid=accepted.get("runId").toString();
            await(rid);var state=jdbc.queryForMap("SELECT status,draft_content,model_invoked_at FROM ai_assistant_execution_run WHERE id=?",rid);
            assertEquals("COMPLETED",state.get("status"));assertEquals(ANSWER,state.get("draft_content"));assertNotNull(state.get("model_invoked_at"));assertEquals(before+1,invocations.get());
            assertEquals(ANSWER,jdbc.queryForObject("SELECT m.content FROM ai_assistant_message m JOIN zf_companion_run_context x ON x.response_message_id=m.id WHERE x.run_id=? AND m.status='COMPLETED'",String.class,rid));
            assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT s.status FROM ai_skill_run s JOIN zf_companion_run_context x ON x.skill_run_id=s.id WHERE x.run_id=?",String.class,rid));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_assistant_run_event WHERE run_id=? AND event_type='RUN_COMPLETED'",Integer.class,rid));
            assertNull(jdbc.queryForObject("SELECT active_run_id FROM zf_conversation_binding WHERE session_id=?",String.class,sid));
            var retry=conversations.send(sid,Map.of("clientMessageId",jdbc.queryForObject("SELECT client_message_id FROM ai_assistant_execution_run WHERE id=?",String.class,rid),"text","此刻的空间","context",Map.of("capsules",List.of())));assertEquals(rid,retry.get("runId"));assertEquals(before+1,invocations.get());
            var note=notes.create(Map.of("clientRecordId",UUID.randomUUID().toString(),"kind","TEXT","title","Private fixture","text","PRIVATE_CONTROLLED_ROOT","tags",List.of()));
            var denied=conversations.send(sid,Map.of("clientMessageId",UUID.randomUUID().toString(),"text","Use selected note","context",Map.of("capsules",List.of(Map.of("id",note.get("id"),"revisionId",note.get("currentRevisionId"),"scope","TURN")))));
            await(denied.get("runId").toString());assertEquals("MODEL_EGRESS_DENIED",jdbc.queryForObject("SELECT error_code FROM ai_assistant_execution_run WHERE id=?",String.class,denied.get("runId")));assertEquals(before+1,invocations.get());
            configure(user,true);
            var cited=conversations.send(sid,Map.of("clientMessageId",UUID.randomUUID().toString(),"text","Use selected note with a real marker","context",Map.of("capsules",List.of(Map.of("id",note.get("id"),"revisionId",note.get("currentRevisionId"),"scope","TURN")))));
            String citedRun=cited.get("runId").toString();await(citedRun);
            assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?",String.class,citedRun));
            var cards=citations.list(citedRun);assertEquals(1,cards.size());assertEquals("PRIVATE_CONTROLLED_ROOT",cards.get(0).get("excerpt"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_run_source WHERE run_id=? AND cited_flag=1",Integer.class,citedRun));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_assistant_run_event WHERE run_id=? AND event_type='CITATION_AVAILABLE'",Integer.class,citedRun));
            var marker=cards.get(0).get("citationKey").toString();assertEquals(note.get("id"),citations.detail(citedRun,marker).get("noteId"));
            notes.patch(note.get("id").toString(),Map.of("text","Changed private fixture","expectedVersion",note.get("version")));
            assertTrue(citations.list(citedRun).isEmpty());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->citations.detail(citedRun,marker));
            System.out.println("INVELLE_CITATIONS_PASS: actual native marker, unknown refusal, one card/event, source revision invalidation");
        } finally {SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    }
    @SuppressWarnings("unchecked")
    private void configure(MatrixUser consumer,boolean allowPrivate) {
        var admin=(MatrixUser)administrators.loadUserByUsername("invelle-admin");admin.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-admin");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin,"",admin.getAuthorities()));
        try {
            var model=models.save(null,Map.of("name","Controlled native citation fixture","modelName","controlled-fixture","provider","FIXTURE","baseUrl","https://api.openai.com/v1","apiKey","invalid-controlled-only-key","temperature",0.6,"maxOutputTokens",2048,"allowPrivateContext",allowPrivate));
            String id=model.get("id").toString();int version=((Number)model.get("version")).intValue();gateway.probe(id,version);models.activate(id,version);
            var configuration=companions.createDraft(Map.of());var draft=((List<Map<String,Object>>)configuration.get("revisions")).get(0);
            configuration=companions.prepare(draft.get("id").toString(),Map.of("expectedVersion",draft.get("version"),"modelId",id));
            preparation.poll();configuration=companions.detail();
            companions.publish(draft.get("id").toString(),Map.of("expectedBindingVersion",configuration.get("bindingVersion")));
        } finally {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(consumer,"",consumer.getAuthorities()));}
    }
    private void await(String id) throws InterruptedException {
        for(int i=0;i<200;i++){String state=jdbc.queryForObject("SELECT status FROM ai_assistant_execution_run WHERE id=?",String.class,id);if(Set.of("COMPLETED","FAILED","CANCELLED").contains(state))return;Thread.sleep(100);}
        for(var entry:Thread.getAllStackTraces().entrySet()) {
            if(entry.getKey().getName().contains("assistant-run") || entry.getKey().getName().contains("boundedElastic")) {
                System.out.println("QA_THREAD "+entry.getKey().getName()+" "+entry.getKey().getState());
                for(var frame:entry.getValue())System.out.println("QA_FRAME "+frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());
            }
        }
        fail("Controlled native run did not become terminal");
    }
}
