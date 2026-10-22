package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.MatrixBootApplication;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.companion.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated MySQL rehearsal only; no public listener, consumer records or model calls. */
public class InvelleReferencesIntegrationMain {
    public static void main(String[] args) {
        if(!"1".equals(System.getenv("INVELLE_CORE_INTEGRATION")))throw new IllegalStateException("INTEGRATION_ENV_REQUIRED");
        int exit=1;
        try(var context=new SpringApplication(MatrixBootApplication.class).run(args)) {
            var jdbc=context.getBean(JdbcTemplate.class);String schema=jdbc.queryForObject("SELECT DATABASE()",String.class);
            assertNotNull(schema);assertTrue(schema.startsWith("zenflow_core_rehearsal_"));TenantContextHolder.setTenantId(1L);
            var actor=(MatrixUser)context.getBean(ZenflowAdminUserDetailsService.class).loadUserByUsername("invelle-admin");
            actor.getAttributes().put(SecurityConstants.CLIENT_ID,ZenflowAdminUserDetailsService.CLIENT);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
            var references=context.getBean(ZenflowReferenceAdmin.class);var sources=context.getBean(ZenflowReferenceSources.class);
            var companions=context.getBean(ZenflowCompanionService.class);
            String body="PUBLIC_REFERENCE_REHEARSAL\n"+"x".repeat(1999)+"🌿"+"long public material".repeat(150);
            var fields=new HashMap<String,Object>(Map.of("title","Controlled public reference","content",body,"kind","MATERIAL","locale","en","citationVisibility","INTERNAL","approved",false));
            var created=references.create(fields);String id=created.get("id").toString();assertEquals(body,references.detail(id).get("content"));
            var selection=new ZenflowReferencePolicy.Reference(id,"MATERIAL","en","INTERNAL");assertThrows(ResponseStatusException.class,()->sources.load(selection));
            fields.put("approved",true);fields.put("expectedSourceVersion",created.get("sourceVersion"));var approved=references.update(id,fields);
            assertThrows(ResponseStatusException.class,()->references.update(id,fields));assertEquals(body,sources.load(selection).fragments().stream().map(ZenflowReferenceSources.Fragment::content).collect(java.util.stream.Collectors.joining()));
            var draft=companions.createDraft(Map.of());var revision=firstRevision(draft);
            var policy=new ZenflowReferencePolicy(List.of(selection),6,0.7);
            var saved=companions.save(revision.get("id").toString(),Map.of("expectedVersion",revision.get("version"),"retrievalPolicy",policy.projection()));
            var savedRevision=firstRevision(saved);assertEquals(policy.projection(),savedRevision.get("retrievalPolicy"));
            var copied=companions.createDraft(Map.of("fromRevisionId",savedRevision.get("id")));assertEquals(policy.projection(),firstRevision(copied).get("retrievalPolicy"));
            var snapshots=context.getBean(ZenflowReferenceSnapshots.class);
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            String revisionId=savedRevision.get("id").toString();
            assertThrows(IllegalStateException.class,()->snapshots.capture(revisionId,policy));
            var frozen=tx.execute(status->{
                assertEquals(1,jdbc.update("UPDATE zf_companion_revision SET state='PREPARING' WHERE tenant_id=1 AND id=? AND state='DRAFT'",revisionId));
                return snapshots.capture(revisionId,policy);
            });
            assertNotNull(frozen);assertFalse(frozen.snapshots().isEmpty());
            assertEquals(body,frozen.snapshots().stream().map(ZenflowReferenceSnapshots.Snapshot::content).collect(java.util.stream.Collectors.joining()));
            assertEquals(frozen,snapshots.verify(revisionId,policy,frozen.digest()));
            fields.put("content","UPDATED_PUBLIC_REFERENCE_REHEARSAL");fields.put("expectedSourceVersion",approved.get("sourceVersion"));
            var edited=references.update(id,fields);
            assertEquals(frozen,tx.execute(status->snapshots.capture(revisionId,policy)));
            assertEquals(frozen,snapshots.verify(revisionId,policy,frozen.digest()));
            String snapshotId=frozen.snapshots().get(0).id();String original=frozen.snapshots().get(0).content();
            jdbc.update("UPDATE zf_reference_snapshot SET content='TAMPERED' WHERE tenant_id=1 AND id=?",snapshotId);
            assertThrows(ResponseStatusException.class,()->snapshots.verify(revisionId,policy,frozen.digest()));
            jdbc.update("UPDATE zf_reference_snapshot SET content=? WHERE tenant_id=1 AND id=?",original,snapshotId);
            assertEquals(frozen,snapshots.verify(revisionId,policy,frozen.digest()));
            references.revoke(id,Map.of("expectedSourceVersion",edited.get("sourceVersion")));assertThrows(ResponseStatusException.class,()->sources.load(selection));
            assertThrows(ResponseStatusException.class,()->snapshots.verify(revisionId,policy,frozen.digest()));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_reference_snapshot WHERE tenant_id=1 AND companion_revision_id=? AND status='VALID'",Integer.class,revisionId));
            jdbc.update("UPDATE zf_companion_revision SET state='FAILED',error_code='CONTROLLED_QA_ONLY' WHERE tenant_id=1 AND id=? AND state='PREPARING'",revisionId);
            assertEquals("0",jdbc.queryForObject("SELECT public_flag FROM ai_dataset WHERE id=(SELECT dataset_id FROM ai_document WHERE id=?)",String.class,id));
            // Controlled configuration metadata only: no vendor probe, request or real-key claim.
            var models=context.getBean(ZenflowModelService.class);
            var model=models.save(null,Map.of("name","Controlled preparation fixture","modelName","controlled-public-fixture","provider","FIXTURE","baseUrl","https://api.openai.com/v1","apiKey","invalid-controlled-only-key","temperature",0.6,"maxOutputTokens",2048,"allowPrivateContext",false));
            String modelId=model.get("id").toString();models.recordTest(modelId,((Number)model.get("version")).intValue());
            jdbc.update("UPDATE ai_model SET ext_data=JSON_SET(ext_data,'$.zenflowPublished',true) WHERE tenant_id=1 AND id=?",modelId);
            var worker=context.getBean(ZenflowCompanionPrepareWorker.class);
            var empty=firstRevision(companions.createDraft(Map.of()));String emptyId=empty.get("id").toString();
            Object activeBinding=companions.detail().get("activeBindingRevisionId");
            var queued=companions.prepare(emptyId,Map.of("expectedVersion",empty.get("version"),"modelId",modelId));
            assertEquals("PREPARING",firstRevision(queued).get("state"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_outbox WHERE tenant_id=1 AND aggregate_id=? AND event_type='COMPANION_PREPARE' AND state='PENDING'",Integer.class,emptyId));
            SecurityContextHolder.clearContext();TenantContextHolder.clear();worker.poll();
            assertNull(SecurityContextHolder.getContext().getAuthentication());assertNull(TenantContextHolder.getTenantId());
            TenantContextHolder.setTenantId(1L);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
            assertEquals("READY",jdbc.queryForObject("SELECT state FROM zf_companion_revision WHERE tenant_id=1 AND id=?",String.class,emptyId));
            assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM zf_outbox WHERE tenant_id=1 AND aggregate_id=? AND event_type='COMPANION_PREPARE'",String.class,emptyId));
            assertEquals(activeBinding,companions.detail().get("activeBindingRevisionId"));
            fields.put("expectedSourceVersion",references.detail(id).get("sourceVersion"));references.update(id,fields);
            var referenced=firstRevision(companions.createDraft(Map.of()));String referencedId=referenced.get("id").toString();
            referenced=firstRevision(companions.save(referencedId,Map.of("expectedVersion",referenced.get("version"),"retrievalPolicy",policy.projection())));
            companions.prepare(referencedId,Map.of("expectedVersion",referenced.get("version"),"modelId",modelId));worker.poll();
            assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM zf_companion_revision WHERE tenant_id=1 AND id=?",String.class,referencedId));
            assertEquals("REFERENCE_INDEX_NOT_READY",jdbc.queryForObject("SELECT error_code FROM zf_companion_revision WHERE tenant_id=1 AND id=?",String.class,referencedId));
            String pinned=jdbc.queryForObject("SELECT reference_digest FROM zf_companion_revision WHERE tenant_id=1 AND id=?",String.class,referencedId);assertNotNull(pinned);
            fields.put("content","EDITED_AFTER_FAILED_PREPARATION");fields.put("expectedSourceVersion",references.detail(id).get("sourceVersion"));references.update(id,fields);
            var retry=firstRevision(companions.detail());
            companions.prepare(referencedId,Map.of("expectedVersion",retry.get("version"),"modelId",modelId));
            // Simulate a crashed prior owner, then let a new leased worker recover it.
            jdbc.update("UPDATE zf_companion_revision SET lease_epoch=41,lease_owner='controlled-crashed-worker',lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE tenant_id=1 AND id=?",referencedId);
            jdbc.update("UPDATE zf_outbox SET state='RUNNING',lease_epoch=41,lease_owner='controlled-crashed-worker',lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE tenant_id=1 AND aggregate_id=? AND event_type='COMPANION_PREPARE' AND state='PENDING'",referencedId);
            worker.poll();assertEquals(42L,jdbc.queryForObject("SELECT lease_epoch FROM zf_companion_revision WHERE tenant_id=1 AND id=?",Long.class,referencedId));
            assertEquals(pinned,jdbc.queryForObject("SELECT reference_digest FROM zf_companion_revision WHERE tenant_id=1 AND id=?",String.class,referencedId));
            assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM zf_companion_revision WHERE tenant_id=1 AND id=?",String.class,referencedId));
            assertEquals(activeBinding,companions.detail().get("activeBindingRevisionId"));
            var embeddings=context.getBean(ZenflowEmbeddingModels.class);var objectMapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var embeddingInput=new HashMap<String,Object>(Map.of("name","Controlled embedding fixture","modelName","controlled-embedding-fixture","provider","FIXTURE","baseUrl","https://api.openai.com/v1","apiKey","invalid-controlled-embedding-only-key","dimensions",3));
            var embedding=embeddings.save(null,embeddingInput);String embeddingId=embedding.get("id").toString();int embeddingVersion=((Number)embedding.get("version")).intValue();
            assertFalse(embedding.containsKey("apiKey"));assertFalse(objectMapper.writeValueAsString(embeddings.list()).contains("invalid-controlled-embedding-only-key"));
            assertThrows(ResponseStatusException.class,()->embeddings.activate(embeddingId,embeddingVersion));
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            var embeddingGateway=new ZenflowEmbeddingGateway(embeddings,objectMapper,(endpoint,key,request)->{
                calls.incrementAndGet();assertTrue(endpoint.toString().endsWith("/embeddings"));assertFalse(request.contains(key));
                var input=objectMapper.readTree(request).path("input");var vectors=new ArrayList<Map<String,Object>>();
                for(int i=input.size()-1;i>=0;i--)vectors.add(Map.of("index",i,"embedding",List.of(0.1,0.2,0.3)));
                return objectMapper.writeValueAsString(Map.of("data",vectors));
            });
            assertThrows(ResponseStatusException.class,()->embeddingGateway.probe(embeddingId,embeddingVersion+1));assertEquals(0,calls.get());
            embeddingGateway.probe(embeddingId,embeddingVersion);assertEquals(1,calls.get());
            var activeChat=jdbc.queryForList("SELECT id FROM ai_model WHERE tenant_id=1 AND model_type='Chat' AND assistant_model='1' AND del_flag='0'",Long.class);
            embeddings.activate(embeddingId,embeddingVersion);var fixedEmbedding=embeddings.publishedSnapshot(embeddingId);String embeddingDigest=embeddings.digest(fixedEmbedding);
            assertEquals(embeddingId,embeddings.active().get("id"));
            assertEquals(activeChat,jdbc.queryForList("SELECT id FROM ai_model WHERE tenant_id=1 AND model_type='Chat' AND assistant_model='1' AND del_flag='0'",Long.class));
            assertEquals(2,embeddingGateway.embed(embeddingId,embeddingDigest,List.of("public one","public two")).size());
            embeddingInput.put("expectedVersion",embeddingVersion);assertThrows(ResponseStatusException.class,()->embeddings.save(embeddingId,embeddingInput));
            assertThrows(ResponseStatusException.class,()->embeddingGateway.embed(embeddingId,"changed",List.of("public one")));
            String embeddingMetadata=jdbc.queryForObject("SELECT ext_data FROM ai_model WHERE tenant_id=1 AND id=?",String.class,embeddingId);
            jdbc.update("UPDATE ai_model SET ext_data='damaged' WHERE tenant_id=1 AND id=?",embeddingId);
            var damaged=assertThrows(ResponseStatusException.class,()->embeddings.save(embeddingId,embeddingInput));assertEquals("EMBEDDING_CONFIGURATION_INVALID",damaged.getReason());
            jdbc.update("UPDATE ai_model SET ext_data=? WHERE tenant_id=1 AND id=?",embeddingMetadata,embeddingId);
            assertEquals(embeddingDigest,embeddings.digest(embeddings.publishedSnapshot(embeddingId)));
            System.out.println("INVELLE_EMBEDDING_CONFIG_PASS: original ai_model CRUD, redaction, bounded controlled probe, tested-before-publish, immutable fixed digest, separate Chat modality, damaged metadata refusal; no real vendor success claim");
            System.out.println("INVELLE_COMPANION_ASYNC_PASS: durable queue, actual managed empty-source release, delegated tenant restore, public freeze, failed index gate, crash recovery, stable retry pin, unchanged online binding");
            System.out.println("INVELLE_REFERENCE_ADMIN_PASS: dedicated MySQL CRUD, approval, CAS, unicode slices, draft selection/copy, freeze/retry, normal edit pinning, tamper rejection, revoke, no fake READY");exit=0;
        }catch(Throwable failure) {
            System.out.println("INVELLE_REFERENCE_ADMIN_FAIL: "+failure.getClass().getSimpleName());
            for(var frame:failure.getStackTrace())if(frame.getClassName().startsWith("com.deepdimen"))System.out.println("QA_FAILURE_FRAME "+frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());
        }finally {SecurityContextHolder.clearContext();TenantContextHolder.clear();}
        System.exit(exit);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> firstRevision(Map<String,Object> config) {
        return ((List<Map<String,Object>>)config.get("revisions")).get(0);
    }
}
