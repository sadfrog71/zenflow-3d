package com.deepdimen.matrix.discover.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.admin.api.vo.AgentShareValidationVO;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.discover.dto.AiAgentDTO;
import com.deepdimen.matrix.discover.dto.AiAgentImportDTO;
import com.deepdimen.matrix.discover.dto.AiAgentPermissionDTO;
import com.deepdimen.matrix.discover.dto.AiAgentSkillDTO;
import com.deepdimen.matrix.discover.dto.AiAgentVersionGrayDTO;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.AiAgentVersionService;
import com.deepdimen.matrix.discover.service.AiUserSkillPrefService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.deepdimen.matrix.discover.vo.AiAgentEnterpriseRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentMarketVO;
import com.deepdimen.matrix.discover.vo.AiAgentPermissionVO;
import com.deepdimen.matrix.discover.vo.AiAgentRankVO;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;
import com.deepdimen.matrix.discover.vo.AiAgentStatisticsVO;
import com.deepdimen.matrix.discover.vo.AiAgentVersionVO;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.deepdimen.matrix.discover.vo.AiUserTopAgentVO;
import com.deepdimen.matrix.discover.vo.MarketOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.deepdimen.matrix.discover.dto.validation.CreateGroup;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能体管理控制器
 *
 * @author matrix
 * @date 2025-03-11
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiAgent")
@Tag(description = "aiAgent", name = "智能体管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiAgentController {

	private final AiAgentService aiAgentService;

	private final AiAgentPermissionService aiAgentPermissionService;

	private final AiAgentVersionService aiAgentVersionService;

	private final AiAgentSkillService aiAgentSkillService;

	private final AiUserSkillPrefService aiUserSkillPrefService;

	private final TenantAgentRouteService tenantAgentRouteService;

	private final AssistantIdentityResolver assistantIdentityResolver;

	@Operation(summary = "分页查询智能体列表")
	@GetMapping("/page")
	public R<Page<AiAgentVO>> page(@ParameterObject Page<AiAgentEntity> page, @ParameterObject AiAgentDTO agentDTO) {
		return R.ok(aiAgentService.pageAgent(page, agentDTO));
	}

	@Operation(summary = "查询当前租户智能体标签建议")
	@GetMapping("/tags")
	public R<List<String>> listTags(@RequestParam(required = false, defaultValue = "false") boolean marketOnly) {
		return R.ok(aiAgentService.listTagSuggestions(marketOnly));
	}

	@Operation(summary = "查询智能体详情")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<AiAgentVO> getById(@PathVariable Long id) {
		if (AiAgentService.DEFAULT_AGENT_ID.equals(id)) {
			Long routedAgentId = tenantAgentRouteService
				.resolve(assistantIdentityResolver.currentIdentity(), TenantAgentRouteService.UNIFIED_ASSISTANT)
				.agent()
				.getId();
			if (!id.equals(routedAgentId)) {
				throw new AccessDeniedException("该系统智能体不是当前租户已发布的默认路由");
			}
		}
		return R.ok(aiAgentService.getAgentDetail(id));
	}

	@Operation(summary = "按智能体ID查询所属租户ID(内部调用,供share免密登录定位租户)")
	@Inner
	@GetMapping("/tenant-id")
	public R<String> getTenantIdByAgentId(@RequestParam("agentId") Long agentId) {
		return R.ok(aiAgentService.getTenantIdByAgentId(agentId));
	}

	@Operation(summary = "校验智能体是否可用于分享登录")
	@Inner
	@GetMapping("/share-validation")
	public R<AgentShareValidationVO> validateShareAgent(@RequestParam("agentId") Long agentId) {
		return R.ok(aiAgentService.validateShareAgent(agentId));
	}

	@Operation(summary = "新增智能体")
	@SysLog("新增智能体")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_agent_add')")
	public R<AiAgentVO> save(@Validated(CreateGroup.class) @RequestBody AiAgentDTO agentDTO) {
		return R.ok(aiAgentService.createAgent(agentDTO));
	}

	@Operation(summary = "导入Dify DSL创建智能体")
	@SysLog("导入Dify DSL创建智能体")
	@PostMapping("/import/dify")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_add')")
	public R<AiAgentVO> importDify(@Valid @RequestBody AiAgentImportDTO importDTO) {
		return R.ok(aiAgentService.importDifyAgent(importDTO));
	}

	@Operation(summary = "查询当前租户可关联的 Dify 应用列表")
	@GetMapping("/dify/available-apps")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_add')")
	public R<List<Map<String, Object>>> listAvailableDifyApps(
			@RequestParam(value = "keyword", required = false) String keyword) {
		return R.ok(aiAgentService.listAvailableDifyApps(keyword));
	}

	@Operation(summary = "修改智能体")
	@SysLog("修改智能体")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> update(@Valid @RequestBody AiAgentDTO agentDTO) {
		aiAgentService.updateAgent(agentDTO);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "删除智能体")
	@SysLog("删除智能体")
	@DeleteMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_del')")
	public R<Boolean> removeById(@PathVariable Long id) {
		return R.ok(aiAgentService.removeById(id));
	}

	@Operation(summary = "外部智能体连接测试（仅 source=external）")
	@SysLog("外部智能体连接测试")
	@PostMapping("/{id}/test")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Map<String, Object>> testExternalConnection(@PathVariable Long id) {
		return R.ok(aiAgentService.testExternalConnection(id));
	}

	@Operation(summary = "获取外部智能体 iframe URL（后端拼接，仅 source=external）")
	@GetMapping("/{id}/iframe-url")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<Map<String, String>> getExternalIframeUrl(@PathVariable Long id) {
		return R.ok(aiAgentService.getExternalIframeUrl(id));
	}

	@Operation(summary = "发布智能体")
	@SysLog("发布智能体")
	@PutMapping("/publish/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> publish(@PathVariable Long id) {
		aiAgentService.publishAgent(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "校验智能体发布契约")
	@PostMapping("/{id}/release/validate")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Map<String, Object>> validateRelease(@PathVariable Long id) {
		aiAgentSkillService.validateForPublish(id);
		return R.ok(Map.of("valid", true, "agentId", id, "skillBindings", aiAgentSkillService.getByAgentId(id)));
	}

	@Operation(summary = "禁用智能体")
	@SysLog("禁用智能体")
	@PutMapping("/disable/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> disable(@PathVariable Long id) {
		aiAgentService.disableAgent(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "启用智能体")
	@SysLog("启用智能体")
	@PutMapping("/enable/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> enable(@PathVariable Long id) {
		aiAgentService.enableAgent(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "查询智能体权限配置")
	@GetMapping("/{id}/permission")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<AiAgentPermissionVO> getPermission(@PathVariable Long id) {
		return R.ok(aiAgentPermissionService.getPermission(id));
	}

	@Operation(summary = "设置智能体权限")
	@SysLog("设置智能体权限")
	@PostMapping("/{id}/permission")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> setPermission(@PathVariable Long id, @Valid @RequestBody AiAgentPermissionDTO dto) {
		aiAgentPermissionService.setPermission(id, dto);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "查询智能体版本列表")
	@GetMapping("/{id}/versions")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<List<AiAgentVersionVO>> listVersions(@PathVariable Long id) {
		return R.ok(aiAgentVersionService.listVersions(id));
	}

	@Operation(summary = "版本对比")
	@GetMapping("/{id}/version/compare")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<Map<String, String>> compareVersions(@PathVariable Long id, @RequestParam String v1,
			@RequestParam String v2) {
		return R.ok(aiAgentVersionService.compareVersions(id, v1, v2));
	}

	@Operation(summary = "版本回滚")
	@SysLog("版本回滚")
	@PostMapping("/{id}/version/{versionId}/rollback")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> rollback(@PathVariable Long id, @PathVariable Long versionId) {
		aiAgentVersionService.rollback(id, versionId);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "配置灰度规则")
	@SysLog("配置灰度规则")
	@PostMapping("/{id}/version/{versionId}/gray")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> setGrayConfig(@PathVariable Long id, @PathVariable Long versionId,
			@RequestBody AiAgentVersionGrayDTO dto) {
		aiAgentVersionService.setGrayConfig(id, versionId, dto);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "市场智能体列表")
	@GetMapping("/market/list")
	public R<Page<AiAgentMarketVO>> marketList(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String category, @RequestParam(required = false) String source,
			@RequestParam(required = false) String tag, @RequestParam(required = false) String excludeTag,
			@RequestParam(required = false) Boolean accessible,
			@RequestParam(required = false, defaultValue = "default") String sortBy,
			@RequestParam(required = false, defaultValue = "1") int pageNum,
			@RequestParam(required = false, defaultValue = "20") int pageSize,
			@RequestParam(required = false, defaultValue = "false") boolean favoriteOnly) {
		return R.ok(aiAgentService.marketList(keyword, category, source, tag, excludeTag, accessible, sortBy, pageNum, pageSize,
				favoriteOnly));
	}

	@Operation(summary = "市场统计概览")
	@GetMapping("/market/overview")
	public R<MarketOverviewVO> marketOverview(@RequestParam(required = false) String excludeTag) {
		return R.ok(aiAgentService.marketOverview(excludeTag));
	}

	@Operation(summary = "智能体概况统计")
	@GetMapping("/statistics")
	public R<AiAgentStatisticsVO> statistics() {
		return R.ok(aiAgentService.statistics());
	}

	@Operation(summary = "企业排行榜")
	@GetMapping("/rank/enterprise")
	@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
	public R<List<AiAgentEnterpriseRankVO>> enterpriseRank(
			@RequestParam(required = false, defaultValue = "satisfaction") String sortBy) {
		return R.ok(aiAgentService.enterpriseRank(sortBy));
	}

	@Operation(summary = "智能体排行Top10")
	@GetMapping("/rank")
	public R<List<AiAgentRankVO>> rankList(@RequestParam(required = false, defaultValue = "calls") String sortBy,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		return R.ok(aiAgentService.rankList(sortBy, limit));
	}

	@Operation(summary = "当前用户常用智能体")
	@GetMapping("/user/topAgents")
	public R<List<AiUserTopAgentVO>> userTopAgents(@RequestParam(required = false, defaultValue = "3") int limit) {
		return R.ok(aiAgentService.userTopAgents(limit));
	}

	@Operation(summary = "获取智能体绑定的技能列表")
	@GetMapping("/{id}/skills")
	public R<List<AiAgentSkillVO>> getAgentSkills(@PathVariable Long id) {
		return R.ok(aiAgentSkillService.getByAgentId(id));
	}

	@Operation(summary = "更新智能体技能关联（全量替换）")
	@SysLog("更新智能体技能关联")
	@PutMapping("/{id}/skills")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> updateAgentSkills(@PathVariable Long id, @Valid @RequestBody List<AiAgentSkillDTO> skills) {
		aiAgentSkillService.saveAgentSkills(id, skills);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "查询当前用户的技能偏好")
	@GetMapping("/{id}/skills/user-pref")
	public R<List<Map<String, Object>>> getUserSkillPrefs(@PathVariable Long id) {
		return R.ok(aiUserSkillPrefService.getByUserAndAgent(id));
	}

	@Operation(summary = "保存当前用户的技能偏好（全量替换）")
	@PutMapping("/{id}/skills/user-pref")
	public R<Boolean> saveUserSkillPrefs(@PathVariable Long id, @RequestBody List<Map<String, Object>> prefs) {
		aiUserSkillPrefService.saveUserPrefs(id, prefs);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "查询智能体已发布 Skill 治理信息")
	@GetMapping("/{id}/skill-releases")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<List<AiAgentSkillVO>> listSkillReleases(@PathVariable Long id) {
		return R.ok(aiAgentSkillService.listReleaseGovernance(id));
	}

	@Operation(summary = "配置 Skill 发布灰度")
	@SysLog("配置 Skill 发布灰度")
	@PostMapping("/{id}/skill-releases/{releaseVersion}/gray")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> setSkillReleaseGray(@PathVariable Long id, @PathVariable String releaseVersion,
			@Valid @RequestBody AiAgentVersionGrayDTO dto) {
		aiAgentSkillService.setReleaseGray(id, releaseVersion, dto);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "停用 Skill 发布版本")
	@SysLog("停用 Skill 发布版本")
	@PostMapping("/{id}/skill-releases/{releaseVersion}/disable")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Map<String, Object>> disableSkillRelease(@PathVariable Long id, @PathVariable String releaseVersion) {
		return R.ok(aiAgentSkillService.disableRelease(id, releaseVersion));
	}

	@Operation(summary = "回滚 Skill 发布版本")
	@SysLog("回滚 Skill 发布版本")
	@PostMapping("/{id}/skill-releases/{releaseVersion}/rollback")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> rollbackSkillRelease(@PathVariable Long id, @PathVariable String releaseVersion) {
		aiAgentSkillService.rollbackRelease(id, releaseVersion);
		return R.ok(Boolean.TRUE);
	}

}
