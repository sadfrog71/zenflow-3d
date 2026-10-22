package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.capsule.api.dto.CapsuleCreateRequest;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.service.CapsuleService;
import com.deepdimen.matrix.zenflow.capsule.ZenflowCapsuleFacade;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class ZenflowNotePrivacyTest {
    @Test void idempotentCreateReusesCoreBodyAndNeverAutomaticallyGrantsConsent() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE zf_profile(tenant_id BIGINT,owner_user_id BIGINT)");jdbc.update("INSERT INTO zf_profile VALUES(1,42)");
        jdbc.execute("CREATE TABLE zf_capsule_ext(capsule_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,kind VARCHAR(24),text_state VARCHAR(16),client_record_id VARCHAR(64),request_hash VARCHAR(64),delete_state VARCHAR(24) DEFAULT 'ACTIVE',pinned BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE zf_capsule_revision_source(capsule_revision_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,capsule_id BIGINT,source_kind VARCHAR(24),source_message_id VARCHAR(64),author_role VARCHAR(16))");
        jdbc.execute("CREATE TABLE zf_capsule_consent(id BIGINT,capsule_revision_id BIGINT,scope_type VARCHAR(16),scope_key VARCHAR(64),state VARCHAR(16),version INT,tenant_id BIGINT,owner_user_id BIGINT,capsule_id BIGINT)");
        var core=mock(CapsuleService.class);var access=mock(ZenflowConsumerAccess.class);var identity=new CapsuleIdentity(1L,42L,"APP_USER");when(access.capsuleIdentity()).thenReturn(identity);
        var note=new CapsuleDetailVO();note.setId(99L);note.setCurrentRevisionId(100L);note.setRevisionNo(1);note.setVersion(1);note.setTitle("Private");note.setContent("private-sentinel");note.setStatus(CapsuleStatus.ACTIVE);
        when(core.create(eq(identity),any())).thenReturn(note);when(core.detail(identity,99L)).thenReturn(note);
        var facade=new ZenflowCapsuleFacade(core,jdbc,access,new DataSourceTransactionManager(source),new ObjectMapper());
        var body=Map.<String,Object>of("clientRecordId","record-uuid-1","kind","TEXT","text","private-sentinel");
        assertEquals("99",facade.create(body).get("id"));assertEquals("99",facade.create(body).get("id"));
        verify(core,times(1)).create(eq(identity),any(CapsuleCreateRequest.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_capsule_ext",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_capsule_consent",Integer.class));
        assertEquals("MANUAL",jdbc.queryForObject("SELECT source_kind FROM zf_capsule_revision_source",String.class));
        assertThrows(ResponseStatusException.class,()->facade.create(Map.of("clientRecordId","record-uuid-1","text","different input")));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,43L,"APP_USER"));
        assertThrows(ResponseStatusException.class,()->facade.detail("99"));verify(core,never()).detail(new CapsuleIdentity(1L,43L,"APP_USER"),99L);
    }
    @Test void callerCannotInjectOwnerOrOpenUnimplementedMedia() {
        var core=mock(CapsuleService.class);var access=mock(ZenflowConsumerAccess.class);
        var facade=new ZenflowCapsuleFacade(core,mock(JdbcTemplate.class),access,mock(org.springframework.transaction.PlatformTransactionManager.class),new ObjectMapper());
        assertThrows(ResponseStatusException.class,()->facade.create(Map.of("ownerUserId","other","text","private")));
        assertThrows(ResponseStatusException.class,()->facade.create(Map.of("clientRecordId","record-uuid-1","kind","VOICE","text","private")));
        verifyNoMoreInteractions(core);
    }
    @Test void audioOnlyProductNoteBindsSameRevisionWithoutInventedTextOrMemoryConsent(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE zf_profile(tenant_id BIGINT,owner_user_id BIGINT)");jdbc.update("INSERT INTO zf_profile VALUES(1,42)");
        jdbc.execute("CREATE TABLE zf_capsule_ext(capsule_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,kind VARCHAR(24),text_state VARCHAR(16),client_record_id VARCHAR(64),request_hash VARCHAR(64),delete_state VARCHAR(24) DEFAULT 'ACTIVE',pinned BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE zf_capsule_revision_source(capsule_revision_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,capsule_id BIGINT,source_kind VARCHAR(24),source_message_id VARCHAR(64),author_role VARCHAR(16))");
        jdbc.execute("CREATE TABLE zf_capsule_consent(id BIGINT,capsule_revision_id BIGINT,scope_type VARCHAR(16),scope_key VARCHAR(64),state VARCHAR(16),version INT,tenant_id BIGINT,owner_user_id BIGINT,capsule_id BIGINT)");
        var core=mock(CapsuleService.class);var access=mock(ZenflowConsumerAccess.class);var identity=new CapsuleIdentity(1L,42L,"APP_USER");when(access.capsuleIdentity()).thenReturn(identity);
        var note=new CapsuleDetailVO();note.setId(99L);note.setCurrentRevisionId(100L);note.setRevisionNo(1);note.setVersion(1);note.setTitle("Original voice");note.setContent("");note.setStatus(CapsuleStatus.ACTIVE);
        when(core.create(eq(identity),any())).thenReturn(note);when(core.detail(identity,99L)).thenReturn(note);
        var media=mock(com.deepdimen.matrix.zenflow.media.ZenflowNoteMedia.class);String mediaId=UUID.randomUUID().toString();
        when(media.validateIds(any())).thenReturn(java.util.List.of(mediaId));when(media.list("99",1)).thenReturn(java.util.List.of(new com.deepdimen.matrix.zenflow.media.ZenflowNoteMedia.Media(mediaId,"audio/mp4",100,1)));
        var facade=new ZenflowCapsuleFacade(core,jdbc,access,new DataSourceTransactionManager(source),new ObjectMapper());facade.setMedia(media);
        var result=facade.create(Map.of("clientRecordId","voice-uuid-1","kind","VOICE","mediaIds",java.util.List.of(mediaId)));
        assertEquals("",result.get("text"));assertEquals("EMPTY",result.get("textState"));assertEquals("VOICE",result.get("kind"));
        verify(media).bind(identity,"99",1,java.util.List.of(mediaId));verify(core).create(eq(identity),argThat(request->request.getContent().isEmpty()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_capsule_consent",Integer.class));
    }

}
