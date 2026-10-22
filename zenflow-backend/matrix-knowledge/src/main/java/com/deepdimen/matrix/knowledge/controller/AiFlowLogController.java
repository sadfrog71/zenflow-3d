package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.knowledge.entity.AiFlowLogEntity;
import com.deepdimen.matrix.knowledge.service.AiFlowLogService;
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
 * AI流程日志控制器 负责管理AI流程执行日志的记录、查询和导出功能
 *
 * @author matrix
 * @date 2025-03-03 10:21:15
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiFlowLog")
@Tag(description = "aiFlowLog", name = "大模型流程日志表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiFlowLogController {

	private final AiFlowLogService aiFlowLogService;

	/**
	 * 分页查询流程执行日志 获取AI流程的执行历史记录
	 * @param page 分页对象
	 * @param aiFlowLog 查询条件
	 * @return 分页的日志列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlowLog_view')")
	public R getAiFlowLogPage(@ParameterObject Page page, @ParameterObject AiFlowLogEntity aiFlowLog) {
		LambdaQueryWrapper<AiFlowLogEntity> wrapper = Wrappers.lambdaQuery();
		return R.ok(aiFlowLogService.page(page, wrapper));
	}

	/**
	 * 通过ID查询流程日志详情 获取单条流程执行日志的详细信息
	 * @param id 日志ID
	 * @return 日志详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlowLog_view')")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiFlowLogService.getById(id));
	}

	/**
	 * 新增流程执行日志 记录AI流程的执行过程和结果
	 * @param aiFlowLog 日志实体对象
	 * @return 操作结果
	 */
	@Operation(summary = "新增大模型流程日志表", description = "新增大模型流程日志表")
	@SysLog("新增大模型流程日志表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlowLog_add')")
	public R save(@RequestBody AiFlowLogEntity aiFlowLog) {
		return R.ok(aiFlowLogService.save(aiFlowLog));
	}

	/**
	 * 修改流程执行日志 更新日志信息，如执行状态、结果等
	 * @param aiFlowLog 日志实体对象
	 * @return 操作结果
	 */
	@Operation(summary = "修改大模型流程日志表", description = "修改大模型流程日志表")
	@SysLog("修改大模型流程日志表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlowLog_edit')")
	public R updateById(@RequestBody AiFlowLogEntity aiFlowLog) {
		return R.ok(aiFlowLogService.updateById(aiFlowLog));
	}

	/**
	 * 批量删除流程日志 支持批量清理历史执行日志
	 * @param ids 日志ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除大模型流程日志表", description = "通过id删除大模型流程日志表")
	@SysLog("通过id删除大模型流程日志表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlowLog_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiFlowLogService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出流程日志到Excel 将选中的日志记录导出为Excel文件
	 * @param aiFlowLog 查询条件
	 * @param ids 指定要导出的日志ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlowLog_export')")
	public List<AiFlowLogEntity> export(AiFlowLogEntity aiFlowLog, Long[] ids) {
		return aiFlowLogService
			.list(Wrappers.lambdaQuery(aiFlowLog)
				.in(ArrayUtil.isNotEmpty(ids), AiFlowLogEntity::getId, CollUtil.toList(ids)));
	}

}
