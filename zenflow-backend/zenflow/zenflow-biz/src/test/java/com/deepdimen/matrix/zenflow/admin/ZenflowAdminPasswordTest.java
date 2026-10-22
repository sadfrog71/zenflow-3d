package com.deepdimen.matrix.zenflow.admin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import org.springframework.web.server.ResponseStatusException;
class ZenflowAdminPasswordTest {
 @Test void validatesCurrentPasswordAndRevokesOldCredentialsWithMonotonicVersion(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE sys_user(user_id BIGINT,tenant_id BIGINT,username VARCHAR(64),password VARCHAR(128),email VARCHAR(254),lock_flag CHAR(1),del_flag CHAR(1),password_modify_time TIMESTAMP,password_expire_flag CHAR(1),update_time TIMESTAMP)");
  jdbc.execute("CREATE TABLE sys_role(role_id BIGINT,tenant_id BIGINT,role_code VARCHAR(64),del_flag CHAR(1))");jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
  var encoder=new BCryptPasswordEncoder();jdbc.update("INSERT INTO sys_user(user_id,tenant_id,username,password,lock_flag,del_flag) VALUES(42,1,'fixture',?,'0','0')",encoder.encode("old-fixture-pass"));jdbc.update("INSERT INTO sys_role VALUES(7,1,'ZF_ADMIN','0')");jdbc.update("INSERT INTO sys_user_role VALUES(42,7)");
  var properties=new ZenflowProperties();properties.setTenantId(1);var identities=new ZenflowAdminUserDetailsService(jdbc,properties);var access=new ZenflowAdminAccess(identities);var service=new ZenflowAdminPassword(jdbc,access,new DataSourceTransactionManager(ds));
  var prior=(MatrixUser)identities.loadUserByUsername("fixture");prior.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-admin");
  try{SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(prior,null,prior.getAuthorities()));
   assertThrows(ResponseStatusException.class,()->service.change("wrong-fixture-pass","new-fixture-pass"));assertThrows(ResponseStatusException.class,()->service.change("old-fixture-pass","short"));assertThrows(ResponseStatusException.class,()->service.change("old-fixture-pass","old-fixture-pass"));
   service.change("old-fixture-pass","new-fixture-pass");assertTrue(encoder.matches("new-fixture-pass",jdbc.queryForObject("SELECT password FROM sys_user WHERE user_id=42",String.class)));
   assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->identities.loadUserByUser(prior));assertThrows(ResponseStatusException.class,access::require);
   var next=(MatrixUser)identities.loadUserByUsername("fixture");next.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-admin");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(next,null,next.getAuthorities()));
   service.change("new-fixture-pass","third-fixture-pass");assertTrue(((MatrixUser)identities.loadUserByUsername("fixture")).getPasswordModifyTime().isAfter(next.getPasswordModifyTime()));
  }finally{SecurityContextHolder.clearContext();}
 }
}
