package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ZenflowReferencePolicyTest {
    private Map<String,Object> selection(String id) {
        return Map.of("documentId",id,"kind","MATERIAL","locale","en","citationVisibility","INTERNAL");
    }
    @Test void boundedPolicyRoundTripsAndKeepsExplicitInternalSelection() {
        var policy=ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("123"))));
        assertEquals(6,policy.maxFragments());
        assertEquals("INTERNAL",policy.references().get(0).citationVisibility());
        assertEquals(policy,ZenflowReferencePolicy.parse(policy.projection()));
        assertEquals(ZenflowReferencePolicy.empty(),ZenflowReferencePolicy.parse(Map.of("references",List.of())));
    }
    @Test void rejectsBodiesForeignScopesDuplicatesAndOverflowIdentifiers() {
        var unsafe=new HashMap<>(selection("123"));unsafe.put("content","private user data");
        assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",List.of(unsafe))));
        assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("123")),"tenantId","foreign")));
        assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("123"),selection("123")))));
        assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("9999999999999999999")))));
    }
    @Test void directlyConstructedPolicyAlsoRejectsNullMetadata() {
        for(var ref:List.of(new ZenflowReferencePolicy.Reference("123",null,"en","PUBLIC"),
                new ZenflowReferencePolicy.Reference("123","MATERIAL",null,"PUBLIC"),
                new ZenflowReferencePolicy.Reference("123","MATERIAL","en",null)))
            assertThrows(IllegalArgumentException.class,()->new ZenflowReferencePolicy(List.of(ref),6,0.7));
    }
    @Test void rejectsUnboundedOrDisabledRetrievalForNonemptySelections() {
        for(var count:List.of(-1,0,7,1.5,Double.NaN,Double.POSITIVE_INFINITY))
            assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("123")),"maxFragments",count)));
        for(var score:List.of(-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY))
            assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("123")),"minScore",score)));
        var excessive=new ArrayList<Map<String,Object>>();for(int i=1;i<=21;i++)excessive.add(selection(Integer.toString(i)));
        assertThrows(IllegalArgumentException.class,()->ZenflowReferencePolicy.parse(Map.of("references",excessive)));
    }
    @Test void fixedReferencesRoundTripWithoutRequiringRetrieval() {
        var ref=new HashMap<String,Object>(selection("123"));ref.put("mode","FIXED");
        var policy=ZenflowReferencePolicy.parse(Map.of("references",List.of(ref),"maxFragments",0));
        assertFalse(policy.hasRetrieval());
        assertEquals("FIXED",policy.references().get(0).effectiveMode());
        assertEquals(policy,ZenflowReferencePolicy.parse(policy.projection()));
        assertFalse(ZenflowReferencePolicy.parse(Map.of("references",List.of(selection("123")))).projection().toString().contains("mode"));
    }
}
