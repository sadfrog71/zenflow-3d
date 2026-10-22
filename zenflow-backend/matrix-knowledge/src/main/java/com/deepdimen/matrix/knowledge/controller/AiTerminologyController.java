package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.RequestExcel;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.dto.AiTerminologyDTO;
import com.deepdimen.matrix.knowledge.entity.AiTerminologyEntity;
import com.deepdimen.matrix.knowledge.service.AiTerminologyService;
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
 * AI术语
 *
 * @author MATRIX AI
 * @date 2025-09-20 18:59:42
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiTerminology")
@Tag(description = "aiTerminology", name = "AI术语管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiTerminologyController {

	private final AiTerminologyService aiTerminologyService;

	private final com.deepdimen.matrix.knowledge.service.impl.AiTerminologyDatasetBindings datasetBindings;

	@GetMapping("/datasets")
	@HasPermission("knowledge_aiTerminology_view")
	public R datasets() {
		return R.ok(datasetBindings.options());
	}

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param aiTerminology AI术语
	 * @return
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiTerminology_view")
	public R getAiTerminologyPage(@ParameterObject Page page, @ParameterObject AiTerminologyEntity aiTerminology,
			@RequestParam(required = false) Long dataId) {
		LambdaQueryWrapper<AiTerminologyEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.like(StrUtil.isNotBlank(aiTerminology.getWord()), AiTerminologyEntity::getWord,
				aiTerminology.getWord());
		// 只查询顶级术语,不查询同义词
		wrapper.isNull(AiTerminologyEntity::getPid);
		if (dataId != null) {
			var ids = datasetBindings.termsForDataset(dataId);
			if (ids.isEmpty())
				wrapper.apply("1=0");
			else
				wrapper.in(AiTerminologyEntity::getTermId, ids);
		}
		return R.ok(aiTerminologyService.page(page, wrapper));
	}

	/**
	 * 通过条件查询AI术语
	 * @param aiTerminology 查询条件
	 * @return R 对象列表
	 */
	@Operation(summary = "通过条件查询", description = "通过条件查询对象")
	@GetMapping("/details")
	@HasPermission("knowledge_aiTerminology_view")
	public R getDetails(@ParameterObject AiTerminologyEntity aiTerminology) {
		return aiTerminologyService.getDetails(aiTerminology);
	}

	/**
	 * 新增AI术语
	 * @param aiTerminology AI术语DTO对象
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI术语", description = "新增AI术语")
	@SysLog("新增AI术语")
	@PostMapping
	@HasPermission("knowledge_aiTerminology_add")
	public R save(@RequestBody AiTerminologyDTO aiTerminology) {
		return aiTerminologyService.saveAiTerminology(aiTerminology);
	}

	/**
	 * 修改AI术语
	 * @param aiTerminology AI术语
	 * @return R
	 */
	@Operation(summary = "修改AI术语", description = "修改AI术语")
	@SysLog("修改AI术语")
	@PutMapping
	@HasPermission("knowledge_aiTerminology_edit")
	public R updateById(@RequestBody AiTerminologyDTO aiTerminology) {
		return aiTerminologyService.updateAiTerminology(aiTerminology);
	}

	/**
	 * 通过id删除AI术语
	 * @param ids termId列表
	 * @return R
	 */
	@Operation(summary = "通过id删除AI术语", description = "通过id删除AI术语")
	@SysLog("通过id删除AI术语")
	@DeleteMapping
	@HasPermission("knowledge_aiTerminology_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiTerminologyService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出excel 表格
	 * @param aiTerminology 查询条件
	 * @param ids 导出指定ID
	 * @return excel 文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiTerminology_export")
	public List<AiTerminologyEntity> exportExcel(AiTerminologyEntity aiTerminology, Long[] ids) {
		return aiTerminologyService.list(Wrappers.lambdaQuery(aiTerminology)
			.in(ArrayUtil.isNotEmpty(ids), AiTerminologyEntity::getTermId, CollUtil.toList(ids)));
	}

	/**
	 * 导入excel 表
	 * @param aiTerminologyList 对象实体列表
	 * @param bindingResult 错误信息列表
	 * @return ok fail
	 */
	@PostMapping("/import")
	@HasPermission("knowledge_aiTerminology_export")
	public R importExcel(@RequestExcel List<AiTerminologyEntity> aiTerminologyList, BindingResult bindingResult) {
		return R.ok(aiTerminologyService.saveBatch(aiTerminologyList));
	}

	/**
	 * 查询所有顶级术语列表
	 * @param aiTerminology 查询条件DTO
	 * @return 包含查询结果的R对象
	 */
	@GetMapping("/list")
	public R listAll(AiTerminologyDTO aiTerminology) {
		return R.ok(aiTerminologyService.list(Wrappers.<AiTerminologyEntity>lambdaQuery()
			.like(StrUtil.isNotBlank(aiTerminology.getWord()), AiTerminologyEntity::getWord, aiTerminology.getWord())
			.isNull(AiTerminologyEntity::getPid)));
	}

}
