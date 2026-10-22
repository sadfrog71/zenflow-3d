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
import com.deepdimen.matrix.knowledge.entity.AiDataEntity;
import com.deepdimen.matrix.knowledge.service.AiDataService;
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
 * AI数据集管理控制器 管理AI知识库的数据集，包括数据集的创建、修改、查询和删除等操作 数据集是AI系统进行知识检索和学习的基础数据单元
 *
 * @author matrix
 * @date 2025-03-26 21:47:45
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiData")
@Tag(description = "aiData", name = "AI数据集管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiDataController {

	private final AiDataService aiDataService;

	/**
	 * 分页查询数据集列表 支持按数据集名称进行模糊搜索
	 * @param page 分页对象，包含页码、每页大小等信息
	 * @param aiData 查询条件，可包含数据集名称等筛选条件
	 * @return 分页数据集列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_view')")
	public R getAiDataPage(@ParameterObject Page page, @ParameterObject AiDataEntity aiData) {
		LambdaQueryWrapper<AiDataEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.like(StrUtil.isNotBlank(aiData.getDatasetName()), AiDataEntity::getDatasetName,
				aiData.getDatasetName());
		return R.ok(aiDataService.page(page, wrapper));
	}

	/**
	 * 查询数据集详情 根据数据集ID获取单个数据集的详细信息
	 * @param dataId 数据集ID
	 * @return 数据集详细信息
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{dataId}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_view')")
	public R getById(@PathVariable("dataId") Long dataId) {
		return R.ok(aiDataService.getById(dataId));
	}

	/**
	 * 新增数据集 创建新的AI数据集，用于存储和管理知识库数据
	 * @param aiData 数据集实体，包含名称、描述、类型等信息
	 * @return 创建结果
	 */
	@Operation(summary = "新增AI 数据集管理表", description = "新增AI 数据集管理表")
	@SysLog("新增AI 数据集管理表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_add')")
	public R save(@RequestBody AiDataEntity aiData) {
		return R.ok(aiDataService.saveOrUpdateData(aiData));
	}

	/**
	 * 修改数据集 更新数据集的基本信息和配置
	 * @param aiData 数据集实体，包含需要更新的信息
	 * @return 更新结果
	 */
	@Operation(summary = "修改AI 数据集管理表", description = "修改AI 数据集管理表")
	@SysLog("修改AI 数据集管理表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_edit')")
	public R updateById(@RequestBody AiDataEntity aiData) {
		return R.ok(aiDataService.saveOrUpdateData(aiData));
	}

	/**
	 * 批量删除数据集 根据ID列表删除数据集及其关联的数据 注意：删除操作不可恢复，请谨慎使用
	 * @param ids 数据集ID数组
	 * @return 删除结果
	 */
	@Operation(summary = "通过id删除AI 数据集管理表", description = "通过id删除AI 数据集管理表")
	@SysLog("通过id删除AI 数据集管理表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiDataService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出数据集Excel 根据查询条件或指定ID导出数据集信息到Excel文件
	 * @param aiData 查询条件
	 * @param ids 指定导出的数据集ID数组（可选）
	 * @return 数据集列表
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiData_export')")
	public List<AiDataEntity> export(AiDataEntity aiData, Long[] ids) {
		return aiDataService
			.list(Wrappers.lambdaQuery(aiData)
				.in(ArrayUtil.isNotEmpty(ids), AiDataEntity::getDataId, CollUtil.toList(ids)));
	}

}
