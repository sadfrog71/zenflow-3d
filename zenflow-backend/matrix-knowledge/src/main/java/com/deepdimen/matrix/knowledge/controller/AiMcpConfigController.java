package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;
import com.deepdimen.matrix.knowledge.service.AiMcpConfigService;
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
 * MCP配置控制器 管理模型上下文协议(Model Context Protocol)的配置，用于AI模型的工具调用和集成
 *
 * @author matrix
 * @date 2025-03-22 13:36:32
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiMcpConfig")
@Tag(description = "aiMcpConfig", name = "MCP配置表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiMcpConfigController {

	private final AiMcpConfigService aiMcpConfigService;

	/**
	 * 分页查询MCP配置列表 支持按名称、类型、启用状态等条件筛选
	 * @param page 分页对象
	 * @param aiMcpConfig 查询条件
	 * @return 分页的MCP配置列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_view')")
	public R getAiMcpConfigPage(@ParameterObject Page page, @ParameterObject AiMcpConfigEntity aiMcpConfig) {
		LambdaQueryWrapper<AiMcpConfigEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.like(StrUtil.isNotBlank(aiMcpConfig.getName()), AiMcpConfigEntity::getName, aiMcpConfig.getName());
		wrapper.eq(StrUtil.isNotBlank(aiMcpConfig.getMcpType()), AiMcpConfigEntity::getMcpType,
				aiMcpConfig.getMcpType());
		wrapper.eq(StrUtil.isNotBlank(aiMcpConfig.getMcpEnabled()), AiMcpConfigEntity::getMcpEnabled,
				aiMcpConfig.getMcpEnabled());
		return R.ok(aiMcpConfigService.page(page, wrapper));
	}

	/**
	 * 通过ID查询MCP配置详情 获取单个MCP配置的完整信息
	 * @param mcpId 配置ID
	 * @return MCP配置详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{mcpId}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_view')")
	public R getById(@PathVariable("mcpId") Long mcpId) {
		return R.ok(aiMcpConfigService.getById(mcpId));
	}

	/**
	 * 新增MCP配置 创建新的模型上下文协议配置
	 * @param aiMcpConfig MCP配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增MCP配置表", description = "新增MCP配置表")
	@SysLog("新增MCP配置表")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_add')")
	public R save(@RequestBody AiMcpConfigEntity aiMcpConfig) {
		return R.ok(aiMcpConfigService.saveOrUpdateMcp(aiMcpConfig));
	}

	/**
	 * 修改MCP配置 更新现有的MCP配置信息
	 * @param aiMcpConfig MCP配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改MCP配置表", description = "修改MCP配置表")
	@SysLog("修改MCP配置表")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_edit')")
	public R updateById(@RequestBody AiMcpConfigEntity aiMcpConfig) {
		return R.ok(aiMcpConfigService.saveOrUpdateMcp(aiMcpConfig));
	}

	/**
	 * 批量删除MCP配置 支持批量删除不再使用的MCP配置
	 * @param ids MCP配置ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除MCP配置表", description = "通过id删除MCP配置表")
	@SysLog("通过id删除MCP配置表")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiMcpConfigService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出MCP配置到Excel 将选中的MCP配置导出为Excel文件
	 * @param aiMcpConfig 查询条件
	 * @param ids 指定要导出的配置ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_export')")
	public List<AiMcpConfigEntity> export(AiMcpConfigEntity aiMcpConfig, Long[] ids) {
		return aiMcpConfigService
			.list(Wrappers.lambdaQuery(aiMcpConfig)
				.in(ArrayUtil.isNotEmpty(ids), AiMcpConfigEntity::getMcpId, CollUtil.toList(ids)));
	}

	/**
	 * 查询启用的MCP配置列表 获取所有启用状态的MCP配置
	 * @return 启用的MCP配置列表
	 */
	@Operation(summary = "查询列表", description = "查询列表")
	@GetMapping("/list")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_view')")
	public R list() {
		return R.ok(aiMcpConfigService.list(Wrappers.<AiMcpConfigEntity>lambdaQuery()
			.eq(AiMcpConfigEntity::getMcpEnabled, YesNoEnum.YES.getCode())));
	}

	/**
	 * 查询MCP工具资源列表 获取指定MCP配置下可用的工具和资源
	 * @param aiMcpConfig MCP配置实体，用于指定查询哪个MCP
	 * @return 包含MCP工具资源列表的响应对象
	 */
	@Operation(summary = "查询MCP工具", description = "查询MCP功能列表")
	@GetMapping("/list/tools")
	@PreAuthorize("@pms.hasPermission('knowledge_aiMcpConfig_view')")
	public R listTools(AiMcpConfigEntity aiMcpConfig) {

		try {
			return R.ok(aiMcpConfigService.listTools(aiMcpConfig));
		}
		catch (Exception e) {
			return R.failed("查询MCP工具资源列表失败: " + e.getMessage());
		}
	}

}
