package com.deepdimen.matrix.zenflow.admin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
class ZenflowHomeSlogansTest {
 @Test void scopedLibraryUpdatesConflictAndPublicViewOmitsDisabled(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE ALIAS UTC_TIMESTAMP FOR 'com.deepdimen.matrix.zenflow.companion.ZenflowReferenceAdminTest.utc'");jdbc.execute("CREATE TABLE zf_home_slogans(tenant_id BIGINT PRIMARY KEY,version BIGINT,items_json VARCHAR(20000),updated_at TIMESTAMP)");
  var p=new ZenflowProperties();p.setTenantId(1L);var service=new ZenflowHomeSlogans(jdbc,p,org.mockito.Mockito.mock(ZenflowAdminAccess.class),new ObjectMapper(),new DataSourceTransactionManager(ds));
  assertEquals(3,service.publicItems().size());var rows=List.of(Map.of("zh","让万物穿过自己。","en","Let everything pass through you.","enabled",true),Map.of("zh","停用句","en","Disabled","enabled",false));service.save(Map.of("expectedVersion",0,"items",rows));assertEquals(1,service.publicItems().size());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(Map.of("expectedVersion",0,"items",rows)));
  p.setTenantId(2L);assertEquals(3,service.publicItems().size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_home_slogans",Integer.class));
 }
 @Test void rejectsMalformedAndOversizedSlogans(){assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ZenflowHomeSlogans.validate(List.of(Map.of("zh"," ","en","","enabled",true))));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ZenflowHomeSlogans.validate(List.of(Map.of("zh","字".repeat(81),"en","","enabled",true))));}
}
