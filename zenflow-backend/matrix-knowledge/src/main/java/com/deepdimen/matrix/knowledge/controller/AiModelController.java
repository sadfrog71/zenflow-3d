package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.service.AiModelService;
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
 * AI模型配置控制器 管理AI模型的配置信息，包括模型参数、默认模型设置、模型同步等功能
 *
 * @author matrix
 * @date 2024-09-27 23:37:54
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiModel")
@Tag(description = "aiModel", name = "模型配置管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiModelController {

	private final AiModelService aiModelService;

	/**
	 * 分页查询模型配置列表 支持按模型类型筛选
	 * @param page 分页对象
	 * @param aiModel 查询条件
	 * @return 分页的模型配置列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_view')")
	public R getAiModelPage(@ParameterObject Page page, @ParameterObject AiModelEntity aiModel) {
		return R.ok(aiModelService.page(page, Wrappers.<AiModelEntity>lambdaQuery()
			.eq(StrUtil.isNotBlank(aiModel.getModelType()), AiModelEntity::getModelType, aiModel.getModelType())));
	}

	/**
	 * 查询AI模型详情列表 获取模型详细信息，默认模型优先显示
	 * @param aiModel 查询条件
	 * @return 模型详情列表
	 */
	@Inner(value = false)
	@Operation(summary = "列表查询", description = "列表查询")
	@GetMapping("/details")
	public R getDetails(@ParameterObject AiModelEntity aiModel) {
		// 如果是默认模型，则优先显示
		return R.ok(aiModelService.list(Wrappers.lambdaQuery(aiModel).orderByDesc(AiModelEntity::getDefaultModel)));
	}

	/**
	 * 按类型查询模型列表 支持按多个模型类型查询，默认模型和最新创建的优先排序
	 * @param modelType 模型类型数组
	 * @return 模型列表
	 */
	@Operation(summary = "列表查询", description = "列表查询")
	@GetMapping("/list")
	public R list(String[] modelType) {
		return R.ok(aiModelService.list(Wrappers.lambdaQuery(AiModelEntity.class)
			.in(ArrayUtil.isNotEmpty(modelType), AiModelEntity::getModelType, CollUtil.toList(modelType))
			.orderByDesc(AiModelEntity::getDefaultModel, AiModelEntity::getCreateTime)));
	}

	/**
	 * 通过ID查询模型配置详情 获取单个模型的完整配置信息
	 * @param id 模型ID
	 * @return 模型配置详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_view')")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiModelService.getById(id));
	}

	/**
	 * 新增AI模型配置 添加新的AI模型配置，包括模型参数和连接信息
	 * @param aiModel 模型配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增模型配置", description = "新增模型配置")
	@SysLog("新增模型配置")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_add')")
	public R save(@RequestBody AiModelEntity aiModel) {
		return aiModelService.saveModel(aiModel);
	}

	/**
	 * 同步模型配置 刷新模型配置，确保更换模型后立即生效
	 * @return 同步结果
	 */
	@Operation(summary = "同步模型", description = "同步模型，避免换成模型后，模型不生效")
	@SysLog("同步模型")
	@PostMapping("/sync")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_add')")
	public R sync() {
		return aiModelService.syncModel();
	}

	/**
	 * 修改AI模型配置 更新现有模型的配置信息
	 * @param aiModel 模型配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改模型配置", description = "修改模型配置")
	@SysLog("修改模型配置")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_edit')")
	public R updateById(@RequestBody AiModelEntity aiModel) {
		return aiModelService.updateModel(aiModel);
	}

	@Operation(summary = "设置统一助手模型", description = "复用当前租户已配置的 Chat 模型作为统一助手模型")
	@SysLog("设置统一助手模型")
	@PutMapping("/{id}/assistant-model")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_edit')")
	public R setAssistantModel(@PathVariable("id") Long id, @RequestParam("enabled") boolean enabled) {
		return aiModelService.setAssistantModel(id, enabled);
	}

	/**
	 * 批量删除模型配置 支持批量删除不再使用的模型配置
	 * @param ids 模型ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除模型配置", description = "通过id删除模型配置")
	@SysLog("通过id删除模型配置")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_del')")
	public R removeById(@RequestBody Long[] ids) {
		return aiModelService.removeModel(CollUtil.toList(ids));
	}

	/**
	 * 导出模型配置到Excel 将选中的模型配置导出为Excel文件
	 * @param aiModel 查询条件
	 * @param ids 指定要导出的模型ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_export')")
	public List<AiModelEntity> export(AiModelEntity aiModel, Long[] ids) {
		return aiModelService
			.list(Wrappers.lambdaQuery(aiModel)
				.in(ArrayUtil.isNotEmpty(ids), AiModelEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 获取Ollama模型列表 从指定的Ollama服务器动态获取可用模型列表
	 * @param baseUrl Ollama服务器的基础URL地址
	 * @return 模型列表
	 */
	@Operation(summary = "获取Ollama模型列表", description = "从Ollama服务器获取可用的模型列表")
	@GetMapping("/ollama/models")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_view')")
	public R getOllamaModels(@RequestParam String baseUrl) {
		return aiModelService.getOllamaModels(baseUrl);
	}

	/**
	 * 动态拉取供应商模型列表 根据 baseUrl+apiKey 实时从供应商获取可用模型名列表，替代前端写死的模型常量
	 * @param aiModel 模型配置参数，需含 provider、baseUrl、apiKey（id 可选，用于脱敏回查）
	 * @return 模型名列表
	 */
	@Operation(summary = "动态拉取供应商模型列表", description = "根据baseUrl+apiKey实时获取供应商可用模型列表")
	@PostMapping("/models")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_view')")
	public R fetchProviderModels(@RequestBody AiModelEntity aiModel) {
		return aiModelService.fetchProviderModels(aiModel);
	}

	/**
	 * 测试模型连接 使用传入的模型配置参数临时构建模型实例验证连通性，无需先保存
	 * @param aiModel 模型配置参数
	 * @return 测试结果，包含 success/message/latency
	 */
	@Operation(summary = "测试模型连接", description = "临时构建模型实例验证连通性，按模型类型执行最小化测试")
	@SysLog("测试模型连接")
	@PostMapping("/test")
	@PreAuthorize("@pms.hasPermission('knowledge_aiModel_view')")
	public R testModel(@RequestBody AiModelEntity aiModel) {
		return aiModelService.testModel(aiModel);
	}

}
