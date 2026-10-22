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
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.service.AiEmbedStoreService;
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
 * 向量库配置管理控制器 用于管理向量数据库的配置信息，支持多种向量数据库类型的配置管理
 *
 * @author matrix
 * @date 2025-02-11 15:07:46
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiEmbedStore")
@Tag(description = "aiEmbedStore", name = "向量库配置管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiEmbedStoreController {

	private final AiEmbedStoreService aiEmbedStoreService;

	/**
	 * 分页查询向量库配置 支持按配置名称和存储类型进行过滤
	 * @param page 分页对象
	 * @param aiEmbedStore 向量库配置查询条件
	 * @return 分页后的向量库配置列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_view')")
	public R getAiEmbedStorePage(@ParameterObject Page page, @ParameterObject AiEmbedStoreEntity aiEmbedStore) {
		LambdaQueryWrapper<AiEmbedStoreEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.like(StrUtil.isNotBlank(aiEmbedStore.getName()), AiEmbedStoreEntity::getName, aiEmbedStore.getName());
		wrapper.eq(StrUtil.isNotBlank(aiEmbedStore.getStoreType()), AiEmbedStoreEntity::getStoreType,
				aiEmbedStore.getStoreType());
		return R.ok(aiEmbedStoreService.page(page, wrapper));
	}

	/**
	 * 获取向量库配置详细信息 根据查询条件获取单个向量库配置的详细信息
	 * @param query 查询条件
	 * @return 向量库配置详细信息
	 */
	@Operation(summary = "获取详细信息", description = "获取详细信息")
	@GetMapping("/details")
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_view')")
	public R getDetails(@ParameterObject AiEmbedStoreEntity query) {
		return R.ok(aiEmbedStoreService.getOne(Wrappers.query(query), false));
	}

	/**
	 * 获取所有向量库配置列表
	 * @return 所有向量库配置信息
	 */
	@GetMapping("/list")
	@Operation(summary = "获取全部配置列表", description = "获取系统中所有向量库配置")
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_view')")
	public R list() {
		return R.ok(aiEmbedStoreService.list());
	}

	/**
	 * 新增向量库配置 添加新的向量数据库连接配置
	 * @param aiEmbedStore 向量库配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增向量库配置", description = "新增向量库配置")
	@SysLog("新增向量库配置")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_add')")
	public R save(@RequestBody AiEmbedStoreEntity aiEmbedStore) {
		return R.ok(aiEmbedStoreService.save(aiEmbedStore));
	}

	/**
	 * 修改向量库配置 更新现有向量数据库的连接配置信息
	 * @param aiEmbedStore 向量库配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改向量库配置", description = "修改向量库配置")
	@SysLog("修改向量库配置")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_edit')")
	public R updateById(@RequestBody AiEmbedStoreEntity aiEmbedStore) {
		return R.ok(aiEmbedStoreService.updateEmbedStore(aiEmbedStore));
	}

	/**
	 * 批量删除向量库配置
	 * @param ids 向量库配置ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除向量库配置", description = "通过id删除向量库配置")
	@SysLog("通过id删除向量库配置")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiEmbedStoreService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出向量库配置信息到Excel
	 * @param aiEmbedStore 查询条件
	 * @param ids 指定导出的配置ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiEmbedStore_export')")
	public List<AiEmbedStoreEntity> export(AiEmbedStoreEntity aiEmbedStore, Long[] ids) {
		return aiEmbedStoreService.list(
				Wrappers.lambdaQuery(aiEmbedStore)
					.in(ArrayUtil.isNotEmpty(ids), AiEmbedStoreEntity::getStoreId, CollUtil.toList(ids)));
	}

}
