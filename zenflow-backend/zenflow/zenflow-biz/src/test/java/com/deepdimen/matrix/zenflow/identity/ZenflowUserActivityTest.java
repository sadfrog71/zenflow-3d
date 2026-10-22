package com.deepdimen.matrix.zenflow.identity;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class ZenflowUserActivityTest {
 @Test void handlesMysqlAndH2TimestampRepresentations(){var value=Instant.parse("2026-10-07T00:00:00Z");assertEquals(value,ZenflowUserActivity.instant(java.sql.Timestamp.from(value)));assertEquals(value,ZenflowUserActivity.instant(java.time.LocalDateTime.ofInstant(value,java.time.ZoneOffset.UTC)));assertNull(ZenflowUserActivity.instant(null));}
 @Test void countsForegroundIntervalsOnceAndRejectsOfflineGaps(){
  var start=Instant.parse("2026-10-07T00:00:00Z");assertEquals(30000,ZenflowUserActivity.credit(start,start,start.plusSeconds(30)));
  assertEquals(15000,ZenflowUserActivity.credit(start,start.plusSeconds(15),start.plusSeconds(30)));
  assertEquals(0,ZenflowUserActivity.credit(start,start,start.plusSeconds(60)));assertEquals(0,ZenflowUserActivity.credit(start,start,start.minusSeconds(1)));
 }
 @Test void duplicatePulseDoesNotIncreaseUsageAndOtherOwnerCannotResumeSession(){
  var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE zf_user_activity(tenant_id BIGINT,owner_user_id BIGINT,active_millis BIGINT DEFAULT 0,last_seen_at TIMESTAMP,PRIMARY KEY(tenant_id,owner_user_id))");
  jdbc.execute("CREATE TABLE zf_user_activity_session(tenant_id BIGINT,owner_user_id BIGINT,session_key VARCHAR(36),sequence_no BIGINT,last_pulse_at TIMESTAMP,ended INT DEFAULT 0,PRIMARY KEY(tenant_id,owner_user_id,session_key))");
  var access=org.mockito.Mockito.mock(ZenflowConsumerAccess.class);var user=org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);org.mockito.Mockito.when(user.getId()).thenReturn(42L);org.mockito.Mockito.when(access.require()).thenReturn(user);
  var time=new java.time.Instant[]{Instant.parse("2026-10-07T00:00:00Z")};var clock=new java.time.Clock(){public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}public java.time.Clock withZone(java.time.ZoneId z){return this;}public Instant instant(){return time[0];}};
  var service=new ZenflowUserActivity(jdbc,access,new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds),clock);String key="00000000-0000-4000-8000-000000000000";
  service.pulse(new ZenflowUserActivity.Pulse(key,1,"START"));time[0]=time[0].plusSeconds(30);service.pulse(new ZenflowUserActivity.Pulse(key,2,"HEARTBEAT"));service.pulse(new ZenflowUserActivity.Pulse(key,2,"HEARTBEAT"));
  assertEquals(30000L,jdbc.queryForObject("SELECT active_millis FROM zf_user_activity WHERE owner_user_id=42",Long.class));
  org.mockito.Mockito.when(user.getId()).thenReturn(43L);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.pulse(new ZenflowUserActivity.Pulse(key,3,"HEARTBEAT")));
  org.mockito.Mockito.when(user.getId()).thenReturn(42L);time[0]=time[0].plusSeconds(60);service.pulse(new ZenflowUserActivity.Pulse(key,3,"STOP"));assertEquals(30000L,jdbc.queryForObject("SELECT active_millis FROM zf_user_activity WHERE owner_user_id=42",Long.class));
 }
}
