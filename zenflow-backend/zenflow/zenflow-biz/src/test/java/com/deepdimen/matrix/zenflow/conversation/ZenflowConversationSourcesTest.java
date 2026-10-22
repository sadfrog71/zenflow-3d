package com.deepdimen.matrix.zenflow.conversation;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZenflowConversationSourcesTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ZenflowConversationSources sources=new ZenflowConversationSources(jdbc);
    @Test void absenceMeansNoPrivateSelection(){assertEquals(List.of(),sources.selections(null));assertEquals(List.of(),sources.selections(Map.of()));verifyZeroInteractions(jdbc);}
    @Test void acceptsOnlyExplicitTurnAndCanonicalizesOrder(){var value=sources.selections(Map.of("capsules",List.of(Map.of("id","2","revisionId","22","scope","TURN"),Map.of("id","1","revisionId","11","scope","TURN"))));assertEquals("1",value.get(0).get("id"));verifyZeroInteractions(jdbc);}
    @Test void callersCannotGrantArbitraryRunSessionOrPermanentPermission(){for(String scope:List.of("SESSION","PERSISTENT","ALL"))assertThrows(ResponseStatusException.class,()->sources.selections(Map.of("capsules",List.of(Map.of("id","1","revisionId","11","scope",scope)))));assertThrows(ResponseStatusException.class,()->sources.selections(Map.of("runId","invented")));assertThrows(ResponseStatusException.class,()->sources.selections(Map.of("capsules",List.of(Map.of("id","1","revisionId","11","scope","TURN","runId","invented")))));verifyZeroInteractions(jdbc);}
    @Test void duplicateOrOversizedSelectionsAreRejectedBeforeWriting(){var note=Map.of("id","1","revisionId","11","scope","TURN");assertThrows(ResponseStatusException.class,()->sources.selections(Map.of("capsules",List.of(note,note))));assertThrows(ResponseStatusException.class,()->sources.selections(Map.of("capsules",List.of(note,note,note,note))));verifyZeroInteractions(jdbc);}
}
