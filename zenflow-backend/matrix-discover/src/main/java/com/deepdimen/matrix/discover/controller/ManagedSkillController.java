package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.service.impl.*;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Explicit managed paths; existing LEGACY handlers and publication remain untouched. */
@RestController
@RequestMapping("/aiSkill")
@RequiredArgsConstructor
public class ManagedSkillController {

	private final ManagedSkillPackageService packages;

	private final ManagedSkillReleaseService releases;

	private final ManagedSkillBindingService bindings;

	private final ManagedSkillCatalogService catalog;

	@GetMapping("/managed/packages")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_view')")
	public R<List<AiSkillPackageEntity>> packages(@RequestParam(required = false) Long skillId,
			@RequestParam(required = false) Long beforeId) {
		return R.ok(catalog.packages(skillId, beforeId));
	}

	@GetMapping("/managed/packages/{id}/preview")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_view')")
	public R<ManagedSkillCatalogService.PackagePreview> preview(@PathVariable Long id) {
		return R.ok(catalog.preview(id));
	}

	@PostMapping(value = "/managed/packages", consumes = "multipart/form-data")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_import')")
	public R<Long> importPackage(@RequestParam("file") MultipartFile file) throws IOException {
		if (file == null || file.isEmpty() || file.getSize() > AgentSkillPackageParser.MAX_ARCHIVE_BYTES)
			throw new IllegalArgumentException("Skill ZIP 必须非空且不超过 10 MiB");
		return R.ok(packages.importPackage(file.getBytes(), file.getOriginalFilename()));
	}

	@PostMapping("/managed/packages/{id}/approve")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_approve')")
	public R<Boolean> approve(@PathVariable Long id) {
		packages.approve(id);
		return R.ok(true);
	}

	@GetMapping("/{id}/releases")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_view')")
	public R<List<AiSkillReleaseEntity>> releases(@PathVariable Long id,
			@RequestParam(required = false) Long beforeId) {
		return R.ok(catalog.releases(id, beforeId));
	}

	@PostMapping("/{id}/releases")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_approve')")
	public R<AiSkillReleaseEntity> publish(@PathVariable Long id, @Valid @RequestBody PublishRequest request) {
		return R.ok(releases.publishForSkill(id, request.packageId(), request.version()));
	}

	@PostMapping("/releases/{id}/revoke")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_disable')")
	public R<Boolean> revoke(@PathVariable Long id, @Valid @RequestBody RevokeRequest request) {
		releases.revoke(id, request.expectedDigest(), request.reason());
		return R.ok(true);
	}

	@GetMapping("/binding-targets")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<List<com.deepdimen.matrix.skill.api.SkillConsumerPolicy.BindingTarget>> targets(
			@RequestParam String consumerType, @RequestParam(required = false) String beforeId) {
		return R.ok(bindings.targets(consumerType, beforeId));
	}

	@PostMapping("/bindings/validate")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<Boolean> validate(@Valid @RequestBody CreateBindingRequest request) {
		bindings.validateDraft(request.consumer(), request.revision().input());
		return R.ok(true);
	}

	@GetMapping("/bindings")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<ManagedSkillBindingService.BindingView> findBinding(@RequestParam String consumerType,
			@RequestParam String consumerId, @RequestParam String slotCode, @RequestParam String bindingKey) {
		return R.ok(bindings.findConsumer(new Consumer(consumerType, consumerId, slotCode, bindingKey)));
	}

	@GetMapping("/bindings/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<ManagedSkillBindingService.BindingView> binding(@PathVariable Long id,
			@RequestParam(required = false) Long beforeId) {
		return R.ok(bindings.detail(id, beforeId));
	}

	@PostMapping("/bindings")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<Long> createBinding(@Valid @RequestBody CreateBindingRequest request) {
		return R.ok(bindings.createDraft(request.consumer(), request.revision().input()));
	}

	@PostMapping("/bindings/{id}/revisions")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<Long> revise(@PathVariable Long id, @Valid @RequestBody ReviseRequest request) {
		return R.ok(bindings.revise(id, request.expectedRevision(), request.revision().input()));
	}

	@PostMapping("/bindings/{id}/activate")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable')")
	public R<Boolean> activate(@PathVariable Long id, @Valid @RequestBody ActivateRequest request) {
		bindings.activate(id, request.revisionId(), request.expectedRevision());
		return R.ok(true);
	}

	public record PublishRequest(@NotNull @Positive Long packageId,
			@NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String version) {
	}

	public record RevokeRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedDigest,
			@NotBlank @Size(max = 1000) String reason) {
	}

	public record RevisionRequest(@NotNull @Positive Long releaseId, @NotNull Boolean enabled,
			@NotBlank @Size(max = 65536) String audienceJson, @NotBlank @Size(max = 64) String activationMode,
			@NotBlank @Size(max = 65536) String parameterJson, @NotBlank @Size(max = 65536) String fallbackPolicy) {
		ManagedSkillBindingService.RevisionInput input() {
			return new ManagedSkillBindingService.RevisionInput(releaseId, enabled, audienceJson, activationMode,
					parameterJson, fallbackPolicy);
		}
	}

	public record CreateBindingRequest(@NotNull Consumer consumer, @NotNull @Valid RevisionRequest revision) {
	}

	public record ReviseRequest(@NotNull @PositiveOrZero Long expectedRevision,
			@NotNull @Valid RevisionRequest revision) {
	}

	public record ActivateRequest(@NotNull @Positive Long revisionId, @NotNull @PositiveOrZero Long expectedRevision) {
	}

}
