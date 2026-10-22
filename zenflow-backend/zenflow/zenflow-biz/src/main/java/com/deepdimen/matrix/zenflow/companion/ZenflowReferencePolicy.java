package com.deepdimen.matrix.zenflow.companion;

import java.util.*;

/** Draft selections refer to existing knowledge documents; callers cannot supply source bodies. */
public record ZenflowReferencePolicy(List<Reference> references, int maxFragments, double minScore) {
    public record Reference(String documentId, String kind, String locale, String citationVisibility, String mode) {
        public Reference(String documentId,String kind,String locale,String visibility){this(documentId,kind,locale,visibility,null);}
        public String effectiveMode(){return mode==null?"RETRIEVAL":mode;}
        public Map<String,Object> projection(){var value=new LinkedHashMap<String,Object>();value.put("documentId",documentId);value.put("kind",kind);value.put("locale",locale);value.put("citationVisibility",citationVisibility);if(mode!=null)value.put("mode",mode);return value;}
    }
    public boolean hasRetrieval(){return references.stream().anyMatch(ref->"RETRIEVAL".equals(ref.effectiveMode()));}
    public ZenflowReferencePolicy {
        references = List.copyOf(references);
        if (references.size() > 20 || maxFragments < 0 || maxFragments > 6 || !Double.isFinite(minScore)
                || minScore < 0 || minScore > 1 || (references.stream().anyMatch(ref->"RETRIEVAL".equals(ref.effectiveMode())) && maxFragments == 0))
            throw new IllegalArgumentException("REFERENCE_POLICY_INVALID");
        var ids = new HashSet<String>();
        for (var ref : references) {
            if (ref == null || ref.documentId() == null || !ref.documentId().matches("[1-9][0-9]{0,18}"))
                throw new IllegalArgumentException("REFERENCE_DOCUMENT_INVALID");
            try { Long.parseLong(ref.documentId()); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("REFERENCE_DOCUMENT_INVALID"); }
            if (!ids.add(ref.documentId()))throw new IllegalArgumentException("REFERENCE_SELECTION_INVALID");
            requireMetadata(ref.kind(),ref.locale(),ref.citationVisibility());
            if(!Set.of("FIXED","RETRIEVAL").contains(ref.effectiveMode()))throw new IllegalArgumentException("REFERENCE_MODE_INVALID");
        }
    }
    public static void requireMetadata(String kind,String locale,String citationVisibility) {
        if(kind==null || locale==null || citationVisibility==null || !Set.of("MATERIAL","EXAMPLE").contains(kind)
                || !Set.of("zh-CN","en").contains(locale) || !Set.of("PUBLIC","TITLE_ONLY","INTERNAL").contains(citationVisibility))
            throw new IllegalArgumentException("REFERENCE_SELECTION_INVALID");
    }
    public static ZenflowReferencePolicy empty() { return new ZenflowReferencePolicy(List.of(), 0, 0.7); }
    public static ZenflowReferencePolicy parse(Map<String,Object> input) {
        if (input == null || !Set.of("references", "maxFragments", "minScore").containsAll(input.keySet()))
            throw new IllegalArgumentException("REFERENCE_POLICY_INVALID");
        if (!(input.get("references") instanceof List<?> raw) || raw.size() > 20)
            throw new IllegalArgumentException("REFERENCE_POLICY_INVALID");
        var refs = new ArrayList<Reference>();
        for (var value : raw) {
            if (!(value instanceof Map<?,?> row) || !Set.of("documentId", "kind", "locale", "citationVisibility", "mode").containsAll(row.keySet()) || !row.keySet().containsAll(Set.of("documentId","kind","locale","citationVisibility"))
                    || row.values().stream().anyMatch(v -> !(v instanceof String)))
                throw new IllegalArgumentException("REFERENCE_SELECTION_INVALID");
            refs.add(new Reference((String)row.get("documentId"), (String)row.get("kind"),
                    (String)row.get("locale"), (String)row.get("citationVisibility"), (String)row.get("mode")));
        }
        Object count = input.getOrDefault("maxFragments", refs.stream().anyMatch(ref->"RETRIEVAL".equals(ref.effectiveMode())) ? 6 : 0);
        Object score = input.getOrDefault("minScore", 0.7);
        if (!(count instanceof Number n) || n.doubleValue() != n.intValue() || !(score instanceof Number s))
            throw new IllegalArgumentException("REFERENCE_POLICY_INVALID");
        return new ZenflowReferencePolicy(refs, n.intValue(), s.doubleValue());
    }
    public Map<String,Object> projection() {
        return Map.of("references", references.stream().map(Reference::projection).toList(),
                "maxFragments", maxFragments, "minScore", minScore);
    }
}
