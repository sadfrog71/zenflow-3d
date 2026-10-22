package com.deepdimen.matrix.knowledge.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.deepdimen.matrix.knowledge.entity.AiDocumentCategoryEntity;

/** 不依赖数据库的分类树规则，输入必须已按租户与知识库隔离。 */
public final class AiDocumentCategoryTree {

	private AiDocumentCategoryTree() {
	}

	public static List<Long> descendants(List<AiDocumentCategoryEntity> nodes, Long id) {
		Map<Long, List<Long>> children = new HashMap<>();
		for (AiDocumentCategoryEntity node : nodes) {
			children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getId());
		}
		List<Long> result = new ArrayList<>();
		result.add(id);
		Set<Long> seen = new HashSet<>(result);
		for (int i = 0; i < result.size(); i++) {
			Long parent = result.get(i);
			for (Long childId : children.getOrDefault(parent, List.of())) {
				if (!seen.add(childId)) {
					throw new IllegalArgumentException("分类层级存在循环，请先修复分类数据");
				}
				result.add(childId);
			}
		}
		return result;
	}

	public static void validateParent(List<AiDocumentCategoryEntity> nodes, Long id, Long parentId) {
		Map<Long, Long> parents = new HashMap<>();
		for (AiDocumentCategoryEntity node : nodes) {
			parents.put(node.getId(), node.getParentId());
		}
		if (parentId == null || parentId < 0 || (parentId != 0 && !parents.containsKey(parentId))) {
			throw new IllegalArgumentException("上级分类不存在或不属于当前知识库");
		}
		Long candidateId = id == null ? -1L : id;
		parents.put(candidateId, parentId);
		// 检查整棵树，包含随本节点一起移动的后代。
		for (Long start : parents.keySet()) {
			Set<Long> seen = new HashSet<>();
			Long current = start;
			while (current != 0) {
				if (!seen.add(current)) {
					throw new IllegalArgumentException("分类不能移动到自身或下级分类");
				}
				if (seen.size() > 3) {
					throw new IllegalArgumentException("文档分类最多支持三级");
				}
				current = parents.get(current);
				if (current == null) {
					throw new IllegalArgumentException("上级分类不存在");
				}
			}
		}
	}

}
