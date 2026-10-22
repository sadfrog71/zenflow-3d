package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.RequestExcel;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.entity.AiDatasourceEntity;
import com.deepdimen.matrix.knowledge.service.AiDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源表
 *
 * @author matrix
 * @date 2025-09-21 17:25:49
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiDatasource")
@Tag(description = "aiDatasource", name = "数据源表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiDatasourceController {

	private final AiDatasourceService aiDatasourceService;

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param aiDatasource 数据源表
	 * @return
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiDatasource_view")
	public R getAiDatasourcePage(@ParameterObject Page page, @ParameterObject AiDatasourceEntity aiDatasource) {
		LambdaQueryWrapper<AiDatasourceEntity> wrapper = Wrappers.lambdaQuery();
		return R.ok(aiDatasourceService.page(page, wrapper));
	}

	/**
	 * 列表查询
	 * @param aiDatasource 查询条件参数对象
	 * @return 查询结果
	 */
	@Operation(summary = "列表查询", description = "列表查询")
	@GetMapping("/list")
	@HasPermission("knowledge_aiDatasource_view")
	public R listAiDatasource(@ParameterObject AiDatasourceEntity aiDatasource) {
		LambdaQueryWrapper<AiDatasourceEntity> wrapper = Wrappers.lambdaQuery(aiDatasource);
		return R.ok(aiDatasourceService.list(wrapper));
	}

	/**
	 * 通过条件查询数据源表
	 * @param aiDatasource 查询条件
	 * @return R 对象列表
	 */
	@Operation(summary = "通过条件查询", description = "通过条件查询对象")
	@GetMapping("/details")
	@HasPermission("knowledge_aiDatasource_view")
	public R getDetails(@ParameterObject AiDatasourceEntity aiDatasource) {
		return R.ok(aiDatasourceService.list(Wrappers.query(aiDatasource)));
	}

	/**
	 * 新增数据源表
	 * @param aiDatasource 数据源表
	 * @return R
	 */
	@Operation(summary = "新增数据源表", description = "新增数据源表")
	@SysLog("新增数据源表")
	@PostMapping
	@HasPermission("knowledge_aiDatasource_add")
	public R save(@RequestBody AiDatasourceEntity aiDatasource) {
		return R.ok(aiDatasourceService.save(aiDatasource));
	}

	/**
	 * 修改数据源表
	 * @param aiDatasource 数据源表
	 * @return R
	 */
	@Operation(summary = "修改数据源表", description = "修改数据源表")
	@SysLog("修改数据源表")
	@PutMapping
	@HasPermission("knowledge_aiDatasource_edit")
	public R updateById(@RequestBody AiDatasourceEntity aiDatasource) {
		return R.ok(aiDatasourceService.updateById(aiDatasource));
	}

	/**
	 * 通过id删除数据源表
	 * @param ids id列表
	 * @return R
	 */
	@Operation(summary = "通过id删除数据源表", description = "通过id删除数据源表")
	@SysLog("通过id删除数据源表")
	@DeleteMapping
	@HasPermission("knowledge_aiDatasource_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiDatasourceService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出excel 表格
	 * @param aiDatasource 查询条件
	 * @param ids 导出指定ID
	 * @return excel 文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiDatasource_export")
	public List<AiDatasourceEntity> exportExcel(AiDatasourceEntity aiDatasource, Long[] ids) {
		return aiDatasourceService
			.list(Wrappers.lambdaQuery(aiDatasource)
				.in(ArrayUtil.isNotEmpty(ids), AiDatasourceEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 导入excel 表
	 * @param aiDatasourceList 对象实体列表
	 * @param bindingResult 错误信息列表
	 * @return ok fail
	 */
	@PostMapping("/import")
	@HasPermission("knowledge_aiDatasource_export")
	public R importExcel(@RequestExcel List<AiDatasourceEntity> aiDatasourceList, BindingResult bindingResult) {
		return R.ok(aiDatasourceService.saveBatch(aiDatasourceList));
	}

	/**
	 * 验证数据源连接
	 * @param aiDatasource 数据源实体
	 * @return 验证结果
	 */
	@PostMapping("/validateConnection")
	@HasPermission("knowledge_aiDatasource_add")
	@Operation(summary = "验证数据源连接", description = "验证数据源连接")
	public R validateConnection(@RequestBody AiDatasourceEntity aiDatasource) {
		return aiDatasourceService.validateConnection(aiDatasource);
	}

}
