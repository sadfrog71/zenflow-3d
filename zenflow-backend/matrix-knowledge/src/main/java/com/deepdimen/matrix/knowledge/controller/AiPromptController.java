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
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.AiPromptDTO;
import com.deepdimen.matrix.knowledge.entity.AiPromptEntity;
import com.deepdimen.matrix.knowledge.service.AiPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI提示词控制器 管理AI提示词模板，支持用户自定义提示词和提示词优化功能
 *
 * @author matrix
 * @date 2024-03-20 16:29:05
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiPrompt")
@Tag(description = "aiPrompt", name = "提示词管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiPromptController {

	/**
	 * 管理员角色ID
	 */
	private static final Long ADMIN_ROLE_ID = 1L;

	private final AiPromptService aiPromptService;

	/**
	 * 查询提示词列表 支持按类型过滤：personal 仅查本人，enterprise 查租户内所有企业提示词，不传则合并返回
	 * @param aiPrompt 查询条件，act 字段作为关键词搜索，promptType 作为类型过滤
	 * @return 提示词列表
	 */
	@Operation(summary = "列表查询", description = "列表查询")
	@GetMapping("/list")
	@HasPermission("knowledge_aiPrompt_view")
	public R getAiPromptList(AiPromptEntity aiPrompt) {
		Long currentUserId = SecurityUtils.getUser().getId();
		String promptType = aiPrompt.getPromptType();
		String keyword = aiPrompt.getAct();

		LambdaQueryWrapper<AiPromptEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AiPromptEntity::getPromptStatus, YesNoEnum.YES.getCode());

		if ("enterprise".equals(promptType)) {
			// 企业提示词：租户内所有用户可见，依赖租户隔离
			wrapper.eq(AiPromptEntity::getPromptType, "enterprise");
		}
		else if ("personal".equals(promptType)) {
			// 个人提示词：仅本人可见
			wrapper.eq(AiPromptEntity::getPromptType, "personal");
			wrapper.eq(AiPromptEntity::getUserId, currentUserId);
		}
		else {
			// 未指定类型：合并返回企业提示词和本人个人提示词
			wrapper.and(w -> w.eq(AiPromptEntity::getPromptType, "enterprise")
				.or()
				.and(inner -> inner.eq(AiPromptEntity::getPromptType, "personal")
					.eq(AiPromptEntity::getUserId, currentUserId)));
		}

		if (StrUtil.isNotBlank(keyword)) {
			wrapper.and(w -> w.like(AiPromptEntity::getAct, keyword).or().like(AiPromptEntity::getPrompt, keyword));
		}

		// 按分类过滤
		wrapper.eq(StrUtil.isNotBlank(aiPrompt.getPromptCategory()), AiPromptEntity::getPromptCategory,
				aiPrompt.getPromptCategory());

		// 个人提示词优先，同类型按创建时间倒序
		wrapper.last("ORDER BY CASE WHEN prompt_type = 'personal' THEN 0 ELSE 1 END, create_time DESC");
		return R.ok(aiPromptService.list(wrapper));
	}

	/**
	 * 分页查询提示词列表（后台管理用），支持按类型过滤
	 * @param page 分页对象
	 * @param aiPrompt 查询条件
	 * @return 分页的提示词列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiPrompt_view")
	public R getAiPromptPage(@ParameterObject Page page, @ParameterObject AiPromptEntity aiPrompt) {
		// 后台管理接口，仅管理员可访问
		if (!SecurityUtils.getRoleIds().contains(ADMIN_ROLE_ID)) {
			return R.failed("无权限，仅管理员可访问");
		}
		LambdaQueryWrapper<AiPromptEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(StrUtil.isNotBlank(aiPrompt.getPromptType()), AiPromptEntity::getPromptType,
				aiPrompt.getPromptType());
		wrapper.like(StrUtil.isNotBlank(aiPrompt.getAct()), AiPromptEntity::getAct, aiPrompt.getAct());
		wrapper.eq(StrUtil.isNotBlank(aiPrompt.getPromptCategory()), AiPromptEntity::getPromptCategory,
				aiPrompt.getPromptCategory());
		// 后台管理展示所有状态的提示词，不过滤 promptStatus
		return R.ok(aiPromptService.page(page, wrapper));
	}

	/**
	 * 通过ID查询提示词详情 获取单个提示词的完整信息
	 * @param id 提示词ID
	 * @return 提示词详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@HasPermission("knowledge_aiPrompt_view")
	public R getById(@PathVariable("id") Long id) {
		AiPromptEntity entity = aiPromptService.getById(id);
		if (entity == null) {
			return R.failed("提示词不存在");
		}
		if ("personal".equals(entity.getPromptType()) && !SecurityUtils.getUser().getId().equals(entity.getUserId())) {
			return R.failed("无权限查看他人的个人提示词");
		}
		return R.ok(entity);
	}

	/**
	 * 新增提示词 为当前用户创建新的提示词模板，企业提示词仅管理员可创建
	 * @param aiPrompt 提示词实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增提示词", description = "新增提示词")
	@SysLog("新增提示词")
	@PostMapping
	@HasPermission("knowledge_aiPrompt_add")
	public R save(@RequestBody AiPromptEntity aiPrompt) {
		// promptType 未传时默认为个人提示词
		if (StrUtil.isBlank(aiPrompt.getPromptType())) {
			aiPrompt.setPromptType("personal");
		}
		if (!List.of("personal", "enterprise").contains(aiPrompt.getPromptType())) {
			return R.failed("提示词类型不合法，仅支持 personal/enterprise");
		}
		if ("enterprise".equals(aiPrompt.getPromptType()) && !SecurityUtils.getRoleIds().contains(ADMIN_ROLE_ID)) {
			return R.failed("无权限创建企业提示词，仅管理员可操作");
		}
		// promptStatus 未传时默认为有效
		if (StrUtil.isBlank(aiPrompt.getPromptStatus())) {
			aiPrompt.setPromptStatus(YesNoEnum.YES.getCode());
		}
		// promptCategory 未传时默认为未分类
		if (StrUtil.isBlank(aiPrompt.getPromptCategory())) {
			aiPrompt.setPromptCategory("uncategorized");
		}
		// 个人提示词记录所有者，企业提示词记录创建者（仅供审计，查询时不依赖此字段）
		aiPrompt.setUserId(SecurityUtils.getUser().getId());
		aiPrompt.setUseCount(0);
		return R.ok(aiPromptService.save(aiPrompt));
	}

	/**
	 * 修改提示词 更新现有的提示词内容或状态，企业提示词仅管理员可编辑
	 * @param aiPrompt 提示词实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改提示词", description = "修改提示词")
	@SysLog("修改提示词")
	@PutMapping
	@HasPermission("knowledge_aiPrompt_edit")
	public R updateById(@RequestBody AiPromptEntity aiPrompt) {
		if (aiPrompt.getId() == null) {
			return R.failed("提示词ID不能为空");
		}
		AiPromptEntity existing = aiPromptService.getById(aiPrompt.getId());
		if (existing == null) {
			return R.failed("提示词不存在");
		}
		// 基于数据库原始类型判断权限，禁止前端篡改 promptType 和 userId
		aiPrompt.setPromptType(null);
		aiPrompt.setUserId(null);
		aiPrompt.setUseCount(null);
		if ("enterprise".equals(existing.getPromptType()) && !SecurityUtils.getRoleIds().contains(ADMIN_ROLE_ID)) {
			return R.failed("无权限编辑企业提示词，仅管理员可操作");
		}
		if ("personal".equals(existing.getPromptType())
				&& !SecurityUtils.getUser().getId().equals(existing.getUserId())) {
			return R.failed("无权限修改他人的个人提示词");
		}
		return R.ok(aiPromptService.updateById(aiPrompt));
	}

	/**
	 * 批量删除提示词 支持批量删除不再使用的提示词
	 * @param ids 提示词ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除提示词", description = "通过id删除提示词")
	@SysLog("通过id删除提示词")
	@DeleteMapping
	@HasPermission("knowledge_aiPrompt_del")
	public R removeByIds(@RequestBody Long[] ids) {
		if (ArrayUtil.isEmpty(ids)) {
			return R.ok();
		}
		Long currentUserId = SecurityUtils.getUser().getId();
		List<AiPromptEntity> existingList = aiPromptService.listByIds(CollUtil.toList(ids));
		for (AiPromptEntity existing : existingList) {
			if ("enterprise".equals(existing.getPromptType()) && !SecurityUtils.getRoleIds().contains(ADMIN_ROLE_ID)) {
				return R.failed("无权限删除企业提示词，仅管理员可操作");
			}
			if ("personal".equals(existing.getPromptType()) && !currentUserId.equals(existing.getUserId())) {
				return R.failed("无权限删除他人的个人提示词");
			}
		}
		return R.ok(aiPromptService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出提示词到Excel 将选中的提示词导出为Excel文件
	 * @param ids 指定要导出的提示词ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiPrompt_export")
	public List<AiPromptEntity> export(Long[] ids) {
		Long currentUserId = SecurityUtils.getUser().getId();
		LambdaQueryWrapper<AiPromptEntity> wrapper = Wrappers.lambdaQuery();
		// 只能导出企业提示词和本人个人提示词
		wrapper.and(w -> w.eq(AiPromptEntity::getPromptType, "enterprise")
			.or()
			.and(inner -> inner.eq(AiPromptEntity::getPromptType, "personal")
				.eq(AiPromptEntity::getUserId, currentUserId)));
		wrapper.in(ArrayUtil.isNotEmpty(ids), AiPromptEntity::getId, CollUtil.toList(ids));
		return aiPromptService.list(wrapper);
	}

	/**
	 * 递增提示词使用次数 每次在对话中使用提示词时调用，use_count 原子递增 +1
	 * @param id 提示词ID
	 * @return 操作结果
	 */
	@Operation(summary = "递增使用次数", description = "递增提示词使用次数")
	@PutMapping("/{id}/use")
	@HasPermission("knowledge_aiPrompt_view")
	public R usePrompt(@PathVariable("id") Long id) {
		AiPromptEntity existing = aiPromptService.getById(id);
		if (existing == null) {
			return R.failed("提示词不存在");
		}
		Long currentUserId = SecurityUtils.getUser().getId();
		if ("personal".equals(existing.getPromptType()) && !currentUserId.equals(existing.getUserId())) {
			return R.failed("无权限操作他人的个人提示词");
		}
		aiPromptService.update(Wrappers.<AiPromptEntity>lambdaUpdate()
			.eq(AiPromptEntity::getId, id)
			.setSql("use_count = use_count + 1"));
		return R.ok();
	}

	/**
	 * 使用AI优化提示词 调用大模型对用户输入的提示词进行优化，提高提示词质量
	 * @param aiPrompt 包含原始提示词的请求对象
	 * @return 优化后的提示词内容
	 */
	@Operation(summary = "调用大模型优化提示词", description = "OpenAI调用大模型优化提示词")
	@PostMapping("/optimize")
	@HasPermission("knowledge_aiPrompt_edit")
	public R<String> optimizePrompt(@RequestBody AiPromptDTO aiPrompt) {
		return R.ok(aiPromptService.optimizePrompt(aiPrompt));
	}

}
