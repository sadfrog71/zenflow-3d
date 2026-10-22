package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.*;
import com.deepdimen.matrix.assistant.matrix.JdbcAssistantSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ZenflowJdbcSubjectTest {
    @Test void realSqlPersistsSubjectAndSeparatesSameIdDirectoryAndUpdates() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE ai_assistant_session(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),owner_user_id VARCHAR(64),owner_subject_type VARCHAR(16),title VARCHAR(200),title_source VARCHAR(16),status VARCHAR(32),active_scopes VARCHAR(256),active_scopes_json VARCHAR(1024),active_skill_code VARCHAR(128),last_source_route VARCHAR(256),last_message_at TIMESTAMP,archived_at TIMESTAMP,version BIGINT,message_sequence BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        var repository=new JdbcAssistantSessionRepository(jdbc,new ObjectMapper());var now=Instant.now();
        repository.saveSession(new AssistantSession("app","1","42","Private",SessionStatus.ACTIVE,List.of(),null,null,TitleSource.MANUAL,now,null,1,now,now,"APP_USER"));
        repository.saveSession(new AssistantSession("admin","1","42","Admin",SessionStatus.ACTIVE,List.of(),null,null,TitleSource.MANUAL,now,null,1,now,now,"ADMIN_USER"));
        jdbc.update("UPDATE ai_assistant_session SET message_sequence=2");
        assertEquals("APP_USER",repository.findSession("app").orElseThrow().ownerSubjectType());
        var owned=repository.findSessions("1","42",SessionStatus.ACTIVE,null,null,null,20,"APP_USER");
        assertEquals(1,owned.size());assertEquals("app",owned.get(0).assistantSessionId());
        assertTrue(repository.updateTitle("app","1","42","Admin attempted",1).isEmpty());
        assertTrue(repository.updateTitle("app","1","42","Changed",1,"APP_USER").isPresent());
        assertThrows(IllegalStateException.class,()->repository.recordRunStarted("app","1","42","Wrong",List.of(),null,null,now));
        repository.recordRunStarted("app","1","42","Right",List.of(),null,null,now,"APP_USER");
        assertEquals("APP_USER",repository.findSession("app").orElseThrow().ownerSubjectType());
    }
}
