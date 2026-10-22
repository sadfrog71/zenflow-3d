package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.discover.service.AiSkillPackageService;
import com.deepdimen.matrix.discover.service.AiSkillService;
import com.deepdimen.matrix.discover.vo.AiSkillAdminVO;
import com.deepdimen.matrix.discover.vo.AiSkillImportVO;
import com.deepdimen.matrix.discover.vo.AiSkillVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 技能目录控制器
 *
 * @author matrix
 * @date 2026-04-25
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiSkill")
@Tag(description = "aiSkill", name = "技能目录")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiSkillController {

	private final AiSkillService aiSkillService;

	private final AiSkillPackageService aiSkillPackageService;

	@Operation(summary = "获取可用技能列表")
	@GetMapping("/list")
	public R<List<AiSkillVO>> list() {
		return R.ok(aiSkillService.listAvailableSkills());
	}

	@Operation(summary = "分页查询 Skill 管理库")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_view', 'knowledge_agent_view')")
	public R<Page<AiSkillAdminVO>> page(@ParameterObject Page<AiSkillAdminVO> page,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String packageStatus) {
		return R.ok(aiSkillPackageService.page(page, keyword, packageStatus));
	}

	@Operation(summary = "调整 Skill 显示顺序")
	@PutMapping("/order")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_import', 'knowledge_agent_edit')")
	public R<Boolean> reorder(@RequestBody List<Long> skillIds) {
		aiSkillPackageService.reorderSkills(skillIds);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "导入 Agent Skills ZIP 包")
	@PostMapping(value = "/import", consumes = "multipart/form-data")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_import', 'knowledge_agent_add')")
	public R<AiSkillImportVO> importPackage(@RequestParam("file") MultipartFile file) {
		return R.ok(aiSkillPackageService.importPackage(file));
	}

	@Operation(summary = "更新 Agent Skills ZIP 包")
	@PostMapping(value = "/{id}/update", consumes = "multipart/form-data")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_import', 'knowledge_agent_edit')")
	public R<AiSkillImportVO> updatePackage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
		return R.ok(aiSkillPackageService.updatePackage(id, file));
	}

	@Operation(summary = "审核并启用 Skill")
	@PostMapping("/{id}/approve")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_approve', 'knowledge_agent_edit')")
	public R<Boolean> approve(@PathVariable Long id) {
		aiSkillPackageService.approve(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "停用 Skill")
	@PostMapping("/{id}/disable")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_disable', 'knowledge_agent_edit')")
	public R<Boolean> disable(@PathVariable Long id) {
		aiSkillPackageService.disable(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "下架 Skill")
	@PostMapping("/{id}/unpublish")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_disable', 'knowledge_agent_edit')")
	public R<Boolean> unpublish(@PathVariable Long id) {
		aiSkillPackageService.unpublish(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "重新启用 Skill")
	@PostMapping("/{id}/enable")
	// 兼容已缓存的角色权限：审核员天然具备把已审核通过包重新投入运行的权限；
	// 新角色则可只授予 knowledge_skill_enable。
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable', 'knowledge_skill_approve', 'knowledge_agent_edit')")
	public R<Boolean> enable(@PathVariable Long id) {
		aiSkillPackageService.enable(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "上架 Skill")
	@PostMapping("/{id}/publish")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_enable', 'knowledge_skill_approve', 'knowledge_agent_edit')")
	public R<Boolean> publish(@PathVariable Long id) {
		aiSkillPackageService.publish(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "删除自定义 Skill")
	@DeleteMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_delete', 'knowledge_agent_del')")
	public R<Boolean> delete(@PathVariable Long id) {
		aiSkillPackageService.delete(id);
		return R.ok(Boolean.TRUE);
	}

	@Operation(summary = "下载不可变 Skill ZIP 包")
	@GetMapping("/packages/{packageId}/download")
	@PreAuthorize("@pms.hasPermission('knowledge_skill_view', 'knowledge_agent_view')")
	public ResponseEntity<byte[]> download(@PathVariable Long packageId) {
		AiSkillPackageService.SkillPackageDownload download = aiSkillPackageService.download(packageId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("application/zip"))
			.contentLength(download.size())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
			.header(HttpHeaders.ETAG, "\"sha256:" + download.sha256() + "\"")
			.header("X-Content-SHA256", download.sha256())
			.body(download.content());
	}

}
