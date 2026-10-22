package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.knowledge.entity.AiDataFieldEntity;
import com.deepdimen.matrix.knowledge.service.AiDataFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI数据字段管理控制器 用于管理AI数据表的字段信息，包括字段的同步、评估和批量更新等功能
 *
 * @author matrix
 * @date 2025-03-26 21:49:03
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiDataField")
@Tag(description = "aiDataField", name = "AI数据字段管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiDataFieldController {

	private final AiDataFieldService aiDataFieldService;

	/**
	 * 分页查询字段信息
	 * @param page 分页对象
	 * @param aiDataField AI数据字段查询条件
	 * @return 分页后的字段列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_view')")
	public R getAiDataFieldPage(@ParameterObject Page page, @ParameterObject AiDataFieldEntity aiDataField) {
		LambdaQueryWrapper<AiDataFieldEntity> wrapper = Wrappers.lambdaQuery();
		return R.ok(aiDataFieldService.page(page, wrapper));
	}

	/**
	 * 通过字段ID查询详细信息
	 * @param fieldId 字段ID
	 * @return 字段详细信息
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{fieldId}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_view')")
	public R getById(@PathVariable("fieldId") Long fieldId) {
		return R.ok(aiDataFieldService.getById(fieldId));
	}

	/**
	 * 新增AI数据字段
	 * @param aiDataField AI数据字段实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI 字段管理表", description = "新增AI 字段管理表")
	@SysLog("新增AI 字段管理表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_add')")
	public R save(@RequestBody AiDataFieldEntity aiDataField) {
		return R.ok(aiDataFieldService.save(aiDataField));
	}

	/**
	 * 修改AI数据字段信息
	 * @param aiDataField AI数据字段实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改AI 字段管理表", description = "修改AI 字段管理表")
	@SysLog("修改AI 字段管理表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_edit')")
	public R updateById(@RequestBody AiDataFieldEntity aiDataField) {
		return R.ok(aiDataFieldService.updateById(aiDataField));
	}

	/**
	 * 批量删除AI数据字段
	 * @param ids 字段ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除AI 字段管理表", description = "通过id删除AI 字段管理表")
	@SysLog("通过id删除AI 字段管理表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiDataFieldService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出字段信息到Excel
	 * @param aiDataField 查询条件
	 * @param ids 指定导出的字段ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_export')")
	public List<AiDataFieldEntity> export(AiDataFieldEntity aiDataField, Long[] ids) {
		return aiDataFieldService.list(Wrappers.lambdaQuery(aiDataField)
			.in(ArrayUtil.isNotEmpty(ids), AiDataFieldEntity::getFieldId, CollUtil.toList(ids)));
	}

	/**
	 * 获取指定数据表的字段信息 如果字段信息不存在，则自动同步并初始化
	 * @param dsName 数据源名称
	 * @param tableName 数据表名称
	 * @return 字段列表
	 */
	@Operation(summary = "通过table 获取字段", description = "如果没有的话则会初始化")
	@SysLog("获取字段")
	@GetMapping("/table/{dsName}/{tableName}")
	public R get(@PathVariable String dsName, @PathVariable String tableName) {
		return R.ok(aiDataFieldService.getOrSync(dsName, tableName));
	}

	/**
	 * 同步数据表字段信息 从数据源中获取最新的表结构信息并更新到系统中
	 * @param dsName 数据源名称
	 * @param tableName 数据表名称
	 * @return 同步结果
	 */
	@SysLog("同步字段")
	@PostMapping("/sync/{dsName}/{tableName}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_del')")
	public R syncTableField(@PathVariable String dsName, @PathVariable String tableName) {
		return R.ok(aiDataFieldService.syncTableField(dsName, tableName));
	}

	/**
	 * 批量更新字段信息 支持新增和更新操作
	 * @param aiDataField 字段实体列表
	 * @return 操作结果
	 */
	@Operation(summary = "批量更新", description = "批量更新")
	@SysLog("批量更新")
	@PutMapping("/batch")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_del')")
	public R batchSave(@RequestBody List<AiDataFieldEntity> aiDataField) {
		return R.ok(aiDataFieldService.saveOrUpdateBatch(aiDataField));
	}

	/**
	 * AI字段智能评估 使用AI对表字段进行智能分析和评估，生成字段描述和使用建议
	 * @param dsName 数据源名称
	 * @param tableName 数据表名称
	 * @return 评估结果
	 */
	@Operation(summary = "字段评估")
	@SysLog("字段评估")
	@PostMapping("/assess/{dsName}/{tableName}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_del')")
	public R assess(@PathVariable String dsName, @PathVariable String tableName) {
		aiDataFieldService.validateTableSelection(dsName, tableName);
		aiDataFieldService.assessTableField(dsName, tableName);
		return R.ok();
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<R<Void>> invalidSelection(ResponseStatusException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(R.failed(ex.getReason()));
	}

}
