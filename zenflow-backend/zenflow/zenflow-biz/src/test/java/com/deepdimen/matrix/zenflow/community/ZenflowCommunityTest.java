package com.deepdimen.matrix.zenflow.community;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
class ZenflowCommunityTest {
 @Test void reviewWithdrawalAndOwnershipAreEnforcedAgainstRealStoredRows() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:community;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new JdbcTemplate(ds);
  String sql=Files.readString(Path.of("../../db/zenflow/P06_community.sql")).replaceAll("(?m)^--.*$", "");
  for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
  var consumer=mock(ZenflowConsumerAccess.class);var admin=mock(ZenflowAdminAccess.class);
  var owner=mock(MatrixUser.class);when(owner.getId()).thenReturn(11L);when(owner.getTenantId()).thenReturn(1L);when(owner.getNickname()).thenReturn("Me");
  var other=mock(MatrixUser.class);when(other.getId()).thenReturn(12L);when(other.getTenantId()).thenReturn(1L);
  when(consumer.require()).thenReturn(owner);when(admin.require()).thenReturn(owner);
  var service=new ZenflowCommunity(jdbc,consumer,admin,new DataSourceTransactionManager(ds));
  var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
  var image=service.upload(Map.of("base64",Base64.getEncoder().encodeToString(out.toByteArray())));String imageId=image.get("id").toString();
  when(consumer.require()).thenReturn(other);assertThrows(RuntimeException.class,()->service.image(imageId,false));assertThrows(RuntimeException.class,()->service.submit(Map.of("title","Not mine","text","Text","category","ESSAY","attachmentIds",List.of(imageId))));
  when(consumer.require()).thenReturn(owner);
  var created=service.submit(Map.of("title","A page","text","A moment of light","category","ESSAY","attachmentIds",List.of(imageId)));String id=created.get("id").toString();
  assertEquals("PENDING",created.get("state"));assertEquals(0,((List<?>)service.list(false,null,null,20).get("items")).size());
  when(consumer.require()).thenReturn(other);assertThrows(RuntimeException.class,()->service.detail(id));assertThrows(RuntimeException.class,()->service.withdraw(id,1));
  service.moderate(id,Map.of("action","APPROVE","expectedVersion",1,"featured",true));
  assertEquals("APPROVED",service.detail(id).get("state"));assertTrue(service.image(imageId,false).get("uri").toString().startsWith("data:image/png;base64,"));assertEquals(1,((List<?>)service.detail(id).get("images")).size());assertEquals(1,((List<?>)service.list(false,null,null,20).get("items")).size());
  when(consumer.require()).thenReturn(owner);service.withdraw(id,2);
  when(consumer.require()).thenReturn(other);assertThrows(RuntimeException.class,()->service.detail(id));assertEquals(0,((List<?>)service.list(false,null,null,20).get("items")).size());assertThrows(RuntimeException.class,()->service.image(imageId,false));
  when(consumer.require()).thenReturn(owner);var second=service.submit(Map.of("title","Second","text","Content","category","POEM","attachmentIds",List.of()));String secondId=second.get("id").toString();
  service.moderate(secondId,Map.of("action","REJECT","expectedVersion",1,"reviewNote","Please revise"));assertEquals("REJECTED",service.detail(secondId).get("state"));assertEquals("Please revise",service.detail(secondId).get("reviewNote"));
  var third=service.submit(Map.of("title","Third","text","Content","category","SHORT","attachmentIds",List.of()));String thirdId=third.get("id").toString();service.moderate(thirdId,Map.of("action","APPROVE","expectedVersion",1));service.moderate(thirdId,Map.of("action","TAKEDOWN","expectedVersion",2));when(consumer.require()).thenReturn(other);assertThrows(RuntimeException.class,()->service.detail(thirdId));
 }
}
