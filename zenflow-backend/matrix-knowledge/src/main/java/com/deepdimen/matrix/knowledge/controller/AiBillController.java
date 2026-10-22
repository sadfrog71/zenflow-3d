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
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import com.deepdimen.matrix.knowledge.service.AiBillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI账单控制器 提供AI服务使用账单的管理功能，包括账单的查询、新增、修改、删除和导出等操作
 *
 * @author matrix
 * @date 2024-03-26 11:26:59
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiBill")
@Tag(description = "aiBill", name = "账单管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiBillController {

	private final AiBillService aiBillService;

	/**
	 * 分页查询账单列表 支持按用户名和Token类型进行筛选
	 * @param page 分页对象
	 * @param aiBill 账单查询条件
	 * @return 分页账单列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiBill_view")
	public R getAiBillPage(@ParameterObject Page page, @ParameterObject AiBillEntity aiBill) {
		LambdaQueryWrapper<AiBillEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(StrUtil.isNotBlank(aiBill.getUsername()), AiBillEntity::getUsername, aiBill.getUsername());
		wrapper.eq(StrUtil.isNotBlank(aiBill.getTokenType()), AiBillEntity::getTokenType, aiBill.getTokenType());
		return R.ok(aiBillService.page(page, wrapper));
	}

	/**
	 * 通过ID查询账单详情 根据账单ID获取单条账单的详细信息
	 * @param id 账单ID
	 * @return 账单详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@HasPermission("knowledge_aiBill_view")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiBillService.getById(id));
	}

	/**
	 * 新增账单记录 创建新的AI服务使用账单记录
	 * @param aiBill 账单实体对象
	 * @return 新增结果
	 */
	@Operation(summary = "新增账单", description = "新增账单")
	@SysLog("新增账单")
	@PostMapping
	@HasPermission("knowledge_aiBill_add")
	public R save(@RequestBody AiBillEntity aiBill) {
		return R.ok(aiBillService.save(aiBill));
	}

	/**
	 * 修改账单信息 更新已存在的账单记录信息
	 * @param aiBill 账单实体对象
	 * @return 修改结果
	 */
	@Operation(summary = "修改账单", description = "修改账单")
	@SysLog("修改账单")
	@PutMapping
	@HasPermission("knowledge_aiBill_edit")
	public R updateById(@RequestBody AiBillEntity aiBill) {
		return R.ok(aiBillService.updateById(aiBill));
	}

	/**
	 * 批量删除账单 根据ID列表批量删除账单记录
	 * @param ids 账单ID数组
	 * @return 删除结果
	 */
	@Operation(summary = "通过id删除账单", description = "通过id删除账单")
	@SysLog("通过id删除账单")
	@DeleteMapping
	@HasPermission("knowledge_aiBill_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiBillService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出账单Excel表格 根据查询条件或指定ID导出账单数据到Excel文件
	 * @param aiBill 查询条件
	 * @param ids 指定导出的账单ID数组（可选）
	 * @return 账单数据列表
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiBill_export")
	public List<AiBillEntity> export(AiBillEntity aiBill, Long[] ids) {
		return aiBillService.list(Wrappers.lambdaQuery(aiBill)
			.in(ArrayUtil.isNotEmpty(ids), AiBillEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 统计近30天账单汇总 获取最近30天内的账单统计数据，包括总费用、使用量等汇总信息
	 * @return 账单统计结果
	 */
	@GetMapping("/sum")
	public R getBillSum() {
		return R.ok(aiBillService.getBillSum());
	}

}
