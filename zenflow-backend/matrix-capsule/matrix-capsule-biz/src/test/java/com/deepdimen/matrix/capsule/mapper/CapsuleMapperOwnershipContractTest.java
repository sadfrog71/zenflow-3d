package com.deepdimen.matrix.capsule.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CapsuleMapperOwnershipContractTest {

    @Test
    void allReadPathsContainExplicitTenantAndOwnerPredicates() throws IOException {
        String xml;
        try (var stream = getClass().getResourceAsStream("/mapper/CapsuleMapper.xml")) {
            if (stream == null) {
                throw new IOException("CapsuleMapper.xml not found");
            }
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertOwned(xml, "selectOwnedPage");
        assertOwned(xml, "selectOwnedDetail");
        assertOwned(xml, "selectOwnedForContext");
        assertOwned(xml, "countOwnedExplicitIds");
    }

    private void assertOwned(String xml, String statementId) {
        int start = xml.indexOf("id=\"" + statementId + "\"");
        int end = xml.indexOf("</select>", start);
        String statement = xml.substring(start, end);
        assertTrue(statement.contains("tenant_id = #{tenantId}"), statementId);
        assertTrue(statement.contains("owner_user_id = #{ownerUserId}"), statementId);
    }
}
