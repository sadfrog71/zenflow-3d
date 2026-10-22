package com.deepdimen.matrix.zenflow.identity;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ZenflowUserActivity {
    private final JdbcTemplate jdbc;private final ZenflowConsumerAccess access;private final TransactionTemplate tx;private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ZenflowUserActivity(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager){this(jdbc,access,manager,Clock.systemUTC());}
    ZenflowUserActivity(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,Clock clock){this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.clock=clock;}
    public record Pulse(String sessionKey,long sequence,String state){}
    public void pulse(Pulse pulse){
        if(pulse.sessionKey()==null||!pulse.sessionKey().matches("[a-f0-9-]{36}")||pulse.sequence()<1||!java.util.Set.of("START","HEARTBEAT","STOP").contains(pulse.state()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ACTIVITY_INPUT_INVALID");
        var user=access.require();var now=Timestamp.from(clock.instant());
        tx.executeWithoutResult(ignored->{
            jdbc.update("INSERT IGNORE INTO zf_user_activity(tenant_id,owner_user_id) VALUES(?,?)",user.getTenantId(),user.getId());
            var aggregate=jdbc.queryForMap("SELECT last_seen_at FROM zf_user_activity WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",user.getTenantId(),user.getId());
            var rows=jdbc.queryForList("SELECT sequence_no,last_pulse_at,ended FROM zf_user_activity_session WHERE tenant_id=? AND owner_user_id=? AND session_key=? FOR UPDATE",user.getTenantId(),user.getId(),pulse.sessionKey());
            if(rows.isEmpty()){
                if(!"START".equals(pulse.state()))throw new ResponseStatusException(HttpStatus.CONFLICT,"ACTIVITY_START_REQUIRED");
                jdbc.update("INSERT INTO zf_user_activity_session(tenant_id,owner_user_id,session_key,sequence_no,last_pulse_at) VALUES(?,?,?,?,?)",user.getTenantId(),user.getId(),pulse.sessionKey(),pulse.sequence(),now);
            }else{
                var prior=rows.get(0);if(pulse.sequence()<=((Number)prior.get("sequence_no")).longValue()||((Number)prior.get("ended")).intValue()==1)return;
                if("START".equals(pulse.state()))return;
                long millis=credit(instant(prior.get("last_pulse_at")),instant(aggregate.get("last_seen_at")),now.toInstant());
                jdbc.update("UPDATE zf_user_activity SET active_millis=active_millis+? WHERE tenant_id=? AND owner_user_id=?",millis,user.getTenantId(),user.getId());
                jdbc.update("UPDATE zf_user_activity_session SET sequence_no=?,last_pulse_at=?,ended=? WHERE tenant_id=? AND owner_user_id=? AND session_key=?",pulse.sequence(),now,"STOP".equals(pulse.state())?1:0,user.getTenantId(),user.getId(),pulse.sessionKey());
            }
            jdbc.update("UPDATE zf_user_activity SET last_seen_at=? WHERE tenant_id=? AND owner_user_id=?",now,user.getTenantId(),user.getId());
        });
    }
    static Instant instant(Object value){if(value==null)return null;if(value instanceof Timestamp t)return t.toInstant();if(value instanceof LocalDateTime t)return t.toInstant(ZoneOffset.UTC);throw new IllegalArgumentException("Unsupported database timestamp");}
    static long credit(Instant pulse,Instant seen,Instant now){long gap=Duration.between(pulse,now).toMillis();if(gap<0||gap>45000)return 0;return Math.max(0,Duration.between(seen!=null&&seen.isAfter(pulse)?seen:pulse,now).toMillis());}
}
