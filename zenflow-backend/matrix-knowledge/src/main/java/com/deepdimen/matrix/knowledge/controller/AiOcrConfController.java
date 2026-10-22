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
import com.deepdimen.matrix.knowledge.entity.AiOcrConfEntity;
import com.deepdimen.matrix.knowledge.service.AiOcrConfService;
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
 * AI OCR配置控制器 管理OCR（光学字符识别）的配置和图片文字识别功能
 *
 * @author matrix
 * @date 2024-09-10 00:49:08
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ocr")
@Tag(description = "ocr", name = "AI OCR管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiOcrConfController {

	private final AiOcrConfService aiOcrConfService;

	/**
	 * 分页查询OCR配置列表 获取系统中的OCR配置信息
	 * @param page 分页对象
	 * @param aiOcrConf 查询条件
	 * @return 分页的OCR配置列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_ocr_view")
	public R getAiOcrConfPage(@ParameterObject Page page, @ParameterObject AiOcrConfEntity aiOcrConf) {
		LambdaQueryWrapper<AiOcrConfEntity> wrapper = Wrappers.lambdaQuery();
		return R.ok(aiOcrConfService.page(page, wrapper));
	}

	/**
	 * 通过条件查询OCR配置列表 根据指定条件查询OCR配置信息
	 * @param aiOcrConf 查询条件
	 * @return OCR配置列表
	 */
	@Operation(summary = "通过条件查询", description = "通过条件查询对象")
	@GetMapping("/details")
	@HasPermission("knowledge_ocr_view")
	public R getDetails(@ParameterObject AiOcrConfEntity aiOcrConf) {
		return R.ok(aiOcrConfService.list(Wrappers.query(aiOcrConf)));
	}

	/**
	 * 新增OCR配置 创建新的OCR配置信息
	 * @param aiOcrConf OCR配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI OCR", description = "新增AI OCR")
	@SysLog("新增AI OCR")
	@PostMapping
	@HasPermission("knowledge_ocr_add")
	public R save(@RequestBody AiOcrConfEntity aiOcrConf) {
		return R.ok(aiOcrConfService.save(aiOcrConf));
	}

	/**
	 * 修改OCR配置 更新现有的OCR配置信息
	 * @param aiOcrConf OCR配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改AI OCR", description = "修改AI OCR")
	@SysLog("修改AI OCR")
	@PutMapping
	@HasPermission("knowledge_ocr_edit")
	public R updateById(@RequestBody AiOcrConfEntity aiOcrConf) {
		return R.ok(aiOcrConfService.updateById(aiOcrConf));
	}

	/**
	 * 批量删除OCR配置 支持批量删除不再使用的OCR配置
	 * @param ids OCR配置ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除AI OCR", description = "通过id删除AI OCR")
	@SysLog("通过id删除AI OCR")
	@DeleteMapping
	@HasPermission("knowledge_ocr_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiOcrConfService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出OCR配置到Excel 将选中的OCR配置导出为Excel文件
	 * @param aiOcrConf 查询条件
	 * @param ids 指定要导出的配置ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_ocr_export")
	public List<AiOcrConfEntity> exportExcel(AiOcrConfEntity aiOcrConf, Long[] ids) {
		return aiOcrConfService
			.list(Wrappers.lambdaQuery(aiOcrConf)
				.in(ArrayUtil.isNotEmpty(ids), AiOcrConfEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 导入OCR配置 从上传的Excel文件中批量导入OCR配置
	 * @param aiOcrConfList OCR配置实体列表
	 * @param bindingResult 验证错误信息
	 * @return 操作结果
	 */
	@PostMapping("/import")
	@HasPermission("knowledge_ocr_export")
	public R importExcel(@RequestExcel List<AiOcrConfEntity> aiOcrConfList, BindingResult bindingResult) {
		return R.ok(aiOcrConfService.saveBatch(aiOcrConfList));
	}

	/**
	 * 执行OCR图片识别 调用OCR服务对图片进行文字识别
	 * @param aiOcrConf 包含图片URL的OCR配置实体
	 * @return 识别结果
	 */
	@Operation(summary = "解析图片", description = "调用OCR解析图片")
	@SysLog("解析图片")
	@PostMapping("/parse")
	@HasPermission("knowledge_ocr_add")
	public R parse(@RequestBody AiOcrConfEntity aiOcrConf) {
		return aiOcrConfService.parseImage(aiOcrConf);
	}

}
