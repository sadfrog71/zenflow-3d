package com.deepdimen.matrix.admin.controller;

import com.deepdimen.matrix.admin.api.entity.AiAuthConfigEntity;
import com.deepdimen.matrix.admin.service.AiAuthConfigService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.Inner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 认证配置管理接口
 *
 * @author matrix
 * @date 2026-03-12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiAuthConfig")
@Tag(name = "认证配置管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiAuthConfigController {

	private final AiAuthConfigService aiAuthConfigService;

	/**
	 * 根据租户ID和认证类型查询配置（供 Feign 内部调用）
	 */
	@Inner
	@GetMapping("/getByTenantAndType")
	@Operation(summary = "查询认证配置")
	public R<AiAuthConfigEntity> getByTenantAndType(@RequestParam("tenantId") Long tenantId,
			@RequestParam("authType") String authType) {
		return R.ok(aiAuthConfigService.getByTenantAndType(tenantId, authType));
	}

	@SysLog("保存认证配置")
	@PostMapping
	@Operation(summary = "保存认证配置")
	@PreAuthorize("@pms.hasPermission('admin_auth_config_edit')")
	public R<?> save(@RequestBody AiAuthConfigEntity entity) {
		aiAuthConfigService.saveConfig(entity);
		return R.ok();
	}

	@SysLog("更新认证配置")
	@PutMapping
	@Operation(summary = "更新认证配置")
	@PreAuthorize("@pms.hasPermission('admin_auth_config_edit')")
	public R<?> update(@RequestBody AiAuthConfigEntity entity) {
		aiAuthConfigService.updateConfig(entity);
		return R.ok();
	}

	@SysLog("删除认证配置")
	@DeleteMapping("/{id}")
	@Operation(summary = "删除认证配置")
	@PreAuthorize("@pms.hasPermission('admin_auth_config_edit')")
	public R<?> delete(@PathVariable Long id) {
		aiAuthConfigService.removeById(id);
		return R.ok();
	}

}
