package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiPosterDTO;
import com.deepdimen.matrix.knowledge.entity.AiPosterEntity;
import com.deepdimen.matrix.knowledge.service.AiPosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI海报生成控制器 管理AI海报模板和海报生成功能，支持使用AI自动生成海报内容
 *
 * @author matrix
 * @date 2025-04-04 14:25:49
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiPoster")
@Tag(description = "aiPoster", name = "AI海报模板表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiPosterController {

	private final AiPosterService aiPosterService;

	/**
	 * 查询AI海报模板列表 支持按模板名称模糊查询所有可用的海报模板
	 * @param aiPoster 查询条件，包含模板名称等筛选参数
	 * @return 海报模板列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/list")
	public R getAiPosterPage(@ParameterObject AiPosterEntity aiPoster) {
		return R.ok(aiPosterService.list(Wrappers.<AiPosterEntity>lambdaQuery()
			.like(StrUtil.isNotBlank(aiPoster.getTemplateName()), AiPosterEntity::getTemplateName,
					aiPoster.getTemplateName())));
	}

	/**
	 * 通过ID查询AI海报模板详情 获取单个海报模板的完整信息
	 * @param id 模板ID
	 * @return 海报模板详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiPoster_view')")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiPosterService.getById(id));
	}

	/**
	 * 新增AI海报模板 创建新的海报模板配置
	 * @param aiPoster 海报模板实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI海报模板表", description = "新增AI海报模板表")
	@SysLog("新增AI海报模板表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiPoster_add')")
	public R save(@RequestBody AiPosterEntity aiPoster) {
		return R.ok(aiPosterService.save(aiPoster));
	}

	/**
	 * 修改AI海报模板 更新现有的海报模板信息
	 * @param aiPoster 海报模板实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改AI海报模板表", description = "修改AI海报模板表")
	@SysLog("修改AI海报模板表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiPoster_edit')")
	public R updateById(@RequestBody AiPosterEntity aiPoster) {
		return R.ok(aiPosterService.updateById(aiPoster));
	}

	/**
	 * 批量删除AI海报模板 支持批量删除不再使用的海报模板
	 * @param ids 模板ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除AI海报模板表", description = "通过id删除AI海报模板表")
	@SysLog("通过id删除AI海报模板表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiPoster_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiPosterService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出海报模板到Excel 将选中的海报模板导出为Excel文件
	 * @param aiPoster 查询条件
	 * @param ids 指定要导出的模板ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiPoster_export')")
	public List<AiPosterEntity> export(AiPosterEntity aiPoster, Long[] ids) {
		return aiPosterService
			.list(Wrappers.lambdaQuery(aiPoster)
				.in(ArrayUtil.isNotEmpty(ids), AiPosterEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * AI生成海报 根据模板和输入参数，使用AI自动生成海报内容
	 * @param posterDTO 海报生成参数，包含模板ID、文案要求等
	 * @return 流式返回AI生成的海报内容
	 */
	@Operation(summary = "生成海报", description = "AI生成海报")
	@SysLog("生成海报")
	@PostMapping("/generate")
	@HasPermission("knowledge_ocr_add")
	public Flux<AiMessageResultDTO> generate(@RequestBody AiPosterDTO posterDTO) {
		return aiPosterService.generatePoster(posterDTO);
	}

}
