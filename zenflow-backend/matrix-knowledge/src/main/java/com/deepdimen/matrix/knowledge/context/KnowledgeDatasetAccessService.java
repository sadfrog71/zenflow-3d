package com.deepdimen.matrix.knowledge.context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetPermissionEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetPermissionMapper;

@Service
public class KnowledgeDatasetAccessService {

	private final AiDatasetMapper datasetMapper;

	private final AiDatasetPermissionMapper permissionMapper;

	public KnowledgeDatasetAccessService(AiDatasetMapper datasetMapper, AiDatasetPermissionMapper permissionMapper) {
		this.datasetMapper = datasetMapper;
		this.permissionMapper = permissionMapper;
	}

	public List<AiDatasetEntity> accessibleDatasets(IdentityEnvelope identity, List<Long> requestedIds) {
		long userId = Long.parseLong(identity.userId());
		String tenantId = identity.tenantId();
		List<Long> roleIds = identity.roleIds().stream().map(Long::valueOf).toList();
		Set<Long> accessibleIds = new HashSet<>();
		accessibleIds.addAll(datasetMapper
				.selectList(Wrappers.<AiDatasetEntity>lambdaQuery().eq(AiDatasetEntity::getTenantId, tenantId)
						.eq(AiDatasetEntity::getVisibility, "public"))
				.stream().map(AiDatasetEntity::getId).toList());
		accessibleIds.addAll(permissionMapper.selectList(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
				.eq(AiDatasetPermissionEntity::getTenantId, tenantId)
				.eq(AiDatasetPermissionEntity::getPermissionType, "all")).stream()
				.map(AiDatasetPermissionEntity::getDatasetId).toList());
		accessibleIds.addAll(permissionMapper.selectList(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
				.eq(AiDatasetPermissionEntity::getTenantId, tenantId)
				.eq(AiDatasetPermissionEntity::getPermissionType, "user")
				.eq(AiDatasetPermissionEntity::getTargetId, userId)).stream()
				.map(AiDatasetPermissionEntity::getDatasetId).toList());
		if (!roleIds.isEmpty()) {
			accessibleIds.addAll(permissionMapper.selectList(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
					.eq(AiDatasetPermissionEntity::getTenantId, tenantId)
					.eq(AiDatasetPermissionEntity::getPermissionType, "role")
					.in(AiDatasetPermissionEntity::getTargetId, roleIds)).stream()
					.map(AiDatasetPermissionEntity::getDatasetId).toList());
		}
		if (!requestedIds.isEmpty() && !accessibleIds.containsAll(requestedIds)) {
			return List.of();
		}
		Set<Long> selectedIds = requestedIds.isEmpty() ? accessibleIds : Set.copyOf(requestedIds);
		if (selectedIds.isEmpty()) {
			return List.of();
		}
		return datasetMapper.selectList(Wrappers.<AiDatasetEntity>lambdaQuery()
				.eq(AiDatasetEntity::getTenantId, tenantId).in(AiDatasetEntity::getId, selectedIds)
				.orderByDesc(AiDatasetEntity::getSortOrder));
	}

	public boolean canAccess(IdentityEnvelope identity, long datasetId) {
		return accessibleDatasets(identity, List.of(datasetId)).stream().anyMatch(dataset -> dataset.getId() == datasetId);
	}

}
