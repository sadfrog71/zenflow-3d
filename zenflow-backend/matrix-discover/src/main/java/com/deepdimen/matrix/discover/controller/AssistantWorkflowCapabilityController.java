package com.deepdimen.matrix.discover.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityReleaseEntity;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService.WorkflowCapabilityDetail;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService.WorkflowCapabilityDraftRequest;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService.WorkflowCapabilityInspection;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Admin lifecycle for immutable Workflow Capability releases. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/assistant/workflow-capabilities")
@Tag(description = "assistantWorkflowCapability", name = "统一对话工作流能力")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AssistantWorkflowCapabilityController {

	private final AssistantWorkflowCapabilityService capabilityService;

	private final AssistantIdentityResolver identityResolver;

	@GetMapping
	@Operation(summary = "查询当前租户工作流能力")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<List<WorkflowCapabilityDetail>> list() {
		return R.ok(capabilityService.list(identityResolver.currentIdentity()));
	}

	@GetMapping("/{capabilityId}")
	@Operation(summary = "查询工作流能力及不可变发布历史")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<WorkflowCapabilityDetail> get(@PathVariable Long capabilityId) {
		return R.ok(capabilityService.get(identityResolver.currentIdentity(), capabilityId));
	}

	@GetMapping("/agent/{agentId}/inspect")
	@Operation(summary = "检查当前 Dify Workflow 节点和出域目标")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_view')")
	public R<WorkflowCapabilityInspection> inspect(@PathVariable Long agentId) {
		return R.ok(capabilityService.inspect(identityResolver.currentIdentity(), agentId));
	}

	@PostMapping("/agent/{agentId}")
	@Operation(summary = "保存智能体工作流能力草稿")
	@SysLog("保存统一对话工作流能力草稿")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<AiAssistantWorkflowCapabilityEntity> saveDraft(@PathVariable Long agentId,
			@RequestBody WorkflowCapabilityDraftRequest request) {
		return R.ok(capabilityService.saveDraft(identityResolver.currentIdentity(), agentId, request));
	}

	@PutMapping("/{capabilityId}/publish")
	@Operation(summary = "审核并发布不可变工作流能力版本")
	@SysLog("发布统一对话工作流能力")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<AiAssistantWorkflowCapabilityReleaseEntity> publish(@PathVariable Long capabilityId) {
		return R.ok(capabilityService.publish(identityResolver.currentIdentity(), capabilityId));
	}

	@PutMapping("/{capabilityId}/rollback/{releaseId}")
	@Operation(summary = "回滚到已发布工作流能力版本")
	@SysLog("回滚统一对话工作流能力")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<AiAssistantWorkflowCapabilityReleaseEntity> rollback(@PathVariable Long capabilityId,
			@PathVariable Long releaseId) {
		return R.ok(capabilityService.rollback(identityResolver.currentIdentity(), capabilityId, releaseId));
	}

	@PutMapping("/{capabilityId}/revoke")
	@Operation(summary = "撤销当前工作流能力版本")
	@SysLog("撤销统一对话工作流能力")
	@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
	public R<Boolean> revoke(@PathVariable Long capabilityId) {
		capabilityService.revoke(identityResolver.currentIdentity(), capabilityId);
		return R.ok(Boolean.TRUE);
	}

}
