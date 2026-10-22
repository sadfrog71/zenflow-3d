package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ZenflowPublicationChangesTest {

	@Test
	void auditContainsOnlyChangedFieldLabelsNotAuthoredTextOrExtraSecrets() {
		var before = new HashMap<String, Object>(Map.of("principles", "old private-looking fixture", "modelId", "100",
				"apiKey", "never-serialize-this"));
		var after = new HashMap<String, Object>(
				Map.of("principles", "new private-looking fixture", "modelId", "200", "apiKey", "another-secret"));
		assertEquals(List.of("陪伴原则", "固定模型"), ZenflowCompanionService.publicationChanges(before, after));
	}

	@Test
	void referenceMetadataAndOrderAreComparedWithoutSerializingMapKeyOrder() {
		var original = new LinkedHashMap<String, Object>();
		original.put("documentId", "300");
		original.put("citationVisibility", "PUBLIC");
		var reordered = new LinkedHashMap<String, Object>();
		reordered.put("citationVisibility", "PUBLIC");
		reordered.put("documentId", "300");
		var before = Map.<String, Object>of("retrievalPolicy", Map.of("references", List.of(original)));
		var after = Map.<String, Object>of("retrievalPolicy", Map.of("references", List.of(reordered)));
		assertEquals(List.of("配置内容一致"), ZenflowCompanionService.publicationChanges(before, after));
		reordered.put("citationVisibility", "TITLE_ONLY");
		assertEquals(List.of("参考内容与检索范围"), ZenflowCompanionService.publicationChanges(before, after));
	}

	@Test
	void exampleChangesAreIncludedEvenWhenPrinciplesStayTheSame() {
		assertEquals(List.of("回答示例"),
				ZenflowCompanionService.publicationChanges(
						Map.of("examples", List.of(Map.of("user", "a", "assistant", "b"))),
						Map.of("examples", List.of(Map.of("user", "a", "assistant", "c")))));
	}

}
