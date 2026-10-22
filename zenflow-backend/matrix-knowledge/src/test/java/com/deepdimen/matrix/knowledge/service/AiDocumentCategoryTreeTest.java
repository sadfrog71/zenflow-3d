package com.deepdimen.matrix.knowledge.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.knowledge.entity.AiDocumentCategoryEntity;

class AiDocumentCategoryTreeTest {

	@Test
	void descendantsIncludeSelfAndAllLevels() {
		assertEquals(List.of(1L, 2L, 3L),
				AiDocumentCategoryTree.descendants(List.of(node(1, 0), node(2, 1), node(3, 2)), 1L));
	}

	@Test
	void rejectsCyclesAndMovingSubtreeBeyondDepthLimit() {
		var nodes = List.of(node(1, 0), node(2, 1), node(3, 2), node(4, 0));
		assertThrows(IllegalArgumentException.class, () -> AiDocumentCategoryTree.validateParent(nodes, 1L, 3L));
		assertThrows(IllegalArgumentException.class, () -> AiDocumentCategoryTree.validateParent(nodes, 1L, 4L));
		assertThrows(IllegalArgumentException.class, () -> AiDocumentCategoryTree.validateParent(nodes, null, 3L));
		assertDoesNotThrow(() -> AiDocumentCategoryTree.validateParent(nodes, 3L, 4L));
	}

	@Test
	void rejectsParentOutsideDataset() {
		assertThrows(IllegalArgumentException.class,
				() -> AiDocumentCategoryTree.validateParent(List.of(node(1, 0)), null, 99L));
	}

	private AiDocumentCategoryEntity node(long id, long parent) {
		var value = new AiDocumentCategoryEntity();
		value.setId(id);
		value.setParentId(parent);
		return value;
	}

}
