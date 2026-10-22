package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.discover.dto.AiAgentVersionGrayDTO;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAgentVersionEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentVersionMapper;
import com.deepdimen.matrix.discover.service.AiAgentVersionService;
import com.deepdimen.matrix.discover.vo.AiAgentVersionVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体版本服务实现
 *
 * @author matrix
 * @date 2025-03-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentVersionServiceImpl extends ServiceImpl<AiAgentVersionMapper, AiAgentVersionEntity>
		implements AiAgentVersionService {

	private final ObjectMapper objectMapper;

	private final AiAgentMapper aiAgentMapper;

	@Override
	public List<AiAgentVersionVO> listVersions(Long agentId) {
		List<AiAgentVersionEntity> list = list(Wrappers.<AiAgentVersionEntity>lambdaQuery()
			.eq(AiAgentVersionEntity::getAgentId, agentId)
			.orderByDesc(AiAgentVersionEntity::getCreateTime));
		return list.stream().map(entity -> {
			AiAgentVersionVO vo = new AiAgentVersionVO();
			BeanUtils.copyProperties(entity, vo);
			// 列表不返回大字段config
			vo.setConfig(null);
			return vo;
		}).collect(Collectors.toList());
	}

	@Override
	public Map<String, String> compareVersions(Long agentId, String v1, String v2) {
		List<AiAgentVersionEntity> list = list(Wrappers.<AiAgentVersionEntity>lambdaQuery()
			.eq(AiAgentVersionEntity::getAgentId, agentId)
			.in(AiAgentVersionEntity::getVersion, v1, v2));
		Map<String, String> result = new HashMap<>(2);
		list.forEach(e -> result.put(e.getVersion(), e.getConfig()));
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void rollback(Long agentId, Long versionId) {
		AiAgentVersionEntity version = getById(versionId);
		if (version == null || !version.getAgentId().equals(agentId)) {
			throw new IllegalArgumentException("版本不存在: " + versionId);
		}
		// 将所有版本设为inactive
		update(Wrappers.<AiAgentVersionEntity>lambdaUpdate()
			.eq(AiAgentVersionEntity::getAgentId, agentId)
			.set(AiAgentVersionEntity::getStatus, "inactive"));
		// 将目标版本设为active
		AiAgentVersionEntity update = new AiAgentVersionEntity();
		update.setId(versionId);
		update.setStatus("active");
		updateById(update);
		// 将版本config写回ai_agent，使智能体立即生效
		AiAgentEntity agentUpdate = new AiAgentEntity();
		agentUpdate.setId(agentId);
		agentUpdate.setConfig(version.getConfig());
		aiAgentMapper.updateById(agentUpdate);
		log.info("智能体版本回滚, agentId={}, versionId={}, version={}", agentId, versionId, version.getVersion());
	}

	@Override
	public void setGrayConfig(Long agentId, Long versionId, AiAgentVersionGrayDTO dto) {
		AiAgentVersionEntity version = getById(versionId);
		if (version == null || !version.getAgentId().equals(agentId)) {
			throw new IllegalArgumentException("版本不存在: " + versionId);
		}
		try {
			String grayConfigJson = objectMapper.writeValueAsString(dto);
			AiAgentVersionEntity update = new AiAgentVersionEntity();
			update.setId(versionId);
			update.setGrayConfig(grayConfigJson);
			updateById(update);
			log.info("设置灰度配置, agentId={}, versionId={}", agentId, versionId);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("灰度配置序列化失败", e);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createSnapshot(Long agentId, String config, String remark) {
		// 取最大版本号，避免并发时count()因已删除记录产生重复版本号
		AiAgentVersionEntity latest = getOne(Wrappers.<AiAgentVersionEntity>lambdaQuery()
			.eq(AiAgentVersionEntity::getAgentId, agentId)
			.orderByDesc(AiAgentVersionEntity::getId)
			.last("LIMIT 1"));
		int nextNum = (latest == null) ? 1 : parseVersionNum(latest.getVersion()) + 1;
		String nextVersion = "v" + nextNum;
		// 将旧active版本设为inactive
		update(Wrappers.<AiAgentVersionEntity>lambdaUpdate()
			.eq(AiAgentVersionEntity::getAgentId, agentId)
			.eq(AiAgentVersionEntity::getStatus, "active")
			.set(AiAgentVersionEntity::getStatus, "inactive"));
		// 创建新版本快照
		AiAgentVersionEntity entity = new AiAgentVersionEntity();
		entity.setAgentId(agentId);
		entity.setVersion(nextVersion);
		entity.setConfig(config);
		entity.setStatus("active");
		entity.setRemark(remark);
		save(entity);
		log.info("创建版本快照, agentId={}, version={}", agentId, nextVersion);
	}

	@Override
	public String resolveGrayConfig(Long agentId, Long userId) {
		// 只查找active状态且有灰度配置的版本
		List<AiAgentVersionEntity> grayVersions = list(Wrappers.<AiAgentVersionEntity>lambdaQuery()
			.eq(AiAgentVersionEntity::getAgentId, agentId)
			.eq(AiAgentVersionEntity::getStatus, "active")
			.isNotNull(AiAgentVersionEntity::getGrayConfig)
			.ne(AiAgentVersionEntity::getGrayConfig, ""));
		for (AiAgentVersionEntity version : grayVersions) {
			try {
				AiAgentVersionGrayDTO grayDTO = objectMapper.readValue(version.getGrayConfig(),
						AiAgentVersionGrayDTO.class);
				// 优先精确匹配userIds
				if (!CollectionUtils.isEmpty(grayDTO.getUserIds()) && grayDTO.getUserIds().contains(userId)) {
					return version.getConfig();
				}
				// 按percent哈希取模
				if (grayDTO.getPercent() != null && grayDTO.getPercent() > 0) {
					int hash = (int) (Math.abs(userId) % 100);
					if (hash < grayDTO.getPercent()) {
						return version.getConfig();
					}
				}
			}
			catch (JsonProcessingException e) {
				log.warn("解析灰度配置失败, versionId={}", version.getId(), e);
			}
		}
		return null;
	}

	private int parseVersionNum(String version) {
		if (version == null || !version.startsWith("v")) {
			return 0;
		}
		try {
			return Integer.parseInt(version.substring(1));
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

}
