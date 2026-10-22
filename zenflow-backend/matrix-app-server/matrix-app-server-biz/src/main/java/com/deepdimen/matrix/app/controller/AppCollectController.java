package com.deepdimen.matrix.app.controller;

import com.deepdimen.matrix.app.api.entity.AiCollectEntity;
import com.deepdimen.matrix.app.service.AiCollectService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用收藏接口
 *
 * @author matrix
 * @date 2025-03-11
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiCollect")
@Tag(description = "aiCollect", name = "通用收藏管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AppCollectController {

	private final AiCollectService aiCollectService;

	@Data
	@Schema(description = "收藏请求")
	public static class CollectRequest {

		@NotBlank(message = "收藏类型不能为空")
		@Pattern(regexp = "agent|article", message = "收藏类型只能为 agent 或 article")
		@Schema(description = "收藏类型（agent/article）", requiredMode = Schema.RequiredMode.REQUIRED)
		private String type;

		@NotNull(message = "目标ID不能为空")
		@Schema(description = "目标ID", requiredMode = Schema.RequiredMode.REQUIRED)
		private Long targetId;

	}

	/**
	 * 收藏
	 * @param request 收藏请求
	 * @return R
	 */
	@Operation(summary = "收藏", description = "收藏智能体或文章")
	@SysLog("收藏")
	@PostMapping
	public R<Void> collect(@Valid @RequestBody CollectRequest request) {
		aiCollectService.collect(request.getType(), request.getTargetId());
		return R.ok();
	}

	/**
	 * 取消收藏
	 * @param targetId 目标ID
	 * @param type 收藏类型
	 * @return R
	 */
	@Operation(summary = "取消收藏", description = "取消收藏智能体或文章")
	@SysLog("取消收藏")
	@DeleteMapping("/{targetId}")
	public R<Void> cancelCollect(@PathVariable Long targetId,
			@NotBlank(message = "收藏类型不能为空") @Pattern(regexp = "agent|article",
					message = "收藏类型只能为 agent 或 article") @RequestParam String type) {
		aiCollectService.cancelCollect(type, targetId);
		return R.ok();
	}

	/**
	 * 查询收藏列表
	 * @param type 收藏类型
	 * @return 收藏列表
	 */
	@Operation(summary = "收藏列表", description = "查询收藏列表，最多500条")
	@GetMapping("/list")
	public R<List<AiCollectEntity>> listCollect(
			@NotBlank(message = "收藏类型不能为空") @Pattern(regexp = "agent|article",
					message = "收藏类型只能为 agent 或 article") @RequestParam String type) {
		return R.ok(aiCollectService.listCollect(type));
	}

	/**
	 * 查询收藏数量
	 * @param type 收藏类型
	 * @return 数量
	 */
	@Operation(summary = "收藏数量", description = "查询收藏数量")
	@GetMapping("/count")
	public R<Long> countCollect(
			@NotBlank(message = "收藏类型不能为空") @Pattern(regexp = "agent|article",
					message = "收藏类型只能为 agent 或 article") @RequestParam String type) {
		return R.ok(aiCollectService.countCollect(type));
	}

}
