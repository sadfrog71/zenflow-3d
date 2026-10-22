package com.deepdimen.matrix.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.deepdimen.matrix.admin.api.dto.BusinessMessageSnapshot;
import com.deepdimen.matrix.admin.api.service.BusinessMessageReader;
import com.deepdimen.matrix.admin.mapper.SysMessageMapper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SysBusinessMessageReader implements BusinessMessageReader {

	private final SysMessageMapper messageMapper;

	@Override
	public List<BusinessMessageSnapshot> listUnread(Long tenantId, Long userId, Long orgId, LocalDateTime from,
			int limit) {
		Long currentTenantId = TenantContextHolder.getTenantId();
		if (tenantId == null || !tenantId.equals(currentTenantId) || userId == null || orgId == null) {
			throw new IllegalStateException("消息摘要缺少有效的租户、用户或组织上下文");
		}
		return messageMapper.selectUnreadForDigest(tenantId, userId, orgId, from, Math.max(1, Math.min(limit, 100)));
	}

}
