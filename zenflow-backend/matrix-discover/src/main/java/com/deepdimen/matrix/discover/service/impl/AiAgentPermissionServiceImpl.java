package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.discover.dto.AiAgentPermissionDTO;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAgentPermissionEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentPermissionMapper;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.vo.AiAgentPermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体权限服务实现
 *
 * @author matrix
 * @date 2025-03-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentPermissionServiceImpl extends ServiceImpl<AiAgentPermissionMapper, AiAgentPermissionEntity>
		implements AiAgentPermissionService {

	private final AiAgentMapper aiAgentMapper;

	@Override
	public AiAgentPermissionVO getPermission(Long agentId) {
		List<AiAgentPermissionEntity> list = list(
				Wrappers.<AiAgentPermissionEntity>lambdaQuery().eq(AiAgentPermissionEntity::getAgentId, agentId));
		AiAgentPermissionVO vo = new AiAgentPermissionVO();
		if (CollectionUtils.isEmpty(list)) {
			vo.setPermissionType("all");
			vo.setRoleIds(new ArrayList<>());
			vo.setUserIds(new ArrayList<>());
			vo.setDeptIds(new ArrayList<>());
			return vo;
		}
		boolean hasAll = list.stream().anyMatch(e -> "all".equals(e.getPermissionType()));
		if (hasAll) {
			vo.setPermissionType("all");
			vo.setRoleIds(new ArrayList<>());
			vo.setUserIds(new ArrayList<>());
			vo.setDeptIds(new ArrayList<>());
			return vo;
		}
		vo.setPermissionType("restricted");
		vo.setRoleIds(list.stream()
			.filter(e -> "role".equals(e.getPermissionType()) && e.getTargetId() != null)
			.map(AiAgentPermissionEntity::getTargetId)
			.collect(Collectors.toList()));
		vo.setUserIds(list.stream()
			.filter(e -> "user".equals(e.getPermissionType()) && e.getTargetId() != null)
			.map(AiAgentPermissionEntity::getTargetId)
			.collect(Collectors.toList()));
		vo.setDeptIds(list.stream()
			.filter(e -> "dept".equals(e.getPermissionType()) && e.getTargetId() != null)
			.map(AiAgentPermissionEntity::getTargetId)
			.collect(Collectors.toList()));
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void setPermission(Long agentId, AiAgentPermissionDTO dto) {
		if ("restricted".equals(dto.getPermissionType()) && CollectionUtils.isEmpty(dto.getRoleIds())
				&& CollectionUtils.isEmpty(dto.getUserIds()) && CollectionUtils.isEmpty(dto.getDeptIds())) {
			throw new IllegalArgumentException("restricted 模式下, 角色ID列表、用户ID列表和部门ID列表不能同时为空");
		}
		remove(Wrappers.<AiAgentPermissionEntity>lambdaQuery().eq(AiAgentPermissionEntity::getAgentId, agentId));
		if ("all".equals(dto.getPermissionType())) {
			AiAgentPermissionEntity entity = new AiAgentPermissionEntity();
			entity.setAgentId(agentId);
			entity.setPermissionType("all");
			save(entity);
		}
		else {
			List<AiAgentPermissionEntity> entities = new ArrayList<>();
			if (!CollectionUtils.isEmpty(dto.getRoleIds())) {
				for (Long roleId : dto.getRoleIds().stream().distinct().toList()) {
					AiAgentPermissionEntity entity = new AiAgentPermissionEntity();
					entity.setAgentId(agentId);
					entity.setPermissionType("role");
					entity.setTargetId(roleId);
					entities.add(entity);
				}
			}
			if (!CollectionUtils.isEmpty(dto.getUserIds())) {
				for (Long userId : dto.getUserIds().stream().distinct().toList()) {
					AiAgentPermissionEntity entity = new AiAgentPermissionEntity();
					entity.setAgentId(agentId);
					entity.setPermissionType("user");
					entity.setTargetId(userId);
					entities.add(entity);
				}
			}
			if (!CollectionUtils.isEmpty(dto.getDeptIds())) {
				for (Long deptId : dto.getDeptIds().stream().distinct().toList()) {
					AiAgentPermissionEntity entity = new AiAgentPermissionEntity();
					entity.setAgentId(agentId);
					entity.setPermissionType("dept");
					entity.setTargetId(deptId);
					entities.add(entity);
				}
			}
			saveBatch(entities);
		}
		// 联动更新主表 permission 字段，保证同一事务内一致
		AiAgentEntity agentUpdate = new AiAgentEntity();
		agentUpdate.setId(agentId);
		agentUpdate.setPermission("all".equals(dto.getPermissionType()) ? "public" : "restricted");
		aiAgentMapper.updateById(agentUpdate);
		log.info("设置智能体权限, agentId={}, permissionType={}", agentId, dto.getPermissionType());
	}

	@Override
	public void createDefaultPermission(Long agentId) {
		AiAgentPermissionEntity entity = new AiAgentPermissionEntity();
		entity.setAgentId(agentId);
		entity.setPermissionType("all");
		save(entity);
	}

}
