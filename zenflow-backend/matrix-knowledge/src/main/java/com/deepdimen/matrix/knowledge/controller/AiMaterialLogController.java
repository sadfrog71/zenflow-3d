package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.knowledge.entity.AiMaterialLogEntity;
import com.deepdimen.matrix.knowledge.service.AiMaterialLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI资源记录控制器 管理AI处理过程中产生的资源记录，包括生成的图片、文档等材料的日志管理
 *
 * @author matrix ai
 * @date 2025-05-08 11:06:03
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiMaterialLog")
@Tag(description = "aiMaterialLog", name = "AI资源记录管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiMaterialLogController {

	private final AiMaterialLogService aiMaterialLogService;

	/**
	 * 分页查询AI资源记录 按创建时间倒序排列，支持数据权限过滤
	 * @param page 分页对象
	 * @param aiMaterialLog 查询条件
	 * @return 分页的资源记录列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	public R getAiMaterialLogPage(@ParameterObject Page page, @ParameterObject AiMaterialLogEntity aiMaterialLog) {
		LambdaQueryWrapper<AiMaterialLogEntity> wrapper = Wrappers.lambdaQuery(aiMaterialLog);
		wrapper.orderByDesc(AiMaterialLogEntity::getCreateTime);
		return R.ok(aiMaterialLogService.getPageByScope(page, wrapper));
	}

	/**
	 * 通过ID查询AI资源记录详情 获取单个AI资源的详细信息
	 * @param id 资源记录ID
	 * @return 资源记录详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMaterialLog_view')")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiMaterialLogService.getById(id));
	}

	/**
	 * 新增AI资源记录 记录AI生成的资源信息，如图片、文档等
	 * @param aiMaterialLog 资源记录实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI资源记录", description = "新增AI资源记录")
	@SysLog("新增AI资源记录")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiMaterialLog_add')")
	public R save(@RequestBody AiMaterialLogEntity aiMaterialLog) {
		return R.ok(aiMaterialLogService.save(aiMaterialLog));
	}

	/**
	 * 修改AI资源记录 更新资源记录的相关信息
	 * @param aiMaterialLog 资源记录实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改AI资源记录", description = "修改AI资源记录")
	@SysLog("修改AI资源记录")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiMaterialLog_edit')")
	public R updateById(@RequestBody AiMaterialLogEntity aiMaterialLog) {
		return R.ok(aiMaterialLogService.updateById(aiMaterialLog));
	}

	/**
	 * 批量删除AI资源记录 支持批量删除不需要的资源记录
	 * @param ids 资源记录ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除AI资源记录", description = "通过id删除AI资源记录")
	@SysLog("通过id删除AI资源记录")
	@DeleteMapping
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiMaterialLogService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出资源记录到Excel 将选中的AI资源记录导出为Excel文件
	 * @param aiMaterialLog 查询条件
	 * @param ids 指定要导出的记录ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMaterialLog_export')")
	public List<AiMaterialLogEntity> export(AiMaterialLogEntity aiMaterialLog, Long[] ids) {
		return aiMaterialLogService
			.list(Wrappers.lambdaQuery(aiMaterialLog)
				.in(ArrayUtil.isNotEmpty(ids), AiMaterialLogEntity::getId, CollUtil.toList(ids)));
	}

}
