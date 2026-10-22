package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Upload owners may query processing metadata, but playback separately requires a live parent. */
public class ZenflowMediaQueries {
    public record Status(String mediaId,int version,String state,String purpose,String mime,Long bytes,Double durationSeconds){}
    private final JdbcTemplate jdbc;private final ZenflowConsumerAccess access;
    public ZenflowMediaQueries(JdbcTemplate jdbc,ZenflowConsumerAccess access){this.jdbc=jdbc;this.access=access;}
    public Status status(String id){
        try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(RuntimeException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_MEDIA_ID");}
        var owner=access.capsuleIdentity();if(owner==null||!"APP_USER".equals(owner.subjectType()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
        var rows=jdbc.queryForList("SELECT id,version,processing_state,purpose,actual_mime,actual_bytes,actual_duration_seconds FROM zf_media_asset WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND processing_state NOT IN ('DELETE_REQUESTED','DELETED')",id,owner.tenantId(),owner.ownerUserId());
        if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"MEDIA_NOT_FOUND");var row=rows.get(0);
        return new Status(id,((Number)row.get("version")).intValue(),row.get("processing_state").toString(),row.get("purpose").toString(),(String)row.get("actual_mime"),row.get("actual_bytes")==null?null:((Number)row.get("actual_bytes")).longValue(),row.get("actual_duration_seconds")==null?null:((Number)row.get("actual_duration_seconds")).doubleValue());
    }
}
