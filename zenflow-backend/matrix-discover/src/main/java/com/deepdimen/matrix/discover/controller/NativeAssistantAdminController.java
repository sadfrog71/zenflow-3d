package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.assistant.NativeAssistantProvisioningService;
import com.deepdimen.matrix.discover.assistant.NativeAssistantProvisioningService.ProvisionedNativeProfile;
import com.deepdimen.matrix.discover.assistant.NativeAssistantReadinessService;
import com.deepdimen.matrix.discover.assistant.NativeAssistantReadinessService.ReadinessApprovalRequest;
import com.deepdimen.matrix.discover.assistant.NativeAssistantReadinessService.ReadinessDecision;
import com.deepdimen.matrix.discover.entity.AiTenantAgentRouteSwitchAuditEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-scoped native Assistant provisioning, readiness and audited route control. */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean(NativeAssistantProvisioningService.class)
@RequestMapping("/native-assistant")
@PreAuthorize("@pms.hasPermission('knowledge_agent_edit')")
public class NativeAssistantAdminController {

	private final NativeAssistantProvisioningService provisioningService;

	private final NativeAssistantReadinessService readinessService;

	private final AssistantIdentityResolver identityResolver;

	@PostMapping("/profiles")
	public R<ProvisionedNativeProfile> provision(@Valid @RequestBody ProvisionRequest request) {
		return R.ok(provisioningService.provision(identityResolver.currentIdentity(), request.displayName(),
				request.modelId()));
	}

	@PostMapping("/readiness/approve")
	public R<ReadinessDecision> approve(@RequestBody ReadinessApprovalRequest request) {
		return R.ok(readinessService.approve(identityResolver.currentIdentity(), request));
	}

	@PostMapping("/readiness/revoke")
	public R<ReadinessDecision> revoke(@Valid @RequestBody RevokeRequest request) {
		return R.ok(readinessService.revoke(identityResolver.currentIdentity(), request.nativeProfileId(),
				request.reason()));
	}

	@GetMapping("/readiness")
	public R<ReadinessDecision> readiness(@RequestParam Long nativeProfileId) {
		return R.ok(readinessService.current(identityResolver.currentIdentity(), nativeProfileId));
	}

	@PostMapping("/routes/switch")
	public R<AiTenantAgentRouteSwitchAuditEntity> switchRoute(@Valid @RequestBody SwitchRouteRequest request) {
		return R.ok(provisioningService.switchRoute(identityResolver.currentIdentity(), request.nativeProfileId(),
				request.reason()));
	}

	@PostMapping("/routes/rollback")
	public R<Boolean> rollback(@Valid @RequestBody RollbackRequest request) {
		provisioningService.rollbackRoute(identityResolver.currentIdentity(), request.auditId());
		return R.ok(Boolean.TRUE);
	}

	public record ProvisionRequest(String displayName, String modelId) {
	}

	public record RevokeRequest(@NotNull Long nativeProfileId, @NotBlank String reason) {
	}

	public record SwitchRouteRequest(@NotNull Long nativeProfileId, @NotBlank String reason) {
	}

	public record RollbackRequest(@NotNull Long auditId) {
	}

}
