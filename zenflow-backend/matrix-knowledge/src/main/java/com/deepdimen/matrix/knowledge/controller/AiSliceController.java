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
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 知识切片控制器 管理知识库文档的切片数据，支持切片的增删改查和向量召回测试
 *
 * @author matrix
 * @date 2024-03-14 13:39:40
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiSlice")
@Tag(description = "aiSlice", name = "知识切片管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiSliceController {

	private final AiSliceService aiSliceService;

	private final AiDocumentService documentService;

	/**
	 * 分页查询知识切片列表 支持按文档ID、名称、状态、内容等条件查询，按命中次数倒序排列
	 * @param page 分页对象
	 * @param aiSlice 查询条件
	 * @param datasetId 数据集ID，用于筛选特定数据集下的切片
	 * @return 分页的切片列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiSlice_view")
	public R getAiSlicePage(@ParameterObject Page page, @ParameterObject AiSliceEntity aiSlice,
			@ParameterObject Long datasetId) {
		LambdaQueryWrapper<AiSliceEntity> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(Objects.nonNull(aiSlice.getDocumentId()), AiSliceEntity::getDocumentId, aiSlice.getDocumentId());
		wrapper.like(StrUtil.isNotBlank(aiSlice.getName()), AiSliceEntity::getName, aiSlice.getName());
		wrapper.like(StrUtil.isNotBlank(aiSlice.getSliceStatus()), AiSliceEntity::getSliceStatus,
				aiSlice.getSliceStatus());
		wrapper.like(StrUtil.isNotBlank(aiSlice.getContent()), AiSliceEntity::getContent, aiSlice.getContent());
		wrapper.orderByDesc(AiSliceEntity::getHitCount);

		if (Objects.nonNull(datasetId)) {
			List<Long> list = documentService
				.list(Wrappers.<AiDocumentEntity>lambdaQuery().eq(AiDocumentEntity::getDatasetId, datasetId))
				.stream()
				.map(AiDocumentEntity::getId)
				.toList();
			wrapper.in(CollUtil.isNotEmpty(list), AiSliceEntity::getDocumentId, list);
		}

		return R.ok(aiSliceService.page(page, wrapper));
	}

	/**
	 * 通过ID查询知识切片详情 获取单个切片的完整信息
	 * @param id 切片ID
	 * @return 切片详情
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@HasPermission("knowledge_aiSlice_view")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiSliceService.getById(id));
	}

	/**
	 * 执行向量召回测试 根据用户输入的文本在向量数据库中查找相似的知识切片
	 * @param requestDTO 召回请求参数，包含查询文本和召回数量等
	 * @return 召回的相似切片列表
	 */
	@Operation(summary = "召回测试", description = "通过用户输入的文本执行召回测试")
	@GetMapping("/recall")
	@HasPermission("knowledge_aiSlice_view")
	public R recall(AiSliceReCallRequestDTO requestDTO) {
		return R.ok(aiSliceService.recall(requestDTO));
	}

	/**
	 * 新增知识切片 手动添加知识切片到文档中
	 * @param aiSlice 切片实体
	 * @return 操作结果
	 */
	@Operation(summary = "新增知识切片", description = "新增知识切片")
	@SysLog("新增知识切片")
	@PostMapping
	@HasPermission("knowledge_aiSlice_add")
	public R save(@RequestBody AiSliceEntity aiSlice) {
		return R.ok(aiSliceService.save(aiSlice));
	}

	/**
	 * 修改知识切片 更新切片内容并重新生成向量
	 * @param aiSlice 切片实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改知识切片", description = "修改知识切片")
	@SysLog("修改知识切片")
	@PutMapping
	@HasPermission("knowledge_aiSlice_edit")
	public R updateById(@RequestBody AiSliceEntity aiSlice) {
		return R.ok(aiSliceService.updateSlice(aiSlice));
	}

	/**
	 * 批量删除知识切片 删除切片数据并同步删除向量数据库中的嵌入向量
	 * @param ids 切片ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除知识切片", description = "通过id删除知识切片")
	@SysLog("通过id删除知识切片")
	@DeleteMapping
	@HasPermission("knowledge_aiSlice_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiSliceService.removeSliceAndEmbeddingById(CollUtil.toList(ids)));
	}

	/**
	 * 导出知识切片到Excel 将选中的知识切片导出为Excel文件
	 * @param aiSlice 查询条件
	 * @param ids 指定要导出的切片ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiSlice_export")
	public List<AiSliceEntity> export(AiSliceEntity aiSlice, Long[] ids) {
		return aiSliceService
			.list(Wrappers.lambdaQuery(aiSlice)
				.in(ArrayUtil.isNotEmpty(ids), AiSliceEntity::getId, CollUtil.toList(ids)));
	}

}
