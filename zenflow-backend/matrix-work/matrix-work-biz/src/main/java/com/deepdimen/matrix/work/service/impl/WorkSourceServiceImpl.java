package com.deepdimen.matrix.work.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.work.api.dto.WorkSourceSaveDTO;
import com.deepdimen.matrix.work.api.enums.WorkSourceType;
import com.deepdimen.matrix.work.entity.WorkSourceEntity;
import com.deepdimen.matrix.work.mapper.WorkSourceMapper;
import com.deepdimen.matrix.work.service.WorkAiDatasourceReferenceReader;
import com.deepdimen.matrix.work.service.WorkSourceService;
import com.deepdimen.matrix.work.security.CurrentOrganizationResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkSourceServiceImpl extends ServiceImpl<WorkSourceMapper, WorkSourceEntity>
		implements WorkSourceService {

	private final WorkAiDatasourceReferenceReader aiDatasourceReferenceReader;

	private final ObjectMapper objectMapper;

	private final CurrentOrganizationResolver organizationResolver;

	@Override
	public WorkSourceEntity create(WorkSourceSaveDTO request) {
		validate(request);
		WorkSourceEntity source = new WorkSourceEntity();
		copy(request, source);
		source.setOrgId(organizationResolver.resolve());
		source.setOwnerUserId(
				request.getSourceType() == WorkSourceType.ENTERPRISE_MAIL ? SecurityUtils.getUser().getId() : null);
		source.setTenantId(TenantContextHolder.getTenantId());
		source.setHealthStatus("NOT_CHECKED");
		save(source);
		return source;
	}

	@Override
	public WorkSourceEntity update(Long id, WorkSourceSaveDTO request) {
		validate(request);
		WorkSourceEntity source = getById(id);
		if (source == null) {
			throw new IllegalArgumentException("未找到对应数据源");
		}
		copy(request, source);
		if (!updateById(source)) {
			throw new IllegalStateException("数据源已被其他操作更新，请刷新后重试");
		}
		return source;
	}

	private void validate(WorkSourceSaveDTO request) {
		if (!request.getSourceType().isConfigurable()) {
			throw new IllegalArgumentException(request.getSourceType().getDisplayName() + "尚未开放配置");
		}
		String protocol = StrUtil.blankToDefault(request.getProtocolType(), "").toUpperCase(Locale.ROOT);
		if ("SMTP".equals(protocol)) {
			throw new IllegalArgumentException("SMTP 为发送协议，待办中心禁止配置邮件发送能力");
		}
		if (!request.getSourceType().getProtocols().contains(protocol)) {
			throw new IllegalArgumentException("当前数据源不支持协议：" + protocol);
		}
		if (request.getSourceType() == WorkSourceType.AI_DATASOURCE) {
			if (request.getConfigRefId() == null) {
				throw new IllegalArgumentException("请选择需要复用的 AI 问数数据库连接");
			}
			if (!aiDatasourceReferenceReader.exists(TenantContextHolder.getTenantId(), request.getConfigRefId())) {
				throw new IllegalArgumentException("所选 AI 问数数据库连接不存在或当前租户不可见");
			}
		}
		if (request.getSourceType() == WorkSourceType.ENTERPRISE_MAIL) {
			if (StrUtil.isBlank(request.getSecretRef())) {
				throw new IllegalArgumentException("企业邮箱必须配置 Secret 引用");
			}
			String secretRef = request.getSecretRef().toLowerCase(Locale.ROOT);
			if (!secretRef.startsWith("env://")) {
				throw new IllegalArgumentException("当前邮箱读取适配器仅支持 env:// Secret 引用");
			}
		}
		String config = StrUtil.blankToDefault(request.getConfigJson(), "").toLowerCase(Locale.ROOT);
		if (config.contains("password") || config.contains("passwd") || config.contains("secret")
				|| config.contains("token") || config.contains("authorization") || config.contains("credential")) {
			throw new IllegalArgumentException("敏感凭证不得写入普通配置，请使用 Secret 引用");
		}
		JsonNode details = parseConfig(request.getConfigJson());
		if (request.getSourceType() == WorkSourceType.ENTERPRISE_MAIL
				&& (details.path("server").asText().isBlank() || details.path("mailbox").asText().isBlank())) {
			throw new IllegalArgumentException("企业邮箱必须配置服务器地址和读取邮箱");
		}
		if (request.getSourceType() == WorkSourceType.LOCAL_FOLDER && !details.path("path").asText().startsWith("/")) {
			throw new IllegalArgumentException("服务器文件夹必须使用绝对路径");
		}
	}

	private JsonNode parseConfig(String configJson) {
		try {
			return objectMapper.readTree(StrUtil.blankToDefault(configJson, "{}"));
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("数据源配置不是合法 JSON", exception);
		}
	}

	private void copy(WorkSourceSaveDTO request, WorkSourceEntity source) {
		source.setName(request.getName());
		source.setSourceType(request.getSourceType());
		source.setProtocolType(request.getProtocolType().toUpperCase(Locale.ROOT));
		source.setConfigRefId(request.getConfigRefId());
		source.setSecretRef(request.getSecretRef());
		source.setConfigJson(request.getConfigJson());
		source.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
	}

}
