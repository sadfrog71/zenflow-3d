package com.deepdimen.matrix.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.admin.api.entity.AiApiTokenEntity;
import com.deepdimen.matrix.admin.service.AiApiTokenService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * API Token 管理接口
 *
 * @author matrix
 * @date 2025-03-12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiApiToken")
@Tag(name = "API Token管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiApiTokenController {

	private final AiApiTokenService aiApiTokenService;

	@GetMapping("/page")
	@Operation(summary = "分页查询 API Token 列表")
	@PreAuthorize("@pms.hasPermission('admin_api_token_view')")
	public R<?> page(Page<AiApiTokenEntity> page) {
		return R.ok(aiApiTokenService.pageTokens(page));
	}

	@SysLog("生成 API Token")
	@PostMapping("/generate")
	@Operation(summary = "生成 API Token")
	@PreAuthorize("@pms.hasPermission('admin_api_token_add')")
	public R<?> generate(@RequestBody Map<String, Object> params) {
		String tokenName = (String) params.get("tokenName");
		if (tokenName == null || tokenName.isBlank()) {
			return R.failed("Token名称不能为空");
		}
		String expireTimeStr = (String) params.get("expireTime");
		LocalDateTime expireTime = null;
		if (expireTimeStr != null) {
			try {
				expireTime = LocalDateTime.parse(expireTimeStr);
			}
			catch (DateTimeParseException e) {
				return R.failed("expireTime 格式错误，请使用 ISO-8601 格式，如：2025-12-31T23:59:59");
			}
		}
		return R.ok(aiApiTokenService.generateToken(tokenName, expireTime));
	}

	@SysLog("禁用 API Token")
	@PostMapping("/{id}/disable")
	@Operation(summary = "禁用 API Token")
	@PreAuthorize("@pms.hasPermission('admin_api_token_edit')")
	public R<?> disable(@PathVariable Long id) {
		aiApiTokenService.disableToken(id);
		return R.ok();
	}

	@SysLog("删除 API Token")
	@DeleteMapping("/{id}")
	@Operation(summary = "删除 API Token")
	@PreAuthorize("@pms.hasPermission('admin_api_token_del')")
	public R<?> delete(@PathVariable Long id) {
		aiApiTokenService.deleteToken(id);
		return R.ok();
	}

}
