package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageRequest;
import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.dto.InformationPublishRequest;
import com.deepdimen.matrix.work.api.entity.InformationDelivery;
import com.deepdimen.matrix.work.api.entity.InformationItem;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import com.deepdimen.matrix.work.mapper.InformationDeliveryMapper;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;
import com.deepdimen.matrix.work.mapper.InformationOrganizationMapper;
import com.deepdimen.matrix.work.mapper.InformationPolicyMapper;
import com.deepdimen.matrix.work.mapper.InformationRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InformationAdminService {

	private final InformationItemMapper itemMapper;

	private final InformationDeliveryMapper deliveryMapper;

	private final InformationPolicyMapper policyMapper;

	private final InformationOrganizationMapper organizationMapper;

	private final InformationRunMapper runMapper;

	private final ObjectProvider<BusinessMessagePublisher> messagePublisherProvider;

	private final TenantUserDirectory tenantUserDirectory;

	public IPage<InformationItem> itemPage(Page<InformationItem> page, InformationItem query) {
		return itemMapper.selectPage(page,
				Wrappers.<InformationItem>lambdaQuery()
					.eq(query.getContentType() != null, InformationItem::getContentType, query.getContentType())
					.eq(query.getReviewStatus() != null, InformationItem::getReviewStatus, query.getReviewStatus())
					.eq(query.getPublishStatus() != null, InformationItem::getPublishStatus, query.getPublishStatus())
					.like(query.getTitle() != null && !query.getTitle().isBlank(), InformationItem::getTitle,
							query.getTitle())
					.orderByDesc(InformationItem::getCollectedAt));
	}

	public IPage<InformationPolicy> policyPage(Page<InformationPolicy> page) {
		return policyMapper.selectPage(page,
				Wrappers.<InformationPolicy>lambdaQuery().orderByDesc(InformationPolicy::getUpdateTime));
	}

	public IPage<InformationRun> runPage(Page<InformationRun> page, Long policyId) {
		return runMapper.selectPage(page,
				Wrappers.<InformationRun>lambdaQuery()
					.eq(policyId != null, InformationRun::getPolicyId, policyId)
					.orderByDesc(InformationRun::getStartedAt));
	}

	@Transactional(rollbackFor = Exception.class)
	public InformationPolicy savePolicy(InformationPolicy policy) {
		validateOrganization(policy.getOrgId());
		policy.setProvider("TAVILY");
		policy.setTopic("general".equalsIgnoreCase(policy.getTopic()) ? "general" : "news");
		policy.setTimeRange(normalizeTimeRange(policy.getTimeRange()));
		policy.setDailyLimit(Math.max(1, Math.min(10, policy.getDailyLimit() == null ? 10 : policy.getDailyLimit())));
		policy.setEnabled(policy.getEnabled() == null ? 1 : policy.getEnabled());
		LocalDateTime now = LocalDateTime.now();
		if (policy.getId() == null) {
			InformationSkillPolicyState.prepareSave(policy, null);
			policy.setCreateTime(now);
			policy.setUpdateTime(now);
			policy.setDelFlag("0");
			policyMapper.insert(policy);
		}
		else {
			var old = policyMapper.selectById(policy.getId());
			InformationSkillPolicyState.prepareSave(policy, old);
			policy.setUpdateTime(now);
			if (policyMapper.update(policy,
					Wrappers.<InformationPolicy>lambdaUpdate()
						.eq(InformationPolicy::getId, policy.getId())
						.eq(InformationPolicy::getSkillPolicyVersion, old.getSkillPolicyVersion())
						.set(InformationPolicy::getSkillPolicyVersion, policy.getSkillPolicyVersion())) != 1)
				throw new IllegalArgumentException("INFORMATION_POLICY_VERSION_CONFLICT");
		}
		return policy;
	}

	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> publish(InformationPublishRequest request) {
		if (request.getItemId() == null || request.getOrgIds() == null || request.getOrgIds().isEmpty()) {
			throw new IllegalArgumentException("必须明确选择当前租户内的发布组织");
		}
		InformationItem item = itemMapper.selectById(request.getItemId());
		if (item == null || !"APPROVED".equals(item.getReviewStatus())) {
			throw new IllegalStateException("只有通过双阶段审核的资讯才能发布");
		}
		LocalDate today = LocalDate.now();
		int published = 0;
		Map<Long, String> skipped = new LinkedHashMap<>();
		for (Long orgId : request.getOrgIds().stream().distinct().toList()) {
			validateOrganization(orgId);
			if (deliveryMapper.countPublishedOnDate(orgId, today) >= 30) {
				skipped.put(orgId, "当日已达30条上限");
				continue;
			}
			InformationPolicy sourcePolicy = item.getSourcePolicyId() == null ? null
					: policyMapper.selectById(item.getSourcePolicyId());
			int sourceLimit = sourcePolicy == null || sourcePolicy.getDailyLimit() == null ? 10
					: Math.max(1, Math.min(10, sourcePolicy.getDailyLimit()));
			if (item.getSourcePolicyId() != null && deliveryMapper.countPolicyPublishedOnDate(orgId,
					item.getSourcePolicyId(), today) >= sourceLimit) {
				skipped.put(orgId, "该数据源当日已达" + sourceLimit + "条上限");
				continue;
			}
			long exists = deliveryMapper.selectCount(Wrappers.<InformationDelivery>lambdaQuery()
				.eq(InformationDelivery::getItemId, item.getId())
				.eq(InformationDelivery::getOrgId, orgId)
				.eq(InformationDelivery::getDeliveryDate, today));
			if (exists > 0) {
				skipped.put(orgId, "当日已发布");
				continue;
			}
			InformationDelivery delivery = new InformationDelivery();
			delivery.setItemId(item.getId());
			delivery.setOrgId(orgId);
			delivery.setDeliveryDate(today);
			delivery.setSortOrder(deliveryMapper.countPublishedOnDate(orgId, today) + 1);
			delivery.setStatus("PUBLISHED");
			delivery.setPublishedAt(LocalDateTime.now());
			delivery.setCreateTime(LocalDateTime.now());
			deliveryMapper.insert(delivery);
			publishDailyDigest(orgId, today);
			published++;
		}
		if (published > 0) {
			item.setPublishStatus("PUBLISHED");
			itemMapper.updateById(item);
		}
		return Map.of("published", published, "skipped", skipped);
	}

	private void publishDailyDigest(Long orgId, LocalDate date) {
		Long tenantId = TenantContextHolder.getTenantId();
		List<Long> recipients = tenantUserDirectory.listOrganizationUserIds(tenantId, orgId);
		if (recipients.isEmpty()) {
			return;
		}
		BusinessMessagePublisher publisher = messagePublisherProvider.getIfAvailable();
		if (publisher == null) {
			return;
		}
		publisher.publish(BusinessMessageRequest.builder()
			.bizKey("INFORMATION:DIGEST:" + orgId + ":" + date)
			.sourceType("INFORMATION")
			.sourceId(orgId + ":" + date)
			.eventType("INFORMATION_DAILY_DIGEST")
			.title("今日 AI 资讯已更新")
			.content("新的行业案例与项目信息已进入信息箱。")
			.priority("P3")
			.actionRequired(false)
			.orgId(orgId)
			.targetPath("/todo")
			.targetQuery(Map.of("focus", "information", "orgId", String.valueOf(orgId), "date", date.toString()))
			.recipientUserIds(recipients)
			.build());
	}

	@Transactional(rollbackFor = Exception.class)
	public void withdraw(InformationPublishRequest request) {
		if (request.getItemId() == null || request.getOrgIds() == null || request.getOrgIds().isEmpty()) {
			throw new IllegalArgumentException("撤回必须明确选择组织，不支持全租户撤回");
		}
		List<Long> orgIds = request.getOrgIds().stream().distinct().toList();
		orgIds.forEach(this::validateOrganization);
		InformationDelivery delivery = new InformationDelivery();
		delivery.setStatus("WITHDRAWN");
		delivery.setWithdrawnAt(LocalDateTime.now());
		delivery.setUpdateTime(LocalDateTime.now());
		deliveryMapper.update(delivery,
				Wrappers.<InformationDelivery>lambdaUpdate()
					.eq(InformationDelivery::getItemId, request.getItemId())
					.in(InformationDelivery::getOrgId, orgIds)
					.eq(InformationDelivery::getStatus, "PUBLISHED"));
		long remaining = deliveryMapper.selectCount(Wrappers.<InformationDelivery>lambdaQuery()
			.eq(InformationDelivery::getItemId, request.getItemId())
			.eq(InformationDelivery::getStatus, "PUBLISHED"));
		if (remaining == 0) {
			InformationItem item = new InformationItem();
			item.setId(request.getItemId());
			item.setPublishStatus("DRAFT");
			itemMapper.updateById(item);
		}
	}

	private void validateOrganization(Long orgId) {
		if (orgId == null || organizationMapper.countActiveOrganization(orgId) == 0) {
			throw new IllegalArgumentException("组织不存在或不属于当前租户");
		}
	}

	private String normalizeTimeRange(String value) {
		if (value == null || value.isBlank()) {
			return "month";
		}
		return switch (value.toLowerCase()) {
			case "day", "week", "month", "year", "all" -> value.toLowerCase();
			default -> "month";
		};
	}

}
