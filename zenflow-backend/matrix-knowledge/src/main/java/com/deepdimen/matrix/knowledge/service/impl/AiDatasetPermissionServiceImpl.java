package com.deepdimen.matrix.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.dto.AiDatasetPermissionDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetPermissionEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetPermissionMapper;
import com.deepdimen.matrix.knowledge.service.AiDatasetPermissionService;
import com.deepdimen.matrix.knowledge.vo.AiDatasetPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库权限服务实现
 *
 * @author matrix
 * @date 2025-03-12
 */
@Slf4j
@Service
public class AiDatasetPermissionServiceImpl extends ServiceImpl<AiDatasetPermissionMapper, AiDatasetPermissionEntity>
		implements AiDatasetPermissionService {

	@Override
	public AiDatasetPermissionVO getPermission(Long datasetId) {
		List<AiDatasetPermissionEntity> list = list(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
			.eq(AiDatasetPermissionEntity::getDatasetId, datasetId));
		AiDatasetPermissionVO vo = new AiDatasetPermissionVO();
		if (CollectionUtils.isEmpty(list)) {
			vo.setPermissionType("all");
			vo.setTargetIds(new ArrayList<>());
			return vo;
		}
		AiDatasetPermissionEntity first = list.get(0);
		vo.setPermissionType(first.getPermissionType());
		vo.setTargetIds(list.stream()
			.filter(e -> e.getTargetId() != null && first.getPermissionType().equals(e.getPermissionType()))
			.map(AiDatasetPermissionEntity::getTargetId)
			.collect(Collectors.toList()));
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void setPermission(Long datasetId, AiDatasetPermissionDTO dto) {
		if (!"all".equals(dto.getPermissionType()) && CollectionUtils.isEmpty(dto.getTargetIds())) {
			throw new IllegalArgumentException("权限类型为 " + dto.getPermissionType() + " 时，目标ID列表不能为空");
		}
		// 先删除旧权限
		remove(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
			.eq(AiDatasetPermissionEntity::getDatasetId, datasetId));
		// 再插入新权限
		if ("all".equals(dto.getPermissionType())) {
			AiDatasetPermissionEntity entity = new AiDatasetPermissionEntity();
			entity.setDatasetId(datasetId);
			entity.setPermissionType("all");
			save(entity);
		}
		else {
			List<AiDatasetPermissionEntity> entities = dto.getTargetIds().stream().map(targetId -> {
				AiDatasetPermissionEntity entity = new AiDatasetPermissionEntity();
				entity.setDatasetId(datasetId);
				entity.setPermissionType(dto.getPermissionType());
				entity.setTargetId(targetId);
				return entity;
			}).collect(Collectors.toList());
			saveBatch(entities);
		}
		log.info("设置知识库权限, datasetId={}, permissionType={}", datasetId, dto.getPermissionType());
	}

	@Override
	public void createDefaultPermission(Long datasetId) {
		long count = count(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
			.eq(AiDatasetPermissionEntity::getDatasetId, datasetId));
		if (count > 0) {
			return;
		}
		AiDatasetPermissionEntity entity = new AiDatasetPermissionEntity();
		entity.setDatasetId(datasetId);
		entity.setPermissionType("all");
		save(entity);
	}

}
