package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.knowledge.dto.AiCrawleDocumentDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiWordCloudService;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.source.FileSourceTypeHandler;
import com.deepdimen.matrix.knowledge.support.handler.source.UploadSourceTypeHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识文档管理控制器 提供文档的上传、管理、切片、向量化等功能，支持多种文档来源类型
 *
 * @author matrix
 * @date 2024-03-14 13:38:59
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiDocument")
@Tag(description = "aiDocument", name = "知识文档管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiDocumentController {

	private final List<FileSourceTypeHandler> sourceTypeHandlerList;

	private final UploadSourceTypeHandler uploadSourceTypeHandler;

	private final AiDocumentService aiDocumentService;

	private final AiWordCloudService aiWordCloudService;

	private final com.deepdimen.matrix.knowledge.service.AiDocumentCategoryService categoryService;

	/**
	 * 分页查询知识文档 支持按数据集ID、摘要状态、切片状态、来源类型和文档名称等条件过滤
	 * @param page 分页对象
	 * @param aiDocument 知识文档查询条件
	 * @return 分页后的文档列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiDocument_view")
	public R getAiDocumentPage(@ParameterObject Page page, @ParameterObject AiDocumentEntity aiDocument) {
		return R.ok(aiDocumentService.getDocumentPage(page, aiDocument));
	}

	/**
	 * 通过ID查询知识文档详细信息
	 * @param id 文档ID
	 * @return 文档详细信息
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@HasPermission("knowledge_aiDocument_view")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(aiDocumentService.getById(id));
	}

	/**
	 * 新增知识文档 支持上传文件和其他来源类型（如URL、文本等）的文档添加
	 * @param aiDocumentDTO 知识文档DTO，包含文档信息和来源类型
	 * @return 操作结果
	 */
	@Operation(summary = "新增知识文档", description = "新增知识文档")
	@SysLog("新增知识文档")
	@PostMapping
	@HasPermission("knowledge_aiDocument_add")
	public R save(@RequestBody AiDocumentDTO aiDocumentDTO) {
		// 上传文件类型
		if (SourceTypeEnums.UPLOAD.getType().equals(aiDocumentDTO.getSourceType())) {
			List<AiDocumentEntity> files = aiDocumentDTO.getFiles();
			if (files == null || files.isEmpty() || files.size() > 500
					|| files.stream().anyMatch(java.util.Objects::isNull)) {
				throw new IllegalArgumentException("请选择1至500个上传文件");
			}
			for (AiDocumentEntity file : files) {
				AiDocumentDTO single = new AiDocumentDTO();
				BeanUtils.copyProperties(aiDocumentDTO, single);
				single.setFiles(List.of(file));
				categoryService.prepareUpload(uploadSourceTypeHandler, single);
				aiDocumentService.save(uploadSourceTypeHandler, single);
			}
			return R.ok();
		}

		FileSourceTypeHandler handler = sourceTypeHandlerList.stream()
			.filter(candidate -> candidate.supports(aiDocumentDTO.getSourceType()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("不支持的文档来源类型"));
		categoryService.prepareUpload(handler, aiDocumentDTO);
		aiDocumentService.save(handler, aiDocumentDTO);
		return R.ok();
	}

	/**
	 * 修改知识文档信息
	 * @param aiDocument 知识文档实体
	 * @return 操作结果
	 */
	@Operation(summary = "修改知识文档", description = "修改知识文档")
	@SysLog("修改知识文档")
	@PutMapping
	@HasPermission("knowledge_aiDocument_edit")
	public R updateById(@RequestBody AiDocumentEntity aiDocument) {
		categoryService.validateDocumentUpdate(aiDocument);
		return R.ok(aiDocumentService.updateById(aiDocument));
	}

	/**
	 * 批量删除知识文档 同时删除文档及其关联的切片数据
	 * @param ids 文档ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除知识文档", description = "通过id删除知识文档")
	@SysLog("通过id删除知识文档")
	@DeleteMapping
	@HasPermission("knowledge_aiDocument_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(aiDocumentService.removeDocumentAndSliceBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出知识文档信息到Excel
	 * @param aiDocument 查询条件
	 * @param ids 指定导出的文档ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiDocument_export")
	public List<AiDocumentEntity> export(AiDocumentEntity aiDocument, Long[] ids) {
		categoryService.applyFilter(aiDocument);
		List<Long> categoryIds = aiDocument.getCategoryIds();
		// 父分类导出包含后代，不能同时留下实体的父分类等值条件。
		aiDocument.setCategoryId(null);
		return aiDocumentService.list(Wrappers.lambdaQuery(aiDocument)
			.isNull(Boolean.TRUE.equals(aiDocument.getUncategorized()), AiDocumentEntity::getCategoryId)
			.in(CollUtil.isNotEmpty(categoryIds), AiDocumentEntity::getCategoryId, categoryIds)
			.in(ArrayUtil.isNotEmpty(ids), AiDocumentEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 重新切片文档 对已存在的文档重新执行切片处理，用于处理失败或需要更新的文档
	 * @param aiDocument 包含文档信息的DTO
	 * @return 操作结果
	 */
	@Operation(summary = "重新切片文档", description = "重新切片文档")
	@SysLog("重新切片文档")
	@PostMapping("/retry/slice")
	@HasPermission("knowledge_aiDocument_del")
	public R retry(@RequestBody AiDocumentDTO aiDocument) {
		aiDocumentService.retryDocument(aiDocument);
		return R.ok();
	}

	/**
	 * 文档向量化处理 将文档内容转换为向量表示，用于后续的语义搜索和相似度计算
	 * @param fileDTO 包含文件名称的DTO对象
	 * @return 向量化处理结果
	 */
	@SneakyThrows
	@PostMapping("/embed")
	public R embedDocument(@RequestBody ChatMessageDTO.FileDTO fileDTO) {
		return aiDocumentService.embedDocument(fileDTO.getName());
	}

	/**
	 * 抓取网页文档 从指定的URL或其他来源抓取内容，并将其保存为知识文档
	 * @param crawleDocumentDTO 包含文档来源类型和其他必要信息的DTO对象
	 * @return 抓取结果
	 */
	@PostMapping("/crawle")
	@HasPermission("knowledge_aiDocument_view")
	public R crawleDocument(@RequestBody AiCrawleDocumentDTO crawleDocumentDTO) {
		return R.ok(aiDocumentService.crawleDocument(crawleDocumentDTO));
	}

	/**
	 * 生成文档词云图
	 * @param documentId 文档ID
	 * @param width 词云图宽度，默认800
	 * @param height 词云图高度，默认600
	 * @return PNG格式的词云图片流
	 * @throws IllegalArgumentException 当参数不合法时抛出
	 * @throws Exception 当生成词云图过程中发生错误时抛出
	 */
	@Inner(value = false)
	@SneakyThrows
	@Operation(summary = "生成文档词云图", description = "根据文档内容生成中文词云图")
	@GetMapping("/wordcloud")
	public ResponseEntity<byte[]> generateWordCloud(Long documentId,
			@RequestParam(value = "width", defaultValue = "800") Integer width,
			@RequestParam(value = "height", defaultValue = "600") Integer height) {

		// 生成词云图字节数组
		byte[] wordCloudBytes = aiWordCloudService.generateWordCloud(documentId, new java.awt.Dimension(width, height));

		// 设置响应头
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(wordCloudBytes.length);
		headers.set("Cache-Control", "max-age=3600"); // 缓存1小时
		headers.set("Content-Disposition", "inline; filename" + documentId + ".png");

		return ResponseEntity.ok().headers(headers).body(wordCloudBytes);
	}

	/**
	 * 获取指定文档的图谱数据
	 * @param documentId 文档ID
	 * @return 包含图谱数据的响应对象
	 */
	@Operation(summary = "获取文档图谱数据", description = "获取指定文档的图谱数据")
	@GetMapping("/graphData/{documentId}")
	public R getGraphData(@PathVariable Long documentId) {
		return aiDocumentService.getGraphData(documentId);
	}

	/**
	 * 获取指定文档的图谱节点数据
	 * @param datasetId 数据集ID
	 * @param nodeId 节点ID
	 * @return 包含图谱节点数据的响应结果
	 */
	@Operation(summary = "获取图谱节点数据", description = "获取指定文档的图谱节点数据")
	@GetMapping("/graphNodeData/{datasetId}/{nodeId}")
	public R getGraphNodeData(@PathVariable Long datasetId, @PathVariable Long nodeId) {
		return aiDocumentService.getGraphNodeData(datasetId, nodeId);
	}

}
