package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.*;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ZenflowSessionSubjectTest {
    private IdentityEnvelope identity(String type){return new IdentityEnvelope("1","42",null,List.of(),List.of(),List.of(),"test","TEXT_CONVERSATION",type);}
    @Test void numericIdCollisionCannotReadListOrMutateOtherSubject() {
        var repository=new InMemoryAssistantSessionRepository();
        var service=new AssistantSessionService(repository,null,null);
        var app=identity("APP_USER");var admin=identity("ADMIN_USER");
        var owned=service.create(app,new CreateSessionRequest("Private",List.of(),null));
        var administrative=service.create(admin,new CreateSessionRequest("Admin preview",List.of(),null));
        assertEquals("APP_USER",owned.ownerSubjectType());
        assertThrows(ResponseStatusException.class,()->service.get(admin,owned.assistantSessionId()));
        assertThrows(ResponseStatusException.class,()->service.get(app,administrative.assistantSessionId()));
        assertThrows(ResponseStatusException.class,()->service.updateTitle(admin,owned.assistantSessionId(),new UpdateSessionTitleRequest("wrong",1)));
        var updated=service.updateTitle(app,owned.assistantSessionId(),new UpdateSessionTitleRequest("Renamed",1));
        assertEquals("APP_USER",updated.ownerSubjectType());
        repository.nextSequence(owned.assistantSessionId());repository.nextSequence(administrative.assistantSessionId());
        repository.recordRunStarted(owned.assistantSessionId(),"1","42","Private",List.of(),null,null,Instant.now(),"APP_USER");
        repository.recordRunStarted(administrative.assistantSessionId(),"1","42","Admin",List.of(),null,null,Instant.now(),"ADMIN_USER");
        assertEquals(1,service.listSessions(app,SessionStatus.ACTIVE,null,null,20).items().size());
        assertEquals(owned.assistantSessionId(),service.listSessions(app,SessionStatus.ACTIVE,null,null,20).items().get(0).assistantSessionId());
        assertTrue(repository.updateTitle(owned.assistantSessionId(),"1","42","legacy overwrite",3).isEmpty());
    }
    @Test void legacyIdentityIsExplicitlyAdministrativeAndJsonPreservesConsumerType() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var legacy=new IdentityEnvelope("1","42",null,List.of(),List.of(),List.of(),"test","legacy");
        assertEquals("ADMIN_USER",legacy.subjectType());
        assertEquals("APP_USER",mapper.readValue(mapper.writeValueAsString(identity("APP_USER")),IdentityEnvelope.class).subjectType());
        assertEquals("ADMIN_USER",mapper.readValue("{\"tenantId\":\"1\",\"userId\":\"42\"}",IdentityEnvelope.class).subjectType());
    }
}
