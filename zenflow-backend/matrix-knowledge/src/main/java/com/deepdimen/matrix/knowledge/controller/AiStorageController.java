package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.img.ImgUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.dto.AiStorageDTO;
import com.deepdimen.matrix.knowledge.entity.AiStorageFileEntity;
import com.deepdimen.matrix.knowledge.entity.AiStorageGroupEntity;
import com.deepdimen.matrix.knowledge.service.AiStorageFileService;
import com.deepdimen.matrix.knowledge.service.AiStorageGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.util.Map;
import java.util.Objects;

/**
 * AI存储管理统一控制器
 *
 * @author matrix
 * @date 2025-09-30
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/storage")
@Tag(description = "aiStorage", name = "AI存储管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiStorageController {

	private final AiStorageFileService aiStorageFileService;

	private final AiStorageGroupService aiStorageGroupService;

	// ==================== 文件管理接口 ====================

	/**
	 * 分页查询文件列表
	 * @param page 分页对象
	 * @param aiStorageFile 查询条件
	 * @return 分页结果
	 */
	@Operation(summary = "分页查询文件列表", description = "分页查询文件列表")
	@GetMapping("/files")
	@HasPermission("knowledge_aiStorageFile_view")
	public R getFileList(@ParameterObject Page page, @ParameterObject AiStorageFileEntity aiStorageFile) {
		LambdaQueryWrapper<AiStorageFileEntity> wrapper = Wrappers.lambdaQuery();
		// 可以根据前端传递的参数添加查询条件
		if (aiStorageFile.getGroupId() != null) {
			wrapper.eq(AiStorageFileEntity::getGroupId, aiStorageFile.getGroupId());
		}
		if (aiStorageFile.getFileType() != null) {
			wrapper.eq(AiStorageFileEntity::getFileType, aiStorageFile.getFileType());
		}
		if (aiStorageFile.getFileName() != null) {
			wrapper.like(AiStorageFileEntity::getFileName, aiStorageFile.getFileName());
		}
		return R.ok(aiStorageFileService.page(page, wrapper));
	}

	/**
	 * 获取文件详情
	 * @param id 文件ID
	 * @return 文件详情
	 */
	@Operation(summary = "获取文件详情", description = "获取文件详情")
	@GetMapping("/file/{id}")
	@HasPermission("knowledge_aiStorageFile_view")
	public R getFileDetail(@PathVariable Long id) {
		return R.ok(aiStorageFileService.getById(id));
	}

	/**
	 * 保存文件信息
	 * @param aiStorageFileEntity 文件信息实体
	 * @return 保存结果
	 */
	@Operation(summary = "新增文件信息", description = "新增文件信息")
	@SysLog("新增文件信息")
	@PostMapping("/file")
	@HasPermission("knowledge_aiStorageFile_add")
	public R saveFile(@RequestBody AiStorageFileEntity aiStorageFileEntity) {
		// 默认是未处理状态
		aiStorageFileEntity.setAiAnalyzed(YesNoEnum.NO.getCode());
		boolean saved = aiStorageFileService.save(aiStorageFileEntity);
		aiStorageFileService.embedFile(aiStorageFileEntity.getId());
		return R.ok(saved);
	}

	/**
	 * 更新文件信息
	 * @param id 文件ID
	 * @param aiStorageFile 文件信息
	 * @return 更新结果
	 */
	@Operation(summary = "更新文件信息", description = "更新文件信息")
	@SysLog("更新文件信息")
	@PutMapping("/file/{id}")
	@HasPermission("knowledge_aiStorageFile_edit")
	public R updateFile(@PathVariable Long id, @RequestBody AiStorageFileEntity aiStorageFile) {
		aiStorageFile.setId(id);
		return R.ok(aiStorageFileService.updateById(aiStorageFile));
	}

	/**
	 * 删除单个文件
	 * @param id 文件ID
	 * @return 删除结果
	 */
	@Operation(summary = "删除文件", description = "删除文件")
	@SysLog("删除文件")
	@DeleteMapping("/file/{id}")
	@HasPermission("knowledge_aiStorageFile_del")
	public R deleteFile(@PathVariable Long id) {
		return R.ok(aiStorageFileService.removeById(id));
	}

	/**
	 * 批量删除文件
	 * @param dto 批量删除请求参数
	 * @return 删除结果
	 */
	@Operation(summary = "批量删除文件", description = "批量删除文件")
	@SysLog("批量删除文件")
	@DeleteMapping("/files/batch")
	@HasPermission("knowledge_aiStorageFile_del")
	public R batchDeleteFiles(@RequestBody AiStorageDTO dto) {
		return R.ok(aiStorageFileService.removeBatchByIds(dto.getIds()));
	}

	/**
	 * 移动文件到分组
	 * @param id 文件ID
	 * @param dto 移动文件请求参数
	 * @return 移动结果
	 */
	@Operation(summary = "移动文件到分组", description = "移动文件到分组")
	@SysLog("移动文件到分组")
	@PutMapping("/file/{id}/move")
	@HasPermission("knowledge_aiStorageFile_edit")
	public R moveFile(@PathVariable Long id, @RequestBody AiStorageDTO dto) {
		AiStorageFileEntity file = new AiStorageFileEntity();
		file.setId(id);
		file.setGroupId(dto.getGroupId());
		return R.ok(aiStorageFileService.updateById(file));
	}

	/**
	 * 批量移动文件
	 * @param dto 批量移动文件请求参数
	 * @return 移动结果
	 */
	@Operation(summary = "批量移动文件", description = "批量移动文件")
	@SysLog("批量移动文件")
	@PutMapping("/files/batch-move")
	@HasPermission("knowledge_aiStorageFile_edit")
	public R batchMoveFiles(@RequestBody AiStorageDTO dto) {
		// 批量更新文件分组
		dto.getIds().forEach(id -> {
			AiStorageFileEntity file = new AiStorageFileEntity();
			file.setId(id);
			file.setGroupId(dto.getGroupId());
			aiStorageFileService.updateById(file);
		});
		return R.ok();
	}

	/**
	 * 向量搜索文件
	 * @param keyword 搜索关键词
	 * @return 搜索结果
	 */
	@Operation(summary = "向量搜索文件", description = "向量搜索文件")
	@GetMapping("/search")
	@HasPermission("knowledge_aiStorageFile_view")
	public R searchFiles(String keyword) {
		return R.ok(aiStorageFileService.searchByVector(keyword));
	}

	/**
	 * 图片搜索（以图搜图）
	 * @param imageFile 图片文件
	 * @return 搜索结果
	 */
	@SneakyThrows
	@Operation(summary = "图片搜索", description = "图片搜索（以图搜图）")
	@PostMapping("/search/image")
	@HasPermission("knowledge_aiStorageFile_view")
	public R searchByImage(@RequestPart("imageFile") MultipartFile imageFile) {
		String base64DataUri = ImgUtil.toBase64DataUri(ImageIO.read(imageFile.getInputStream()),
				ImgUtil.IMAGE_TYPE_PNG);
		return R.ok(aiStorageFileService.searchByImage(base64DataUri));
	}

	/**
	 * 触发AI分析
	 * @param id 文件ID
	 * @return 分析结果
	 */
	@Operation(summary = "触发AI分析", description = "触发AI分析")
	@SysLog("触发AI分析")
	@PostMapping("/file/{id}/analyze")
	@HasPermission("knowledge_aiStorageFile_edit")
	public R analyzeFile(@PathVariable Long id) {
		AiStorageFileEntity file = new AiStorageFileEntity();
		file.setId(id);
		aiStorageFileService.updateById(file);
		return R.ok("AI分析已触发");
	}

	/**
	 * 获取相似文件
	 * @param id 文件ID
	 * @param limit 返回数量限制
	 * @return 相似文件列表
	 */
	@Operation(summary = "获取相似文件", description = "获取相似文件")
	@GetMapping("/file/{id}/similar")
	@HasPermission("knowledge_aiStorageFile_view")
	public R getSimilarFiles(@PathVariable Long id, @RequestParam(defaultValue = "10") Integer limit) {
		return R.ok("相似文件查找功能待实现");
	}

	// ==================== 分组管理接口 ====================

	/**
	 * 获取分组列表（树形结构）
	 * @param aiStorageGroup 查询条件
	 * @return 分组列表
	 */
	@Operation(summary = "获取分组列表", description = "获取分组列表（树形结构）")
	@GetMapping("/groups")
	@HasPermission("knowledge_aiStorageGroup_view")
	public R getGroupList(@ParameterObject AiStorageGroupEntity aiStorageGroup) {
		return R.ok(aiStorageGroupService.list(Wrappers.query(aiStorageGroup)));
	}

	/**
	 * 获取分组详情
	 * @param id 分组ID
	 * @return 分组详情
	 */
	@Operation(summary = "获取分组详情", description = "获取分组详情")
	@GetMapping("/group/{id}")
	@HasPermission("knowledge_aiStorageGroup_view")
	public R getGroupDetail(@PathVariable Long id) {
		return R.ok(aiStorageGroupService.getById(id));
	}

	/**
	 * 创建分组
	 * @param aiStorageGroup 分组信息
	 * @return 创建结果
	 */
	@Operation(summary = "创建分组", description = "创建分组")
	@SysLog("创建分组")
	@PostMapping("/group")
	@HasPermission("knowledge_aiStorageGroup_add")
	public R addGroup(@RequestBody AiStorageGroupEntity aiStorageGroup) {
		return R.ok(aiStorageGroupService.save(aiStorageGroup));
	}

	/**
	 * 更新分组
	 * @param id 分组ID
	 * @param aiStorageGroup 分组信息
	 * @return 更新结果
	 */
	@Operation(summary = "更新分组", description = "更新分组")
	@SysLog("更新分组")
	@PutMapping("/group/{id}")
	@HasPermission("knowledge_aiStorageGroup_edit")
	public R updateGroup(@PathVariable Long id, @RequestBody AiStorageGroupEntity aiStorageGroup) {
		aiStorageGroup.setId(id);
		return R.ok(aiStorageGroupService.updateById(aiStorageGroup));
	}

	/**
	 * 删除分组
	 * @param id 分组ID
	 * @return 删除结果
	 */
	@Operation(summary = "删除分组", description = "删除分组")
	@SysLog("删除分组")
	@DeleteMapping("/group/{id}")
	@HasPermission("knowledge_aiStorageGroup_del")
	public R deleteGroup(@PathVariable Long id) {
		return R.ok(aiStorageGroupService.removeById(id));
	}

	/**
	 * 获取分组统计信息
	 * @param id 分组ID（可选）
	 * @return 统计信息
	 */
	@Operation(summary = "获取分组统计信息", description = "获取分组统计信息")
	@GetMapping("/group/stats")
	@HasPermission("knowledge_aiStorageGroup_view")
	public R getGroupStats(@RequestParam(required = false) Long id) {
		long count = aiStorageFileService.count(Wrappers.<AiStorageFileEntity>lambdaQuery()
			.eq(Objects.nonNull(id), AiStorageFileEntity::getGroupId, id));
		return R.ok(Map.of("total", count));
	}

}
