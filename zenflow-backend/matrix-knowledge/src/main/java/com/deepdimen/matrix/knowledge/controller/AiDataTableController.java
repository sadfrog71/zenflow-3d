package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.knowledge.entity.AiDataTableEntity;
import com.deepdimen.matrix.knowledge.service.AiDataTableService;
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
 * AI数据表管理控制器 用于管理系统中的数据表信息，支持数据表的同步、查询和导出等功能
 *
 * @author matrix
 * @date 2025-03-26 21:48:16
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiDataTable")
@Tag(description = "aiDataTable", name = "AI数据表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiDataTableController {

	private final AiDataTableService aiDataTableService;

	/**
	 * 分页查询数据表信息 支持按数据源名称、表名和表注释进行模糊查询
	 * @param page 分页对象
	 * @param aiDataTable AI数据表查询条件
	 * @return 分页后的数据表列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_view')")
	public R getAiDataTablePage(@ParameterObject Page page, @ParameterObject AiDataTableEntity aiDataTable) {
		LambdaQueryWrapper<AiDataTableEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(StrUtil.isNotBlank(aiDataTable.getDsName()), AiDataTableEntity::getDsName, aiDataTable.getDsName())
			.like(StrUtil.isNotBlank(aiDataTable.getTableName()), AiDataTableEntity::getTableName,
					aiDataTable.getTableName())
			.or()
			.like(StrUtil.isNotBlank(aiDataTable.getTableComment()), AiDataTableEntity::getTableComment,
					aiDataTable.getTableName());
		return R.ok(aiDataTableService.page(page, wrapper));
	}

	/**
	 * 查询所有已同步的数据表列表 支持按数据源过滤
	 * @param aiDataTable 查询条件，可指定数据源名称
	 * @return 数据表列表
	 */
	@Operation(summary = "列表查询", description = "列表查询所有已经同步的表")
	@GetMapping("/list")
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_view')")
	public R listTable(@ParameterObject AiDataTableEntity aiDataTable) {
		return R.ok(aiDataTableService.list(Wrappers.<AiDataTableEntity>lambdaQuery()
			.eq(StrUtil.isNotBlank(aiDataTable.getDsName()), AiDataTableEntity::getDsName, aiDataTable.getDsName())));
	}

	/**
	 * 通过ID查询数据表详细信息
	 * @param tableId 数据表ID
	 * @return 数据表详细信息
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{tableId}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_view')")
	public R getById(@PathVariable("tableId") Long tableId) {
		return R.ok(aiDataTableService.getById(tableId));
	}

	/**
	 * 新增AI数据表
	 * @param aiDataTable AI数据表实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI  数据表管理表", description = "新增AI  数据表管理表")
	@SysLog("新增AI  数据表管理表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_add')")
	public R save(@RequestBody AiDataTableEntity aiDataTable) {
		return R.ok(aiDataTableService.save(aiDataTable));
	}

	/**
	 * 修改AI数据表信息
	 * @param aiDataTable AI数据表实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改AI  数据表管理表", description = "修改AI  数据表管理表")
	@SysLog("修改AI  数据表管理表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_edit')")
	public R updateById(@RequestBody AiDataTableEntity aiDataTable) {
		return R.ok(aiDataTableService.updateById(aiDataTable));
	}

	/**
	 * 批量删除AI数据表
	 * @param ids 数据表ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除AI  数据表管理表", description = "通过id删除AI  数据表管理表")
	@SysLog("通过id删除AI  数据表管理表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiDataTableService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出数据表信息到Excel
	 * @param aiDataTable 查询条件
	 * @param ids 指定导出的数据表ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_export')")
	public List<AiDataTableEntity> export(AiDataTableEntity aiDataTable, Long[] ids) {
		return aiDataTableService
			.list(Wrappers.lambdaQuery(aiDataTable)
				.in(ArrayUtil.isNotEmpty(ids), AiDataTableEntity::getTableId, CollUtil.toList(ids)));
	}

	/**
	 * 同步数据表信息 从开发平台的数据源中同步所有数据表信息，便于数据集使用
	 * @return 同步结果
	 */
	@Operation(summary = "同步数据表", description = "从开发平台数据源同步表信息")
	@SysLog("同步数据表")
	@PostMapping("/sync/{dsName}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiDataTable_del')")
	public R sync(@PathVariable String dsName) {
		return R.ok(aiDataTableService.sync(dsName));
	}

}
