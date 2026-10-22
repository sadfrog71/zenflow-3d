package com.deepdimen.matrix.zenflow.garden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure transactional preparation: never mutates the persisted snapshot supplied by the caller. */
public final class ZenflowGardenOperations {
    private ZenflowGardenOperations() {}

    /** The server's versioned asset catalogue supplies allowed local coordinates, not the request. */
    public record Area(Set<String> anchors, double maxPosition, double minScale, double maxScale) {
        public Area {
            anchors = Set.copyOf(anchors);
            if (anchors.isEmpty() || !Double.isFinite(maxPosition) || maxPosition <= 0
                    || !Double.isFinite(minScale) || !Double.isFinite(maxScale)
                    || minScale <= 0 || maxScale < minScale) throw invalid();
        }
    }
    public record Catalogue(Set<String> fishTypes, Map<String,Area> placements, Set<String> presets, int maxPlacements) {
        public Catalogue {
            fishTypes = Set.copyOf(fishTypes); placements = Map.copyOf(placements); presets = Set.copyOf(presets);
            if (fishTypes.isEmpty() || presets.isEmpty() || maxPlacements < 0 || maxPlacements > 100) throw invalid();
        }
    }

    public static ObjectNode apply(ObjectNode stored, JsonNode operations, Catalogue catalogue) {
        if (operations == null || !operations.isArray() || operations.isEmpty() || operations.size() > 32) throw invalid();
        ObjectNode next = stored.deepCopy();
        if (!next.path("fish").isArray() || !next.path("placements").isArray() || !next.path("environment").isObject()) throw new IllegalStateException("GARDEN_STATE_INVALID");
        for (JsonNode op : operations) {
            if (!op.isObject()) throw invalid();
            String type = text(op, "type", 40);
            switch (type) {
                case "SET_FISH_NAME" -> {
                    fields(op, "type", "instanceId", "name");
                    ObjectNode fish = instance(next.path("fish"), text(op, "instanceId", 128));
                    String name = text(op, "name", 40);
                    fish.put("name", name);
                }
                case "SET_FISH_ARCHETYPE" -> {
                    fields(op, "type", "instanceId", "archetypeId");
                    String archetype = text(op, "archetypeId", 128);
                    if (!catalogue.fishTypes().contains(archetype)) throw invalid();
                    instance(next.path("fish"), text(op, "instanceId", 128)).put("archetypeId", archetype);
                }
                case "SET_ENVIRONMENT" -> {
                    fields(op, "type", "presetId", "lampEnabled");
                    if (!op.has("presetId") && !op.has("lampEnabled")) throw invalid();
                    ObjectNode environment = (ObjectNode) next.path("environment");
                    if (op.has("presetId")) {
                        String preset = text(op, "presetId", 64);
                        if (!catalogue.presets().contains(preset)) throw invalid();
                        environment.put("presetId", preset);
                    }
                    if (op.has("lampEnabled")) {
                        if (!op.get("lampEnabled").isBoolean()) throw invalid();
                        environment.put("lampEnabled", op.get("lampEnabled").booleanValue());
                    }
                }
                case "UPSERT_PLACEMENT" -> {
                    fields(op, "type", "placement");
                    JsonNode placement = op.path("placement");
                    fields(placement, "instanceId", "archetypeId", "anchorId", "transform");
                    String id = text(placement, "instanceId", 128);
                    Area area = catalogue.placements().get(text(placement, "archetypeId", 128));
                    if (area == null || !area.anchors().contains(text(placement, "anchorId", 128))) throw invalid();
                    JsonNode transform = placement.path("transform");
                    fields(transform, "position", "rotation", "scale");
                    vector(transform.path("position"), -area.maxPosition(), area.maxPosition());
                    vector(transform.path("rotation"), -Math.PI * 2, Math.PI * 2);
                    vector(transform.path("scale"), area.minScale(), area.maxScale());
                    var list = (com.fasterxml.jackson.databind.node.ArrayNode) next.path("placements");
                    int index = index(list, id);
                    if (index >= 0) list.set(index, placement.deepCopy());
                    else {
                        if (list.size() >= catalogue.maxPlacements()) throw invalid();
                        list.add(placement.deepCopy());
                    }
                }
                case "REMOVE_PLACEMENT" -> {
                    fields(op, "type", "instanceId");
                    var list = (com.fasterxml.jackson.databind.node.ArrayNode) next.path("placements");
                    int index = index(list, text(op, "instanceId", 128));
                    if (index < 0) throw new IllegalArgumentException("GARDEN_INSTANCE_NOT_FOUND");
                    list.remove(index);
                }
                default -> throw invalid(); // INIT is only valid in the separate create-if-absent transaction.
            }
        }
        return next;
    }

    private static ObjectNode instance(JsonNode values, String id) {
        int index = index(values, id);
        if (index < 0 || !values.get(index).isObject()) throw new IllegalArgumentException("GARDEN_INSTANCE_NOT_FOUND");
        return (ObjectNode) values.get(index);
    }
    private static int index(JsonNode values, String id) {
        for (int i = 0; i < values.size(); i++) if (id.equals(values.get(i).path("instanceId").asText())) return i;
        return -1;
    }
    private static String text(JsonNode node, String key, int max) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) throw invalid();
        String text = value.textValue();
        if (text.isBlank() || text.codePointCount(0, text.length()) > max
                || text.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return text;
    }
    private static void fields(JsonNode node, String... allowed) {
        if (!node.isObject()) throw invalid();
        Set<String> names = new HashSet<>(Set.of(allowed));
        node.fieldNames().forEachRemaining(key -> { if (!names.contains(key)) throw invalid(); });
    }
    private static void vector(JsonNode node, double min, double max) {
        if (!node.isArray() || node.size() != 3) throw invalid();
        for (JsonNode value : node) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue() < min || value.doubleValue() > max) throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_GARDEN_OPERATION"); }
}
