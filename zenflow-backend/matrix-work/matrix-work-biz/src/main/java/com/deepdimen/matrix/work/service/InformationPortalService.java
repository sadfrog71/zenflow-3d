package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.api.dto.InformationPageQuery;
import com.deepdimen.matrix.work.api.entity.InformationUserState;
import com.deepdimen.matrix.work.api.vo.InformationCardVO;
import com.deepdimen.matrix.work.api.vo.InformationDetailVO;
import com.deepdimen.matrix.work.api.vo.InformationStatsVO;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;
import com.deepdimen.matrix.work.mapper.InformationUserStateMapper;
import com.deepdimen.matrix.work.security.CurrentOrganizationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class InformationPortalService {

	private final InformationItemMapper itemMapper;
	private final InformationUserStateMapper stateMapper;
	private final CurrentOrganizationResolver organizationResolver;

	public IPage<InformationCardVO> page(InformationPageQuery query) {
		long current = Math.max(1, query.getCurrent());
		long size = Math.max(1, Math.min(10, query.getSize()));
		query.setCurrent(current);
		query.setSize(size);
		return itemMapper.selectPortalPage(new Page<>(current, size), requireTenantId(), organizationResolver.resolve(),
				SecurityUtils.getUser().getId(), query);
	}

	public InformationDetailVO detail(Long itemId) {
		Long orgId = organizationResolver.resolve();
		Long userId = SecurityUtils.getUser().getId();
		InformationDetailVO detail = itemMapper.selectPortalDetail(itemId, requireTenantId(), orgId, userId);
		if (detail == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return detail;
	}

	public InformationStatsVO stats() {
		InformationStatsVO stats = itemMapper.selectPortalStats(requireTenantId(), organizationResolver.resolve(),
				SecurityUtils.getUser().getId());
		return stats == null ? new InformationStatsVO() : stats;
	}

	@Transactional(rollbackFor = Exception.class)
	public void markRead(Long itemId) {
		detail(itemId);
		mutateState(itemId, state -> {
			state.setReadFlag(1);
			state.setReadAt(LocalDateTime.now());
		});
	}

	@Transactional(rollbackFor = Exception.class)
	public void favorite(Long itemId, boolean favorite) {
		detail(itemId);
		mutateState(itemId, state -> {
			state.setFavoriteFlag(favorite ? 1 : 0);
			state.setFavoriteAt(favorite ? LocalDateTime.now() : null);
		});
	}

	@Transactional(rollbackFor = Exception.class)
	public void ignore(Long itemId) {
		detail(itemId);
		mutateState(itemId, state -> {
			state.setIgnoredFlag(1);
			state.setIgnoredAt(LocalDateTime.now());
		});
	}

	private void mutateState(Long itemId, Consumer<InformationUserState> mutation) {
		Long orgId = organizationResolver.resolve();
		Long userId = SecurityUtils.getUser().getId();
		InformationUserState state = stateMapper.selectOne(Wrappers.<InformationUserState>lambdaQuery()
				.eq(InformationUserState::getItemId, itemId)
				.eq(InformationUserState::getOrgId, orgId)
				.eq(InformationUserState::getUserId, userId));
		LocalDateTime now = LocalDateTime.now();
		if (state == null) {
			state = new InformationUserState();
			state.setItemId(itemId);
			state.setOrgId(orgId);
			state.setUserId(userId);
			state.setReadFlag(0);
			state.setFavoriteFlag(0);
			state.setIgnoredFlag(0);
			state.setCreateTime(now);
			mutation.accept(state);
			state.setUpdateTime(now);
			stateMapper.insert(state);
		}
		else {
			mutation.accept(state);
			state.setUpdateTime(now);
			stateMapper.updateById(state);
		}
	}

	private Long requireTenantId() {
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少租户上下文");
		}
		return tenantId;
	}
}
