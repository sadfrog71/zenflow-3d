package com.deepdimen.matrix.knowledge.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiConfirmFormDTO;
import com.deepdimen.matrix.knowledge.service.AiFuncService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/**
 * AI功能控制器 提供AI功能的查询和管理接口，用于获取系统支持的各种AI功能列表
 *
 * @author caoyuan
 * @date 2025/06/03
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiFunc")
@Tag(description = "aiFunc", name = "AI功能管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiFuncController {

	private final AiFuncService aiFuncService;

	/**
	 * 获取所有可用的AI功能列表 返回系统中注册的所有AI功能，包括功能名称和描述信息
	 * @return 包含功能名称和功能描述的Map集合
	 */
	@GetMapping("/functions")
	public R getAllFunction() {
		return aiFuncService.listFunctionCalling();
	}

	/**
	 * 提交功能
	 * @param confirmFormDTO 确认表单DTO对象
	 * @return 操作结果
	 */
	@PostMapping("/submit")
	public R submitFunction(@RequestBody AiConfirmFormDTO confirmFormDTO) {
		return aiFuncService.submitFunction(confirmFormDTO);
	}

}
