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
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.dto.AiReportCompletionsDTO;
import com.deepdimen.matrix.knowledge.entity.AiReportConfEntity;
import com.deepdimen.matrix.knowledge.service.AiReportConfService;
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
 * AI报告生成控制器 管理AI报告模板配置，支持根据模板生成报告文档和下载功能
 *
 * @author matrix
 * @date 2025-05-11 22:14:42
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiReportConf")
@Tag(description = "aiReportConf", name = "AI 报告管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiReportConfController {

	private final AiReportConfService aiReportConfService;

	/**
	 * 分页查询AI报告配置列表 支持按报告标题和提示词内容模糊查询
	 * @param page 分页对象
	 * @param aiReportConf 查询条件
	 * @return 分页的报告配置列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@PreAuthorize("@pms.hasPermission('knowledge_aiReportConf_view')")
	public R getAiReportConfPage(@ParameterObject Page page, @ParameterObject AiReportConfEntity aiReportConf) {
		LambdaQueryWrapper<AiReportConfEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.like(StrUtil.isNotBlank(aiReportConf.getReportTitle()), AiReportConfEntity::getReportTitle,
				aiReportConf.getReportTitle());
		wrapper.like(StrUtil.isNotBlank(aiReportConf.getReportPrompt()), AiReportConfEntity::getReportPrompt,
				aiReportConf.getReportPrompt());
		return R.ok(aiReportConfService.page(page, wrapper));
	}

	/**
	 * 通过ID查询AI报告配置详情 获取单个报告配置的完整信息
	 * @param id 报告配置ID
	 * @return 报告配置详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@PreAuthorize("@pms.hasPermission('knowledge_aiReportConf_view')")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiReportConfService.getById(id));
	}

	/**
	 * 新增AI报告配置 创建新的报告模板配置
	 * @param aiReportConf 报告配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增AI 报告", description = "新增AI 报告")
	@SysLog("新增AI 报告")
	@PostMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiReportConf_add')")
	public R save(@RequestBody AiReportConfEntity aiReportConf) {
		return R.ok(aiReportConfService.save(aiReportConf));
	}

	/**
	 * 修改AI报告配置 更新现有的报告模板信息
	 * @param aiReportConf 报告配置实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改AI 报告", description = "修改AI 报告")
	@SysLog("修改AI 报告")
	@PutMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiReportConf_edit')")
	public R updateById(@RequestBody AiReportConfEntity aiReportConf) {
		return R.ok(aiReportConfService.updateById(aiReportConf));
	}

	/**
	 * 批量删除AI报告配置 支持批量删除不再使用的报告配置
	 * @param ids 报告配置ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除AI 报告", description = "通过id删除AI 报告")
	@SysLog("通过id删除AI 报告")
	@DeleteMapping
	@PreAuthorize("@pms.hasPermission('knowledge_aiReportConf_del')")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiReportConfService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出报告配置到Excel 将选中的报告配置导出为Excel文件
	 * @param aiReportConf 查询条件
	 * @param ids 指定要导出的配置ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@PreAuthorize("@pms.hasPermission('knowledge_aiReportConf_export')")
	public List<AiReportConfEntity> export(AiReportConfEntity aiReportConf, Long[] ids) {
		return aiReportConfService
			.list(Wrappers.lambdaQuery(aiReportConf)
				.in(ArrayUtil.isNotEmpty(ids), AiReportConfEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 解析文档模板 解析报告模板中的标签，获取模板变量信息
	 * @param aiReportConf 包含模板信息的配置实体
	 * @return 模板变量解析结果
	 */
	@Operation(summary = "解析文档", description = "调用文档中的标签")
	@SysLog("解析文档")
	@PostMapping("/parse")
	@HasPermission("knowledge_aiReportConf_add")
	public R parse(@RequestBody AiReportConfEntity aiReportConf) {
		return aiReportConfService.parseDoc(aiReportConf);
	}

	/**
	 * 下载AI生成的报告文档
	 * @param completionsDTO 报告生成参数，包含模板变量值
	 * @return 包含生成结果的响应对象
	 */
	@Operation(summary = "生成文档", description = "生成标准化的报告文档HTML格式")
	@PostMapping("/generate/html")
	@HasPermission("knowledge_aiReportConf_add")
	public R<String> generate(@RequestBody AiReportCompletionsDTO completionsDTO) {
		return R.ok(aiReportConfService.generate(completionsDTO));
	}

}
