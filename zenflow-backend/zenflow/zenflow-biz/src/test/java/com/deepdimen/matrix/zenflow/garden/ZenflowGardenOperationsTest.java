package com.deepdimen.matrix.zenflow.garden;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZenflowGardenOperationsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ZenflowGardenOperations.Catalogue catalogue = new ZenflowGardenOperations.Catalogue(
        Set.of("koi"), Map.of("stone", new ZenflowGardenOperations.Area(Set.of("pond.edge"), 2, 0.5, 2)), Set.of("day", "rain"), 2);
    private ObjectNode state() throws Exception {
        return (ObjectNode) json.readTree("""
            {"fish":[{"instanceId":"fish-1","archetypeId":"future-fish","name":"old"}],
             "placements":[{"instanceId":"future-object","archetypeId":"future-object","extra":"keep"}],
             "environment":{"presetId":"future-weather","lampEnabled":true,"futureSetting":7}}
            """);
    }
    @Test void fishEditPreservesUnmentionedEnvironmentAndUnknownAssets() throws Exception {
        var before = state();
        var after = ZenflowGardenOperations.apply(before, json.readTree("""
            [{"type":"SET_FISH_NAME","instanceId":"fish-1","name":"小白"}]
            """), catalogue);
        assertEquals("小白", after.at("/fish/0/name").textValue());
        assertEquals(before.get("environment"), after.get("environment"));
        assertEquals(before.get("placements"), after.get("placements"));
        assertEquals("future-fish", after.at("/fish/0/archetypeId").textValue());
        assertEquals("old", before.at("/fish/0/name").textValue());
    }
    @Test void invalidSecondOperationCannotPartiallyMutateStoredState() throws Exception {
        var before = state(); var original = before.deepCopy();
        assertThrows(IllegalArgumentException.class, () -> ZenflowGardenOperations.apply(before, json.readTree("""
            [{"type":"SET_FISH_NAME","instanceId":"fish-1","name":"new"},
             {"type":"SET_ENVIRONMENT","presetId":"unapproved"}]
            """), catalogue));
        assertEquals(original, before);
    }
    @Test void environmentPatchOnlyUpdatesExplicitFields() throws Exception {
        var after = ZenflowGardenOperations.apply(state(), json.readTree("""
            [{"type":"SET_ENVIRONMENT","lampEnabled":false}]
            """), catalogue);
        assertEquals("future-weather", after.at("/environment/presetId").textValue());
        assertFalse(after.at("/environment/lampEnabled").booleanValue());
        assertEquals(7, after.at("/environment/futureSetting").intValue());
    }
    @Test void rejectsPayloadOwnershipUnknownFieldsAndMissingInstances() throws Exception {
        for (String operation : new String[]{
            "{\"type\":\"SET_ENVIRONMENT\",\"lampEnabled\":true,\"ownerUserId\":42}",
            "{\"type\":\"SET_ENVIRONMENT\",\"lampEnabled\":\"true\"}",
            "{\"type\":\"SET_FISH_NAME\",\"instanceId\":\"other-owner-fish\",\"name\":\"new\"}",
            "{\"type\":\"INIT\"}"}) {
            assertThrows(IllegalArgumentException.class, () -> ZenflowGardenOperations.apply(state(), json.readTree("[" + operation + "]"), catalogue));
        }
    }
    @Test void placementUsesServerCatalogueAndRejectsUnsafeTransforms() throws Exception {
        String template = """
            [{"type":"UPSERT_PLACEMENT","placement":{"instanceId":"p1","archetypeId":"stone","anchorId":"pond.edge",
             "transform":{"position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}}}]
            """;
        var result = ZenflowGardenOperations.apply(state(), json.readTree(template), catalogue);
        assertEquals(2, result.get("placements").size());
        for (String invalid : new String[]{template.replace("pond.edge", "other.anchor"), template.replace("[0,0,0]", "[2000,0,0]"),
                template.replace("[1,1,1]", "[0,1,1]"), template.replace("stone", "arbitrary-url.glb")}) {
            assertThrows(IllegalArgumentException.class, () -> ZenflowGardenOperations.apply(state(), json.readTree(invalid), catalogue));
        }
        assertThrows(IllegalArgumentException.class, () -> ZenflowGardenOperations.apply(result, json.readTree(template.replace("p1", "p2")), catalogue));
    }
}
