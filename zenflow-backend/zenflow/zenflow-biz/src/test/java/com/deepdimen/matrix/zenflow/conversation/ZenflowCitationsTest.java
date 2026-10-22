package com.deepdimen.matrix.zenflow.conversation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ZenflowCitationsTest {
    @Test void onlyActualExactAvailableMarkersCount() {
        assertEquals(Set.of("PRIVATE_abc"),ZenflowCitations.referenced("正文 [PRIVATE_abc] [PRIVATE_abc] [UNKNOWN] PRIVATE_uncited [PRIVATE_abc_extra]",List.of("PRIVATE_abc","PRIVATE_uncited")));
        assertTrue(ZenflowCitations.referenced("回答没有引用",List.of("PRIVATE_abc")).isEmpty());
    }
    @Test void onlyUniqueExactExplicitPublicQuotesAreRecovered() {
        var sources = Map.of("REFERENCE_a", "【原文】平常心是道。", "REFERENCE_b", "觉察身体的感受。");
        assertEquals(Set.of("REFERENCE_a"), ZenflowCitations.exactPublicQuotes("解释\n> 平常心是道。\n后文", sources));
        assertTrue(ZenflowCitations.exactPublicQuotes("平常心是道。\n> 平常心就是道。\n> 心是道。", sources).isEmpty());
        assertTrue(ZenflowCitations.exactPublicQuotes("> 平常心是道。 [UNKNOWN]", sources).isEmpty());
        assertTrue(ZenflowCitations.exactPublicQuotes("> 平常心是道。", Map.of("REFERENCE_a", "平常心是道。", "REFERENCE_b", "平常心是道。")).isEmpty());
        assertTrue(ZenflowCitations.exactPublicQuotes("> 平常心是道。", Map.of()).isEmpty());
    }
}
