package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.capsule.mapper.CapsuleMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ZenflowCapsuleSubjectSqlTest {
    @Test void realMapperOverloadsBindSubjectAndExcludeOtherUsersAndTenants() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE ai_capsule(id BIGINT PRIMARY KEY,tenant_id BIGINT,owner_user_id BIGINT,owner_subject_type VARCHAR(16),del_flag CHAR(1))");
        jdbc.update("INSERT INTO ai_capsule VALUES(99,1,42,'APP_USER','0'),(100,1,42,'ADMIN_USER','0'),(101,1,43,'APP_USER','0'),(102,2,42,'APP_USER','0')");
        var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setMapperLocations(new ClassPathResource("mapper/CapsuleMapper.xml"));
        try(var session=factory.getObject().openSession(true)) {
            var mapper=session.getMapper(CapsuleMapper.class);
            assertEquals(1,mapper.countOwnedExplicitIds(1L,42L,"APP_USER",List.of(99L,100L,101L,102L)));
            assertEquals(1,mapper.countOwnedExplicitIds(1L,42L,List.of(99L,100L,101L,102L)));
            assertEquals(0,mapper.countOwnedExplicitIds(1L,42L,List.of(99L)));
            assertEquals(0,mapper.countOwnedExplicitIds(1L,42L,"APP_USER",List.of(100L,101L,102L)));
        }
    }
}
