package com.deepdimen.matrix.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageRequest;
import com.deepdimen.matrix.admin.api.entity.SysMessageEntity;
import com.deepdimen.matrix.admin.api.entity.SysMessageRelationEntity;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysMessageMapper;
import com.deepdimen.matrix.admin.mapper.SysMessageRelationMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes module events through the existing framework message tables. */
@Service
@RequiredArgsConstructor
public class SysBusinessMessagePublisher implements BusinessMessagePublisher {

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private com.deepdimen.matrix.admin.mobilepush.MobilePushOutboxServiceImpl pushOutbox;

	private static final String INTERNAL_MESSAGE_CATEGORY = "1";

	private static final int MAX_RECIPIENTS = 100;

	private final SysMessageMapper messageMapper;

	private final SysMessageRelationMapper relationMapper;

	private final SysUserMapper userMapper;

	private final TenantUserDirectory tenantUserDirectory;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long publish(BusinessMessageRequest request) {
		validateRequest(request);
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("缺少租户上下文，无法发布站内消息");
		}

		SysMessageEntity existing = messageMapper.selectOne(Wrappers.<SysMessageEntity>lambdaQuery()
			.eq(SysMessageEntity::getBizKey, request.getBizKey())
			.last("LIMIT 1"));
		if (existing != null) {
			return existing.getId();
		}

		List<Long> recipients = resolveRecipients(request);
		if (recipients.isEmpty()) {
			throw new IllegalArgumentException("站内消息没有有效接收人");
		}

		String priority = normalizePriority(request.getPriority());
		SysMessageEntity message = new SysMessageEntity();
		message.setCategory(INTERNAL_MESSAGE_CATEGORY);
		message.setTitle(plainText(request.getTitle(), 120));
		message.setContent(plainText(request.getContent(), 500));
		message.setSendFlag(YesNoEnum.YES.getCode());
		message.setAllFlag(request.isAllUsers() ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode());
		message.setSort(prioritySort(priority));
		message.setBizKey(request.getBizKey());
		message.setSourceType(identifier(request.getSourceType(), 32, "sourceType"));
		message.setSourceId(bounded(request.getSourceId(), 64));
		message.setEventType(identifier(request.getEventType(), 64, "eventType"));
		message.setPriority(priority);
		message.setActionRequired(Boolean.TRUE.equals(request.getActionRequired()) ? 1 : 0);
		message.setOrgId(request.getOrgId());
		message.setTargetPath(normalizeTargetPath(request.getTargetPath()));
		message.setTargetQuery(request.getTargetQuery() == null || request.getTargetQuery().isEmpty() ? null
				: JSONUtil.toJsonStr(request.getTargetQuery()));
		message.setExpireTime(request.getExpireTime());
		message.setTenantId(tenantId);
		messageMapper.insert(message);

		for (Long userId : recipients) {
			SysMessageRelationEntity relation = new SysMessageRelationEntity();
			relation.setMsgId(message.getId());
			relation.setUserId(userId);
			relation.setReadFlag(YesNoEnum.NO.getCode());
			relation.setTenantId(tenantId);
			relationMapper.insert(relation);
		}
		if (pushOutbox != null)
			pushOutbox.enqueue(tenantId, message.getId());
		return message.getId();
	}

	private List<Long> resolveRecipients(BusinessMessageRequest request) {
		if (request.isAllUsers()) {
			return userMapper
				.selectList(Wrappers.<SysUser>lambdaQuery()
					.eq(SysUser::getDelFlag, "0")
					.eq(SysUser::getLockFlag, "0")
					.orderByAsc(SysUser::getUserId))
				.stream()
				.map(SysUser::getUserId)
				.distinct()
				.toList();
		}

		List<Long> requested = request.getRecipientUserIds() == null ? List.of()
				: request.getRecipientUserIds().stream().filter(id -> id != null).distinct().toList();
		if (requested.isEmpty()) {
			throw new IllegalArgumentException("必须明确指定站内消息接收人");
		}
		if (requested.size() > MAX_RECIPIENTS) {
			throw new IllegalArgumentException("站内消息接收人不能超过" + MAX_RECIPIENTS + "人");
		}
		Long tenantId = TenantContextHolder.getTenantId();
		List<Long> valid = request.getOrgId() == null ? tenantUserDirectory.listAssignableUserIds(tenantId, requested)
				: tenantUserDirectory.listOrganizationUserIds(tenantId, request.getOrgId())
					.stream()
					.filter(requested::contains)
					.toList();
		if (valid.size() != requested.size()) {
			throw new IllegalArgumentException("站内消息包含无效或不可用的接收人");
		}
		return valid;
	}

	private void validateRequest(BusinessMessageRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("站内消息请求不能为空");
		}
		if (StrUtil.isBlank(request.getBizKey()) || request.getBizKey().length() > 190) {
			throw new IllegalArgumentException("业务幂等键不能为空且长度不能超过190");
		}
		if (StrUtil.isBlank(request.getTitle())) {
			throw new IllegalArgumentException("站内消息标题不能为空");
		}
	}

	private String identifier(String value, int maxLength, String field) {
		if (StrUtil.isBlank(value)) {
			throw new IllegalArgumentException(field + "不能为空");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (normalized.length() > maxLength || !normalized.matches("[A-Z0-9_:-]+")) {
			throw new IllegalArgumentException(field + "格式无效");
		}
		return normalized;
	}

	private String normalizePriority(String value) {
		String normalized = StrUtil.blankToDefault(value, "P2").trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "P0", "P1", "P2", "P3" -> normalized;
			default -> "P2";
		};
	}

	private int prioritySort(String priority) {
		return switch (priority) {
			case "P0" -> 100;
			case "P1" -> 80;
			case "P2" -> 50;
			default -> 10;
		};
	}

	private String normalizeTargetPath(String value) {
		if (StrUtil.isBlank(value)) {
			return null;
		}
		String path = value.trim();
		if (!path.startsWith("/") || path.startsWith("//") || path.contains("://") || path.length() > 255) {
			throw new IllegalArgumentException("站内消息目标路径必须是内部路径");
		}
		return path;
	}

	private String plainText(String value, int maxLength) {
		String normalized = Jsoup.parse(StrUtil.blankToDefault(value, "")).text().replaceAll("\\s+", " ").trim();
		return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
	}

	private String bounded(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
	}

}
