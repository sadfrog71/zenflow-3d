package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.common.file.core.FileProperties;
import com.deepdimen.matrix.common.file.local.LocalFileProperties;
import com.deepdimen.matrix.common.file.local.LocalFileTemplate;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit independent local storage only; no reuse of global file credentials, buckets or defaults. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="zenflow.media",name="enabled",havingValue="true")
@EnableConfigurationProperties(ZenflowMediaConfiguration.Properties.class)
public class ZenflowMediaConfiguration {
    @ConfigurationProperties(prefix="zenflow.media")
    public record Properties(boolean enabled,Path privateRoot,Path spoolDirectory,String bucket,String prefix,String storageRef,
            long maxBytes,double maxDurationSeconds,int maxAttachments,int uploadLifetimeSeconds,int leaseSeconds,
            Path ffprobe,Path ffmpeg,int inspectionTimeoutSeconds){}
    private final Clock clock=Clock.systemUTC();
    @Bean ZenflowMediaObjects zenflowMediaObjects(Properties p)throws IOException{
        privateDirectory(p.privateRoot());privateDirectory(p.spoolDirectory());
        var local=new LocalFileProperties();local.setBasePath(p.privateRoot().toRealPath().toString());
        var files=new FileProperties();files.setLocal(local);
        // Not registered as a second FileTemplate bean: existing enterprise injection stays unambiguous.
        return new ZenflowMediaObjects(new LocalFileTemplate(files),p.bucket(),p.prefix());
    }
    @Bean ZenflowAudioInspector zenflowAudioInspector(Properties p){return new ZenflowAudioInspector(p.ffprobe(),p.ffmpeg(),p.inspectionTimeoutSeconds());}
    @Bean ZenflowStageCleanup zenflowStageCleanup(JdbcTemplate jdbc,PlatformTransactionManager manager,ZenflowMediaObjects objects){return new ZenflowStageCleanup(jdbc,manager,objects,clock);}
    @Bean ZenflowMediaUploads zenflowMediaUploads(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ObjectMapper json,Properties p){
        return new ZenflowMediaUploads(jdbc,access,manager,json,clock,new ZenflowMediaUploads.Limits(p.maxBytes(),p.maxDurationSeconds(),Duration.ofSeconds(p.uploadLifetimeSeconds())));
    }
    @Bean ZenflowMediaTransfers zenflowMediaTransfers(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ZenflowMediaObjects objects,Properties p){return new ZenflowMediaTransfers(jdbc,access,manager,objects,p.spoolDirectory(),clock,Duration.ofSeconds(p.leaseSeconds()));}
    @Bean ZenflowMediaCompletion zenflowMediaCompletion(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ObjectMapper json,ZenflowMediaObjects objects,ZenflowAudioInspector inspector,Properties p){
        if(p.leaseSeconds()<p.inspectionTimeoutSeconds()*2+30)throw new IllegalArgumentException("MEDIA_PROCESSING_LEASE_TOO_SHORT");
        return new ZenflowMediaCompletion(jdbc,access,manager,json,objects,inspector,p.spoolDirectory(),clock,Duration.ofSeconds(p.leaseSeconds()),p.storageRef());
    }
    @Bean ZenflowNoteMedia zenflowNoteMedia(JdbcTemplate jdbc,ZenflowConsumerAccess access,Properties p){return new ZenflowNoteMedia(jdbc,access,clock,p.maxAttachments());}
    @Bean ZenflowMediaQueries zenflowMediaQueries(JdbcTemplate jdbc,ZenflowConsumerAccess access){return new ZenflowMediaQueries(jdbc,access);}
    public record SchemaVerified(boolean ready){}
    @Bean SchemaVerified zenflowMediaSchemaVerified(JdbcTemplate jdbc,ZenflowMediaUploads uploads,ZenflowMediaTransfers transfers,ZenflowMediaCompletion completion,ZenflowNoteMedia notes,ZenflowMediaObjects objects){
        jdbc.queryForList("SELECT id,subject_type,purpose,processing_state,version,declared_mime,declared_bytes,declared_duration_seconds,sha256,max_bytes,max_duration_seconds,expires_at,storage_ref,quarantine_key,object_key,processing_request_key,processing_base_version,actual_mime,actual_bytes,actual_duration_seconds FROM zf_media_asset WHERE 1=0");
        jdbc.queryForList("SELECT id,media_id,tenant_id,subject_type,owner_user_id,version,received_at,lease_token,lease_until FROM zf_media_upload WHERE 1=0");
        jdbc.queryForList("SELECT media_id,tenant_id,subject_type,owner_user_id,target_type,target_id,target_version,role FROM zf_media_binding WHERE 1=0");
        jdbc.queryForList("SELECT id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,state,attempts,lease_epoch,lease_owner,lease_until,next_attempt_at,error_code,version FROM zf_outbox WHERE 1=0");
        jdbc.queryForList("SELECT tenant_id,subject_type,owner_user_id,operation_type,client_request_id,payload_hash,state,result_type,result_id,result_version,safe_error_code FROM zf_client_operation WHERE 1=0");
        jdbc.queryForList("SELECT id,tenant_id,owner_subject_type,owner_user_id,current_revision_id,status,del_flag FROM ai_capsule WHERE 1=0");
        jdbc.queryForList("SELECT id,capsule_id,tenant_id,owner_subject_type,owner_user_id,revision_no FROM ai_capsule_revision WHERE 1=0");
        jdbc.queryForList("SELECT capsule_id,tenant_id,owner_user_id,delete_state FROM zf_capsule_ext WHERE 1=0");
        return new SchemaVerified(true);
    }
    private static void privateDirectory(Path path)throws IOException{
        if(path==null||!path.isAbsolute()||!Files.isDirectory(path)||Files.isSymbolicLink(path)||Files.getPosixFilePermissions(path).stream().anyMatch(permission->permission.name().startsWith("GROUP_")||permission.name().startsWith("OTHERS_")))throw new IOException("PRIVATE_MEDIA_DIRECTORY_REQUIRED");
    }
}
