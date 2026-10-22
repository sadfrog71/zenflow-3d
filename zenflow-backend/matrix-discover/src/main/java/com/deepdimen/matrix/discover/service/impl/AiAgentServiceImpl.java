package com.deepdimen.matrix.discover.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.vo.AgentShareValidationVO;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.dify.DifyAgentBridge;
import com.deepdimen.matrix.discover.dto.AiAgentDTO;
import com.deepdimen.matrix.discover.dto.AiAgentImportDTO;
import com.deepdimen.matrix.discover.dto.ExternalAgentConfig;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.admin.api.entity.SysUserDept;
import com.deepdimen.matrix.admin.mapper.SysTenantMapper;
import com.deepdimen.matrix.admin.mapper.SysUserDeptMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiCollectReadMapper;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.AiAgentVersionService;
import com.deepdimen.matrix.discover.vo.AiAgentEnterpriseRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentMarketVO;
import com.deepdimen.matrix.discover.vo.AiAgentRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentStatisticsVO;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.deepdimen.matrix.discover.vo.AiUserTopAgentVO;
import com.deepdimen.matrix.discover.vo.MarketOverviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 智能体服务实现
 *
 * @author matrix
 * @date 2025-03-11
 */
@Slf4j
@Service
public class AiAgentServiceImpl extends ServiceImpl<AiAgentMapper, AiAgentEntity> implements AiAgentService {

	// 忽略未知字段：兼容旧 external 数据 config JSON 中残留的 authType/authParamName 键
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private static final String EXTERNAL_CONFIG_KEY = "external";

	private static final String AUTH_VALUE_MASK = "****";

	@Value("${security.encodeKey:deepmatrixmatrix}")
	private String encodeKey;

	private AES aesCipher;

	@PostConstruct
	private void initAes() {
		this.aesCipher = SecureUtil.aes(encodeKey.getBytes(StandardCharsets.UTF_8));
	}

	private final AiAgentPermissionService aiAgentPermissionService;

	private final AiAgentVersionService aiAgentVersionService;

	private final AiAgentSkillService aiAgentSkillService;

	private final AiCollectReadMapper aiCollectMapper;

	private final SysUserDeptMapper sysUserDeptMapper;

	private final SysTenantMapper sysTenantMapper;

	@Autowired(required = false)
	private DifyAgentBridge difyAgentBridge;

	@Autowired(required = false)
	private AiAssistantWorkflowCapabilityMapper workflowCapabilityMapper;

	private List<String> normalizeTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return Collections.emptyList();
		}
		return tags.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
	}

	public AiAgentServiceImpl(@Lazy AiAgentPermissionService aiAgentPermissionService,
			@Lazy AiAgentVersionService aiAgentVersionService, @Lazy AiAgentSkillService aiAgentSkillService,
			AiCollectReadMapper aiCollectMapper, SysUserDeptMapper sysUserDeptMapper, SysTenantMapper sysTenantMapper) {
		this.aiAgentPermissionService = aiAgentPermissionService;
		this.aiAgentVersionService = aiAgentVersionService;
		this.aiAgentSkillService = aiAgentSkillService;
		this.aiCollectMapper = aiCollectMapper;
		this.sysUserDeptMapper = sysUserDeptMapper;
		this.sysTenantMapper = sysTenantMapper;
	}

	@Override
	public AiAgentVO getAgentDetail(Long id) {
		AiAgentEntity entity = DEFAULT_AGENT_ID.equals(id) ? TenantBroker.noneAs(() -> getById(id)) : getById(id);
		if (entity == null) {
			throw new IllegalArgumentException("智能体不存在: " + id);
		}
		AiAgentVO vo = new AiAgentVO();
		BeanUtils.copyProperties(entity, vo);
		vo.setTags(normalizeTags(entity.getTags()));
		vo.setAccessible(calcAccessible(entity));
		vo.setCollectCount(aiCollectMapper.countByTarget(id, "agent").intValue());
		// 外部智能体：从 config JSON 反序列化 external 子节点回填 VO（authValue 脱敏）
		if ("external".equals(entity.getSource())) {
			fillExternalFieldsToVo(entity, vo);
		}
		else if (difyAgentBridge != null) {
			difyAgentBridge.mergeDifyInfo(vo);
		}
		return vo;
	}

	/** 把 entity.config.external 反序列化后填回 VO，authValue 脱敏为 ****。 */
	private void fillExternalFieldsToVo(AiAgentEntity entity, AiAgentVO vo) {
		ExternalAgentConfig ext = readExternalFromConfig(entity.getConfig());
		if (ext == null) {
			return;
		}
		vo.setEndpointUrl(ext.getEndpointUrl());
		vo.setAuthValue(StringUtils.hasText(ext.getAuthValue()) ? AUTH_VALUE_MASK : null);
	}

	// ============================================================
	// Dify 租户标签辅助方法
	// ============================================================

	private void bindTenantTagToDifyApp(String difyAppId, String tenantId) {
		if (difyAgentBridge == null || !StringUtils.hasText(tenantId)) {
			return;
		}
		String tenantCode = getTenantCode(tenantId);
		if (StringUtils.hasText(tenantCode)) {
			difyAgentBridge.bindTenantTag(difyAppId, tenantCode);
		}
	}

	private String getTenantCode(String tenantId) {
		SysTenant tenant = TenantBroker.noneAs(() -> sysTenantMapper.selectById(tenantId));
		return tenant != null ? tenant.getCode() : null;
	}

	// ============================================================
	// 外部智能体（source=external）辅助方法
	// ============================================================

	private String encryptAuthValue(String plain) {
		if (!StringUtils.hasText(plain)) {
			return null;
		}
		return aesCipher.encryptBase64(plain);
	}

	private String decryptAuthValue(String cipher) {
		if (!StringUtils.hasText(cipher)) {
			return null;
		}
		return aesCipher.decryptStr(cipher);
	}

	/** 从 config JSON 读取 external 子节点；不存在返回 null */
	private ExternalAgentConfig readExternalFromConfig(String configJson) {
		if (!StringUtils.hasText(configJson)) {
			return null;
		}
		try {
			Map<String, Object> root = OBJECT_MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {
			});
			Object node = root.get(EXTERNAL_CONFIG_KEY);
			if (node == null) {
				return null;
			}
			return OBJECT_MAPPER.convertValue(node, ExternalAgentConfig.class);
		}
		catch (Exception e) {
			log.warn("解析 ai_agent.config.external 失败: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 将 ExternalAgentConfig 写入 config JSON 的 external 子节点，保留其它顶级字段。 旧 config 为空时新建只含
	 * external 子节点的对象。
	 */
	private String writeExternalToConfig(String existingConfigJson, ExternalAgentConfig ext) {
		Map<String, Object> root;
		try {
			root = StringUtils.hasText(existingConfigJson)
					? OBJECT_MAPPER.readValue(existingConfigJson, new TypeReference<Map<String, Object>>() {
					}) : new LinkedHashMap<>();
		}
		catch (Exception e) {
			log.warn("现有 config JSON 解析失败，使用新对象覆盖: {}", e.getMessage());
			root = new LinkedHashMap<>();
		}
		root.put(EXTERNAL_CONFIG_KEY, ext);
		try {
			return OBJECT_MAPPER.writeValueAsString(root);
		}
		catch (Exception e) {
			throw new IllegalStateException("序列化 ai_agent.config 失败", e);
		}
	}

	/**
	 * 将 DTO 的外部接入虚拟字段写入 entity.config（authValue 是 **** 时保留旧密文）。 source=external 时调用，强制
	 * agent_type=external。endpointUrl 必填，authValue（token）选填。
	 */
	private void applyExternalFieldsToEntity(AiAgentDTO dto, AiAgentEntity entity, AiAgentEntity existing) {
		if (!StringUtils.hasText(dto.getEndpointUrl())) {
			throw new IllegalArgumentException("外部智能体接入：endpointUrl 必填");
		}
		ExternalAgentConfig ext = new ExternalAgentConfig();
		ext.setEndpointUrl(dto.getEndpointUrl());
		// authValue 处理：**** 视为占位，保留旧密文；非空且非 **** 时加密；空值清空（公开嵌入）
		if (AUTH_VALUE_MASK.equals(dto.getAuthValue())) {
			ExternalAgentConfig oldExt = existing != null ? readExternalFromConfig(existing.getConfig()) : null;
			ext.setAuthValue(oldExt != null ? oldExt.getAuthValue() : null);
		}
		else {
			ext.setAuthValue(encryptAuthValue(dto.getAuthValue()));
		}
		String existingConfig = existing != null ? existing.getConfig() : entity.getConfig();
		entity.setConfig(writeExternalToConfig(existingConfig, ext));
		entity.setAgentType("external");
		entity.setSource("external");
	}

	@Override
	public Page<AiAgentVO> pageAgent(Page<AiAgentEntity> page, AiAgentDTO agentDTO) {
		if (agentDTO == null) {
			agentDTO = new AiAgentDTO();
		}
		Page<AiAgentEntity> entityPage = page(page, Wrappers.<AiAgentEntity>lambdaQuery()
			.like(StringUtils.hasText(agentDTO.getName()), AiAgentEntity::getName, agentDTO.getName())
			.eq(StringUtils.hasText(agentDTO.getStatus()), AiAgentEntity::getStatus, agentDTO.getStatus())
			.eq(StringUtils.hasText(agentDTO.getCategory()), AiAgentEntity::getCategory, agentDTO.getCategory())
			.eq(StringUtils.hasText(agentDTO.getAgentType()), AiAgentEntity::getAgentType, agentDTO.getAgentType())
			.eq(StringUtils.hasText(agentDTO.getDifyMode()), AiAgentEntity::getDifyMode, agentDTO.getDifyMode())
			.eq(StringUtils.hasText(agentDTO.getPermission()), AiAgentEntity::getPermission, agentDTO.getPermission())
			.eq(StringUtils.hasText(agentDTO.getSource()), AiAgentEntity::getSource, agentDTO.getSource())
			.apply(StringUtils.hasText(agentDTO.getTag()), "JSON_CONTAINS(tags, JSON_QUOTE({0}), '$')",
					agentDTO.getTag() == null ? null : agentDTO.getTag().trim())
			.orderByDesc(AiAgentEntity::getSort)
			.orderByDesc(AiAgentEntity::getCreateTime));
		Page<AiAgentVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
		List<AiAgentVO> voList = entityPage.getRecords().stream().map(entity -> {
			AiAgentVO vo = new AiAgentVO();
			BeanUtils.copyProperties(entity, vo);
			vo.setTags(normalizeTags(entity.getTags()));
			// 外部智能体：从 config 反序列化外部接入字段回填 VO（authValue 脱敏）
			if ("external".equals(entity.getSource())) {
				fillExternalFieldsToVo(entity, vo);
			}
			// 列表接口不返回大字段
			vo.setConfig(null);
			vo.setMarketConfig(null);
			return vo;
		}).toList();
		if (difyAgentBridge != null) {
			difyAgentBridge.batchMergeDifyInfo(voList);
		}
		voPage.setRecords(voList);
		return voPage;
	}

	@Override
	public List<String> listTagSuggestions(boolean marketOnly) {
		var query = Wrappers.<AiAgentEntity>lambdaQuery()
			.select(AiAgentEntity::getTags)
			.isNotNull(AiAgentEntity::getTags)
			.eq(marketOnly, AiAgentEntity::getStatus, "published");
		return list(query).stream()
			.filter(Objects::nonNull)
			.map(AiAgentEntity::getTags)
			.flatMap(tags -> normalizeTags(tags).stream())
			.distinct()
			.sorted()
			.toList();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiAgentVO createAgent(AiAgentDTO agentDTO) {
		AiAgentEntity entity = new AiAgentEntity();
		BeanUtils.copyProperties(agentDTO, entity);
		entity.setTags(normalizeTags(agentDTO.getTags()));
		entity.setStatus("draft");
		entity.setUseCount(0);
		// permission 为空时默认 public
		if (!StringUtils.hasText(entity.getPermission())) {
			entity.setPermission("public");
		}
		// source 为空时默认 local
		if (!StringUtils.hasText(entity.getSource())) {
			entity.setSource("local");
		}
		// 外部智能体（source=external）：写入 config.external，强制 agent_type=external，跳过 Dify 联动
		if ("external".equals(entity.getSource())) {
			applyExternalFieldsToEntity(agentDTO, entity, null);
			save(entity);
			aiAgentPermissionService.createDefaultPermission(entity.getId());
			log.info("创建外部智能体成功, id={}, name={}", entity.getId(), entity.getName());
			AiAgentVO vo = new AiAgentVO();
			BeanUtils.copyProperties(entity, vo);
			vo.setEndpointUrl(agentDTO.getEndpointUrl());
			vo.setAuthValue(StringUtils.hasText(agentDTO.getAuthValue()) ? AUTH_VALUE_MASK : null);
			return vo;
		}
		// 本地智能体：agentType 为空时默认 dify
		if (!StringUtils.hasText(entity.getAgentType())) {
			entity.setAgentType("dify");
		}
		// difyMode 为空时默认 agent-chat
		if (!StringUtils.hasText(entity.getDifyMode())) {
			entity.setDifyMode("agent-chat");
		}
		save(entity);
		// 联动创建默认权限（全部可见）
		aiAgentPermissionService.createDefaultPermission(entity.getId());
		log.info("创建智能体成功, id={}, name={}", entity.getId(), entity.getName());
		// agentType 为 dify 时，事务提交后联动创建 Dify 应用（避免事务内持有连接等待HTTP响应）
		if ("dify".equals(entity.getAgentType()) && difyAgentBridge != null) {
			Long entityId = entity.getId();
			String name = entity.getName();
			String description = entity.getDescription();
			String difyMode = entity.getDifyMode();
			String tenantId = entity.getTenantId();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						String difyAppId = difyAgentBridge.createDifyApp(name, description, difyMode);
						if (difyAppId != null) {
							AiAgentEntity update = new AiAgentEntity();
							update.setId(entityId);
							update.setDifyAppId(difyAppId);
							// 获取或创建 Dify API Key
							String apiKey = difyAgentBridge.getOrCreateApiKey(difyAppId);
							if (apiKey != null) {
								update.setDifyApiKey(apiKey);
							}
							updateById(update);
							// 绑定租户标签到 Dify 应用
							bindTenantTagToDifyApp(difyAppId, tenantId);
						}
					}
					catch (Exception e) {
						log.error("Dify应用可能已创建但本地绑定失败, 需排查清理, entityId={}", entityId, e);
					}
				}
			});
		}
		AiAgentVO vo = new AiAgentVO();
		BeanUtils.copyProperties(entity, vo);
		return vo;
	}

	@Override
	public void updateAgent(AiAgentDTO agentDTO) {
		if (agentDTO.getId() == null) {
			throw new IllegalArgumentException("智能体ID不能为空");
		}
		AiAgentEntity existing = getById(agentDTO.getId());
		if (existing == null) {
			throw new IllegalArgumentException("智能体不存在: " + agentDTO.getId());
		}
		AiAgentEntity entity = new AiAgentEntity();
		BeanUtils.copyProperties(agentDTO, entity);
		if (agentDTO.getTags() != null) {
			entity.setTags(normalizeTags(agentDTO.getTags()));
		}
		// 状态变更必须通过专用接口（publish/disable/enable），禁止通过更新接口绕过状态机
		entity.setStatus(null);
		// 外部智能体（source=external）：写入 config.external，强制 agent_type=external，跳过 Dify 联动
		String effectiveSource = StringUtils.hasText(entity.getSource()) ? entity.getSource() : existing.getSource();
		if ("external".equals(effectiveSource)) {
			applyExternalFieldsToEntity(agentDTO, entity, existing);
			updateById(entity);
			markWorkflowCapabilityStale(existing, entity);
			return;
		}
		updateById(entity);
		markWorkflowCapabilityStale(existing, entity);
		// difyAppId 不为空时联动更新 Dify 应用
		if (difyAgentBridge != null && StringUtils.hasText(existing.getDifyAppId())) {
			difyAgentBridge.syncDifyApp(existing.getDifyAppId(), agentDTO.getName(), agentDTO.getDescription());
			// 绑定租户标签（幂等，已绑定不会重复）
			bindTenantTagToDifyApp(existing.getDifyAppId(), existing.getTenantId());
			// 同步模型配置到 Dify
			if (StringUtils.hasText(entity.getConfig())) {
				difyAgentBridge.syncDifyModelConfig(existing.getDifyAppId(), entity.getConfig());
				// 同步知识库到 Dify 外部知识库
				String currentUserId = SecurityUtils.getUser() != null ? String.valueOf(SecurityUtils.getUser().getId())
						: "system";
				difyAgentBridge.syncDatasets(existing.getDifyAppId(),
						withDatasetNames(entity.getConfig(), existing.getTenantId()), existing.getTenantId(),
						currentUserId);
			}
			// difyApiKey 为空时补充获取
			if (!StringUtils.hasText(existing.getDifyApiKey())) {
				String apiKey = difyAgentBridge.getOrCreateApiKey(existing.getDifyAppId());
				if (apiKey != null) {
					AiAgentEntity update = new AiAgentEntity();
					update.setId(existing.getId());
					update.setDifyApiKey(apiKey);
					updateById(update);
				}
			}
		}
		else if (difyAgentBridge != null && "dify".equals(existing.getAgentType())) {
			// difyAppId 为空且 agentType=dify 时，补偿创建 Dify 应用
			String difyAppId = difyAgentBridge.createDifyApp(agentDTO.getName(), agentDTO.getDescription(),
					existing.getDifyMode());
			if (difyAppId != null) {
				AiAgentEntity update = new AiAgentEntity();
				update.setId(existing.getId());
				update.setDifyAppId(difyAppId);
				// 补偿创建时也获取 API Key
				String apiKey = difyAgentBridge.getOrCreateApiKey(difyAppId);
				if (apiKey != null) {
					update.setDifyApiKey(apiKey);
				}
				updateById(update);
				// 绑定租户标签到 Dify 应用
				bindTenantTagToDifyApp(difyAppId, existing.getTenantId());
				// 补偿创建后同步模型配置
				if (StringUtils.hasText(entity.getConfig())) {
					difyAgentBridge.syncDifyModelConfig(difyAppId, entity.getConfig());
					// 补偿创建后同步知识库
					String currentUserId = SecurityUtils.getUser() != null
							? String.valueOf(SecurityUtils.getUser().getId()) : "system";
					difyAgentBridge.syncDatasets(difyAppId,
							withDatasetNames(entity.getConfig(), existing.getTenantId()), existing.getTenantId(),
							currentUserId);
				}
			}
		}
	}

	private void markWorkflowCapabilityStale(AiAgentEntity existing, AiAgentEntity update) {
		if (workflowCapabilityMapper == null || !routingSourceChanged(existing, update)) {
			return;
		}
		workflowCapabilityMapper.update(null,
				Wrappers.<com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity>lambdaUpdate()
					.eq(com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity::getAgentId,
							existing.getId())
					.eq(com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity::getStatus, "PUBLISHED")
					.set(com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity::getStatus, "STALE"));
	}

	private boolean routingSourceChanged(AiAgentEntity existing, AiAgentEntity update) {
		return changed(existing.getName(), update.getName())
				|| changed(existing.getDescription(), update.getDescription())
				|| changed(existing.getUseScenes(), update.getUseScenes())
				|| changed(existing.getOperationGuide(), update.getOperationGuide())
				|| changed(existing.getDifyAppId(), update.getDifyAppId())
				|| changed(existing.getDifyMode(), update.getDifyMode());
	}

	private boolean changed(String current, String requested) {
		return requested != null && !Objects.equals(current, requested);
	}

	/**
	 * 根据智能体配置中的 datasetIds 查询 Matrix 知识库真实名称，仅用于 Dify 同步，不写回智能体配置。
	 */
	private String withDatasetNames(String configJson, String tenantId) {
		if (!StringUtils.hasText(configJson)) {
			return configJson;
		}
		try {
			JsonNode rootNode = OBJECT_MAPPER.readTree(configJson);
			if (!(rootNode instanceof ObjectNode root) || !root.path("datasetIds").isArray()
					|| root.path("datasetIds").isEmpty()) {
				return configJson;
			}
			List<Long> datasetIds = new ArrayList<>();
			for (JsonNode idNode : root.path("datasetIds")) {
				try {
					datasetIds.add(Long.valueOf(idNode.asText()));
				}
				catch (NumberFormatException e) {
					log.warn("智能体配置包含无效知识库ID, datasetId={}", idNode.asText());
				}
			}
			if (datasetIds.isEmpty()) {
				return configJson;
			}
			List<Map<String, Object>> datasets = baseMapper.selectDatasetNames(datasetIds);
			ObjectNode names = OBJECT_MAPPER.createObjectNode();
			for (Map<String, Object> dataset : datasets) {
				Object id = dataset.get("id");
				Object name = dataset.get("name");
				if (id != null && name != null && StringUtils.hasText(name.toString())) {
					names.put(id.toString(), name.toString());
				}
			}
			root.set("datasetNames", names);
			String tenantCode = getTenantCode(tenantId);
			if (StringUtils.hasText(tenantCode)) {
				root.put("tenantCode", tenantCode);
			}
			return OBJECT_MAPPER.writeValueAsString(root);
		}
		catch (Exception e) {
			log.warn("查询知识库真实名称失败，继续使用原同步配置: {}", e.getMessage());
			return configJson;
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void publishAgent(Long id) {
		AiAgentEntity existing = getById(id);
		if (existing == null) {
			throw new IllegalArgumentException("智能体不存在: " + id);
		}
		aiAgentSkillService.validateForPublish(id);
		// 先完成远端投影；任一步失败时保持原平台状态，不生成活动版本。
		if ("dify".equals(existing.getAgentType()) && difyAgentBridge != null
				&& StringUtils.hasText(existing.getDifyAppId())) {
			if (!StringUtils.hasText(existing.getDifyApiKey())) {
				String apiKey = difyAgentBridge.getOrCreateApiKey(existing.getDifyAppId());
				if (apiKey != null) {
					existing.setDifyApiKey(apiKey);
					AiAgentEntity update = new AiAgentEntity();
					update.setId(id);
					update.setDifyApiKey(apiKey);
					updateById(update);
				}
			}
			// advanced-chat/workflow 发布时联动 Dify workflow 发布（确保 Dify 侧最新草稿被发布）
			String difyMode = existing.getDifyMode();
			if ("advanced-chat".equals(difyMode) || "workflow".equals(difyMode)) {
				difyAgentBridge.publishDifyWorkflow(existing.getDifyAppId());
			}
		}
		aiAgentSkillService.projectForPublish(id);
		aiAgentVersionService.createSnapshot(id, existing.getConfig(), null);
		changeStatus(id, "published");
		log.info("发布智能体, id={}", id);
	}

	@Override
	public void disableAgent(Long id) {
		changeStatus(id, "disabled");
		log.info("禁用智能体, id={}", id);
	}

	@Override
	public void enableAgent(Long id) {
		changeStatus(id, "published");
		log.info("启用智能体, id={}", id);
	}

	@Override
	public Page<AiAgentMarketVO> marketList(String keyword, String category, String source, String tag, String excludeTag,
			Boolean accessible, String sortBy, int pageNum, int pageSize, boolean favoriteOnly) {
		String normalizedTag = StringUtils.hasText(tag) ? tag.trim() : null;
		String normalizedExcludeTag = StringUtils.hasText(excludeTag) ? excludeTag.trim() : null;
		Long userId = null;
		List<Long> roleIds = null;
		List<Long> deptIds = null;
		try {
			var user = SecurityUtils.getUser();
			if (user != null) {
				userId = user.getId();
				roleIds = SecurityUtils.getRoleIds();
				if (sysUserDeptMapper != null) {
					List<com.deepdimen.matrix.admin.api.entity.SysUserDept> userDepts = sysUserDeptMapper
						.selectList(Wrappers.<com.deepdimen.matrix.admin.api.entity.SysUserDept>lambdaQuery()
							.eq(com.deepdimen.matrix.admin.api.entity.SysUserDept::getUserId, user.getId()));
					deptIds = userDepts.stream()
						.map(com.deepdimen.matrix.admin.api.entity.SysUserDept::getDeptId)
						.toList();
				}
			}
		}
		catch (Exception e) {
			log.warn("获取当前用户信息失败, 收藏状态将不可用: {}", e.getMessage());
		}
		Page<AiAgentMarketVO> page = new Page<>(pageNum, pageSize);
		return baseMapper.selectMarketPage(page, keyword, category, source, normalizedTag, normalizedExcludeTag, sortBy, userId, roleIds,
				deptIds, accessible, favoriteOnly);
	}

	@Override
	public AiAgentStatisticsVO statistics() {
		AiAgentStatisticsVO vo = baseMapper.selectStatistics();
		vo.setAvgSatisfaction(baseMapper.selectGlobalSatisfaction());
		vo.setTotalFavorites(baseMapper.selectTotalFavorites());
		return vo;
	}

	@Override
	public MarketOverviewVO marketOverview(String excludeTag) {
		MarketOverviewVO vo = new MarketOverviewVO();
		String normalizedExcludeTag = StringUtils.hasText(excludeTag) ? excludeTag.trim() : null;

		// 概览与市场列表使用同一标签过滤口径。
		long localCount = baseMapper.selectPublishedCount("local", normalizedExcludeTag);
		vo.setLocalCount(localCount);

		long thirdPartyCount = baseMapper.selectPublishedCount("external", normalizedExcludeTag);
		vo.setThirdPartyCount(thirdPartyCount);
		vo.setTotalCount(localCount + thirdPartyCount);

		// selectAvailableCount 已从合表统计本地和外部智能体，无需重复累加外部数量。
		try {
			var user = SecurityUtils.getUser();
			if (user != null) {
				List<Long> roleIds = SecurityUtils.getRoleIds();
				List<Long> userDeptIds = null;
				if (sysUserDeptMapper != null) {
					List<SysUserDept> userDepts = sysUserDeptMapper
						.selectList(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, user.getId()));
					userDeptIds = userDepts.stream().map(SysUserDept::getDeptId).toList();
				}
				vo.setAvailableCount(
						baseMapper.selectAvailableCount(user.getId(), roleIds, userDeptIds, normalizedExcludeTag));
				// 合表后收藏类型统一为 agent，无需再分别统计
				vo.setFavoriteCount(aiCollectMapper.countByUserAndType(user.getId(), "agent"));
			}
			else {
				vo.setAvailableCount(0L);
				vo.setFavoriteCount(0L);
			}
		}
		catch (Exception e) {
			log.warn("查询用户权限/收藏数失败: {}", e.getMessage());
			vo.setAvailableCount(0L);
			vo.setFavoriteCount(0L);
		}

		// 今日/昨日调用次数
		LocalDate today = LocalDate.now();
		String todayStr = today.toString();
		String yesterdayStr = today.minusDays(1).toString();
		String tomorrowStr = today.plusDays(1).toString();

		long todayCall = baseMapper.selectCallCount(todayStr, tomorrowStr);
		vo.setTodayCallCount(todayCall);

		long yesterdayCall = baseMapper.selectCallCount(yesterdayStr, todayStr);
		if (yesterdayCall > 0) {
			BigDecimal growth = BigDecimal.valueOf(todayCall - yesterdayCall)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(yesterdayCall), 1, RoundingMode.HALF_UP);
			vo.setCallGrowthRate(growth);
		}
		else {
			vo.setCallGrowthRate(BigDecimal.ZERO);
		}

		return vo;
	}

	@Override
	public List<AiAgentEnterpriseRankVO> enterpriseRank(String sortBy) {
		List<AiAgentEnterpriseRankVO> list = TenantBroker.noneAs(() -> baseMapper.selectEnterpriseRankList(sortBy));
		// 推荐企业置顶（recommended=true 排在最前，推荐配置由后台管理接口维护）
		if (list != null && !list.isEmpty()) {
			list.sort(Comparator
				.comparing((AiAgentEnterpriseRankVO v) -> Boolean.TRUE.equals(v.getRecommended()) ? 0 : 1));
		}
		return list;
	}

	@Override
	public List<AiAgentRankVO> rankList(String sortBy, int limit) {
		return baseMapper.selectRankList(sortBy, limit);
	}

	@Override
	public List<AiUserTopAgentVO> userTopAgents(int limit) {
		var user = SecurityUtils.getUser();
		if (user == null) {
			return List.of();
		}
		return baseMapper.selectUserTopAgents(user.getUsername(), limit);
	}

	private boolean calcAccessible(AiAgentEntity entity) {
		if ("public".equals(entity.getPermission())) {
			return true;
		}
		var permissionVO = aiAgentPermissionService.getPermission(entity.getId());
		if ("all".equals(permissionVO.getPermissionType())) {
			return true;
		}
		try {
			var user = SecurityUtils.getUser();
			if (user == null) {
				return false;
			}
			List<Long> userIds = permissionVO.getUserIds();
			if (userIds != null && userIds.contains(user.getId())) {
				return true;
			}
			List<Long> roleIds = permissionVO.getRoleIds();
			List<Long> userRoleIds = SecurityUtils.getRoleIds();
			if (roleIds != null && userRoleIds != null && userRoleIds.stream().anyMatch(roleIds::contains)) {
				return true;
			}
			// 部门权限校验
			List<Long> deptIds = permissionVO.getDeptIds();
			if (deptIds != null && !deptIds.isEmpty() && sysUserDeptMapper != null) {
				List<SysUserDept> userDepts = sysUserDeptMapper
					.selectList(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, user.getId()));
				List<Long> userDeptIds = userDepts.stream().map(SysUserDept::getDeptId).toList();
				if (userDeptIds.stream().anyMatch(deptIds::contains)) {
					return true;
				}
			}
			return false;
		}
		catch (Exception e) {
			log.warn("权限计算异常, 默认无权限: {}", e.getMessage());
			return false;
		}
	}

	private void changeStatus(Long id, String status) {
		AiAgentEntity entity = new AiAgentEntity();
		entity.setId(id);
		entity.setStatus(status);
		updateById(entity);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiAgentVO importDifyAgent(AiAgentImportDTO importDTO) {
		if (difyAgentBridge == null) {
			throw new IllegalStateException("Dify集成未启用, 无法导入");
		}
		boolean hasDsl = StringUtils.hasText(importDTO.getDslContent());
		boolean hasAppId = StringUtils.hasText(importDTO.getDifyAppId());
		if (!hasDsl && !hasAppId) {
			throw new IllegalArgumentException("DSL内容与Dify应用ID必须二选一");
		}

		String difyAppId;
		String difyApiKey;
		boolean isNewDifyApp;
		if (hasAppId) {
			// 关联已有 Dify 应用分支
			difyAppId = importDTO.getDifyAppId();
			// 校验本地是否已被占用
			AiAgentEntity occupied = getOne(
					Wrappers.<AiAgentEntity>lambdaQuery().eq(AiAgentEntity::getDifyAppId, difyAppId).last("LIMIT 1"),
					false);
			if (occupied != null) {
				throw new IllegalArgumentException("该 Dify 应用已被智能体[" + occupied.getName() + "]关联, 无法重复关联");
			}
			// 复用或创建 Dify API Key
			difyApiKey = difyAgentBridge.getOrCreateApiKey(difyAppId);
			isNewDifyApp = false;
		}
		else {
			// 导入 DSL 到 Dify（事务内包含HTTP调用，因为需要先拿到difyAppId才能创建本地记录；低频管理操作可接受）
			String[] difyResult = difyAgentBridge.importDifyApp(importDTO.getDslContent());
			if (difyResult == null || difyResult[0] == null) {
				throw new IllegalStateException("导入Dify DSL失败, 请检查DSL内容是否合法");
			}
			difyAppId = difyResult[0];
			difyApiKey = difyResult[1];
			isNewDifyApp = true;
		}

		// 2. 获取 Dify 应用信息，用于兜底名称/描述
		String name = importDTO.getName();
		String description = importDTO.getDescription();
		String category = StringUtils.hasText(importDTO.getCategory()) ? importDTO.getCategory() : "general";
		com.fasterxml.jackson.databind.JsonNode difyApp = difyAgentBridge.getCachedDifyApp(difyAppId);
		if (difyApp != null) {
			if (!StringUtils.hasText(name) && difyApp.has("name") && !difyApp.get("name").isNull()) {
				name = difyApp.get("name").asText("");
			}
			if (!StringUtils.hasText(description) && difyApp.has("description")
					&& !difyApp.get("description").isNull()) {
				description = difyApp.get("description").asText("");
			}
		}
		if (!StringUtils.hasText(name)) {
			name = hasAppId ? "关联的Dify应用" : "导入的智能体";
		}

		// 3. 用户填了名称/描述时同步到 Dify 应用（覆盖DSL中的原始值）
		if (StringUtils.hasText(importDTO.getName()) || StringUtils.hasText(importDTO.getDescription())) {
			difyAgentBridge.syncDifyApp(difyAppId, importDTO.getName(), importDTO.getDescription());
		}

		// 4. 反向读取 Dify 应用配置
		String config = difyAgentBridge.readDifyAppConfig(difyAppId);

		// 5. 创建本地 ai_agent 记录
		try {
			AiAgentEntity entity = new AiAgentEntity();
			entity.setName(name);
			entity.setDescription(description);
			entity.setCategory(category);
			entity.setTags(normalizeTags(importDTO.getTags()));
			entity.setAgentType("dify");
			entity.setStatus("draft");
			entity.setUseCount(0);
			entity.setPermission("public");
			entity.setSource("local");
			entity.setDifyAppId(difyAppId);
			entity.setDifyApiKey(difyApiKey);
			// 从 Dify 应用详情读取 mode
			if (difyApp != null && difyApp.has("mode") && !difyApp.get("mode").isNull()) {
				entity.setDifyMode(difyApp.get("mode").asText("agent-chat"));
			}
			else {
				entity.setDifyMode("agent-chat");
			}
			if (StringUtils.hasText(config)) {
				entity.setConfig(config);
			}
			save(entity);

			// 6. 创建默认权限
			aiAgentPermissionService.createDefaultPermission(entity.getId());
			// 7. 绑定租户标签到 Dify 应用
			bindTenantTagToDifyApp(difyAppId, entity.getTenantId());
			log.info("{}Dify应用创建智能体成功, id={}, name={}, difyAppId={}", hasAppId ? "关联" : "导入", entity.getId(),
					entity.getName(), difyAppId);

			AiAgentVO vo = new AiAgentVO();
			BeanUtils.copyProperties(entity, vo);
			return vo;
		}
		catch (Exception e) {
			// 本地保存失败时，仅对新导入的 Dify 应用做清理（关联已有的不能删除）
			if (isNewDifyApp) {
				log.error("导入Dify DSL本地保存失败, 尝试清理Dify应用, difyAppId={}", difyAppId, e);
				difyAgentBridge.deleteDifyApp(difyAppId);
			}
			else {
				log.error("关联Dify应用本地保存失败, difyAppId={}", difyAppId, e);
			}
			throw e;
		}
	}

	@Override
	public List<Map<String, Object>> listAvailableDifyApps(String keyword) {
		if (difyAgentBridge == null) {
			return Collections.emptyList();
		}
		Long tenantId = SecurityUtils.getUser() != null ? SecurityUtils.getUser().getTenantId() : null;
		if (tenantId == null) {
			return Collections.emptyList();
		}
		SysTenant tenant = TenantBroker.noneAs(() -> sysTenantMapper.selectById(tenantId));
		if (tenant == null || !StringUtils.hasText(tenant.getCode())) {
			return Collections.emptyList();
		}
		return difyAgentBridge.listAvailableDifyApps(tenant.getCode(), keyword);
	}

	@Override
	public boolean removeById(Serializable id) {
		AiAgentEntity existing = getById(id);
		String difyAppId = (existing != null) ? existing.getDifyAppId() : null;
		boolean result = super.removeById(id);
		// 逻辑删除成功后联动删除 Dify 应用
		if (result && difyAgentBridge != null && StringUtils.hasText(difyAppId)) {
			difyAgentBridge.deleteDifyApp(difyAppId);
		}
		return result;
	}

	// ============================================================
	// 外部智能体（source=external）连接测试 / iframe URL 生成
	// ============================================================

	@Override
	public Map<String, Object> testExternalConnection(Long id) {
		AiAgentEntity entity = getById(id);
		if (entity == null) {
			throw new IllegalArgumentException("智能体不存在: " + id);
		}
		if (!"external".equals(entity.getSource())) {
			throw new IllegalArgumentException("仅外部智能体（source=external）支持连接测试");
		}
		ExternalAgentConfig ext = readExternalFromConfig(entity.getConfig());
		if (ext == null || !StringUtils.hasText(ext.getEndpointUrl())) {
			throw new IllegalArgumentException("外部智能体未配置 endpointUrl");
		}
		// token 非空则拼接固定 token 参数后测试真实可访问的 URL
		String plain = decryptAuthValue(ext.getAuthValue());
		String testUrl = appendQueryParam(ext.getEndpointUrl(), "token", plain);

		Map<String, Object> result = new LinkedHashMap<>();
		try {
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
			long headStartNs = System.nanoTime();
			HttpResponse<Void> resp = client.send(buildRequest(testUrl, "HEAD"),
					HttpResponse.BodyHandlers.discarding());
			int code = resp.statusCode();
			long ms = (System.nanoTime() - headStartNs) / 1_000_000;
			boolean degraded = false;
			// 405 → 降级 GET（responseTime 仅记录最终请求耗时，message 标注降级）
			if (code == 405) {
				long getStartNs = System.nanoTime();
				resp = client.send(buildRequest(testUrl, "GET"), HttpResponse.BodyHandlers.discarding());
				code = resp.statusCode();
				ms = (System.nanoTime() - getStartNs) / 1_000_000;
				degraded = true;
			}
			boolean ok = (code >= 200 && code < 300) || code == 405;
			String suffix = degraded ? "（HEAD→GET 降级）" : "";
			result.put("success", ok);
			result.put("responseTime", ms);
			if (ok) {
				result.put("message", "连接成功，HTTP " + code + suffix);
			}
			else {
				result.put("message", "端点返回 HTTP " + code + suffix);
			}
		}
		catch (java.net.http.HttpTimeoutException te) {
			result.put("success", false);
			result.put("responseTime", 10000L);
			result.put("message", "连接超时（10s）");
		}
		catch (Exception e) {
			result.put("success", false);
			result.put("responseTime", 0L);
			result.put("message", "连接异常: " + e.getMessage());
		}
		return result;
	}

	@Override
	public Map<String, String> getExternalIframeUrl(Long id) {
		AiAgentEntity entity = getById(id);
		if (entity == null) {
			throw new IllegalArgumentException("智能体不存在: " + id);
		}
		// 二次访问校验：当前用户必须有该智能体的访问权限，避免越权拿到 token
		if (!Boolean.TRUE.equals(calcAccessible(entity))) {
			throw new IllegalArgumentException("当前用户无权访问该智能体");
		}
		if (!"external".equals(entity.getSource())) {
			throw new IllegalArgumentException("仅外部智能体（source=external）支持 iframe URL 生成");
		}
		ExternalAgentConfig ext = readExternalFromConfig(entity.getConfig());
		if (ext == null || !StringUtils.hasText(ext.getEndpointUrl())) {
			throw new IllegalArgumentException("外部智能体未配置 endpointUrl");
		}
		String url = ext.getEndpointUrl();
		// token 非空则按固定参数名 token 拼接为 URL 参数；空则原样返回（公开嵌入）
		String plain = decryptAuthValue(ext.getAuthValue());
		url = appendQueryParam(url, "token", plain);
		Map<String, String> result = new HashMap<>();
		result.put("url", url);
		result.put("name", entity.getName());
		return result;
	}

	@Override
	public String getTenantIdByAgentId(Long agentId) {
		if (agentId == null) {
			return null;
		}
		AiAgentEntity entity = TenantBroker.noneAs(() -> getById(agentId));
		return entity == null ? null : entity.getTenantId();
	}

	@Override
	public AgentShareValidationVO validateShareAgent(Long agentId) {
		if (agentId == null) {
			return null;
		}
		AiAgentEntity entity = TenantBroker.noneAs(() -> getById(agentId));
		if (entity == null) {
			return null;
		}
		return new AgentShareValidationVO(entity.getId(), entity.getTenantId(), entity.getStatus(),
				"0".equals(entity.getDelFlag()) && "published".equals(entity.getStatus()));
	}

	private String appendQueryParam(String url, String name, String value) {
		if (!StringUtils.hasText(value)) {
			return url;
		}
		String separator = url.contains("?") ? "&" : "?";
		return url + separator + URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private HttpRequest buildRequest(String url, String method) {
		return HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(10))
			.method(method, HttpRequest.BodyPublishers.noBody())
			.build();
	}

}
