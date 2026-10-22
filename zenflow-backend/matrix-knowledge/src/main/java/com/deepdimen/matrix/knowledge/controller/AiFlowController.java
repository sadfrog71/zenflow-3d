package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.api.encrypt.annotation.NoEncrypt;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.entity.AiFlowEntity;
import com.deepdimen.matrix.knowledge.service.AiFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;

/**
 * AI流程控制器 提供AI流程的管理功能，包括流程的创建、修改、删除、执行、导入导出等操作
 *
 * @author matrix
 * @date 2025-03-03 10:21:32
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiFlow")
@Tag(description = "aiFlow", name = "大模型流程表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiFlowController {

	private final AiFlowService aiFlowService;

	/**
	 * 分页查询AI流程列表 支持按流程名称模糊查询，按创建时间倒序排列
	 * @param page 分页对象
	 * @param aiFlow 查询条件
	 * @return 分页结果
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_view')")
	public R getAiFlowPage(@ParameterObject Page page, @ParameterObject AiFlowEntity aiFlow) {
		LambdaQueryWrapper<AiFlowEntity> wrapper = Wrappers.<AiFlowEntity>lambdaQuery()
			.like(StrUtil.isNotBlank(aiFlow.getName()), AiFlowEntity::getName, aiFlow.getName())
			.orderByDesc(AiFlowEntity::getCreateTime);
		return R.ok(aiFlowService.page(page, wrapper));
	}

	/**
	 * 通过ID查询AI流程详情
	 * @param id 流程ID
	 * @return 流程详情信息
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_view')")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiFlowService.getById(id));
	}

	/**
	 * 新增AI流程 创建新的AI处理流程配置
	 * @param aiFlow 流程实体对象
	 * @return 操作结果
	 */
	@Operation(summary = "新增大模型流程表", description = "新增大模型流程表")
	@SysLog("新增大模型流程表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_add')")
	public R save(@RequestBody AiFlowEntity aiFlow) {
		return R.ok(aiFlowService.save(aiFlow));
	}

	/**
	 * 修改AI流程 更新已有的AI流程配置信息
	 * @param aiFlow 流程实体对象
	 * @return 操作结果
	 */
	@Operation(summary = "修改大模型流程表", description = "修改大模型流程表")
	@SysLog("修改大模型流程表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_edit')")
	public R updateById(@RequestBody AiFlowEntity aiFlow) {
		return R.ok(aiFlowService.updateById(aiFlow));
	}

	/**
	 * 批量删除AI流程 支持同时删除多个流程配置
	 * @param ids 流程ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除大模型流程表", description = "通过id删除大模型流程表")
	@SysLog("通过id删除大模型流程表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiFlowService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 执行AI流程 根据流程配置执行AI处理任务
	 * @param executeDTO 流程执行参数，包含流程ID和输入参数
	 * @return 流程执行结果
	 */
	@Operation(summary = "执行流程", description = "执行流程")
	@PostMapping("/execute")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_view')")
	public Object execute(@RequestBody AiFlowExecuteDTO executeDTO) {
		return aiFlowService.executeFlow(executeDTO);
	}

	/**
	 * 导出AI流程配置 将流程配置导出为JSON格式文件
	 * @param id 流程ID
	 * @param response HTTP响应对象，用于文件下载
	 */
	@NoEncrypt
	@Operation(summary = "导出流程", description = "导出流程")
	@GetMapping("/export/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_del')")
	public void exportFlow(@PathVariable Long id, HttpServletResponse response) {
		aiFlowService.exportFlow(id, response);
	}

	/**
	 * 复制AI流程 创建指定流程的副本，便于快速创建相似流程
	 * @param id 源流程ID
	 * @return 新流程信息
	 */
	@Operation(summary = "导出流程", description = "导出流程")
	@PostMapping("/copy/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_del')")
	public R copyFlow(@PathVariable Long id) {
		return aiFlowService.copyFlow(id);
	}

	/**
	 * 导入AI流程配置 从上传的JSON文件中导入流程配置
	 * @param file 包含流程配置的JSON文件
	 * @return 导入结果
	 */
	@NoEncrypt
	@SneakyThrows
	@PostMapping("/import")
	@PreAuthorize("@pms.hasPermission('knowledge_aiFlow_del')")
	public R importFlow(@RequestPart("file") MultipartFile file) {
		String inputDsl = IoUtil.read(file.getInputStream(), Charset.defaultCharset());

		if (!JSONUtil.isJson(inputDsl)) {
			return R.failed("导入文件格式错误");
		}

		AiFlowEntity flowEntity = JSONUtil.toBean(inputDsl, AiFlowEntity.class);
		aiFlowService.save(flowEntity);
		return R.ok();
	}

}
