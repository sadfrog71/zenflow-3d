package com.deepdimen.matrix.knowledge.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryMoveDTO;
import com.deepdimen.matrix.knowledge.service.AiDocumentCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/aiDocumentCategory")
public class AiDocumentCategoryController {

	private final AiDocumentCategoryService categoryService;

	@GetMapping("/list")
	@HasPermission("knowledge_aiDocument_view")
	public R list(@RequestParam Long datasetId) {
		return R.ok(categoryService.list(datasetId));
	}

	@PostMapping
	@SysLog("新增文档分类")
	@HasPermission("knowledge_aiDocument_edit")
	public R create(@Valid @RequestBody AiDocumentCategoryDTO request) {
		return R.ok(categoryService.create(request));
	}

	@PutMapping
	@SysLog("修改文档分类")
	@HasPermission("knowledge_aiDocument_edit")
	public R update(@Valid @RequestBody AiDocumentCategoryDTO request) {
		return R.ok(categoryService.update(request));
	}

	@DeleteMapping("/{id}")
	@SysLog("删除空文档分类")
	@HasPermission("knowledge_aiDocument_edit")
	public R delete(@PathVariable Long id, @RequestParam Long datasetId) {
		return R.ok(categoryService.delete(datasetId, id));
	}

	@PutMapping("/documents")
	@SysLog("批量移动文档分类")
	@HasPermission("knowledge_aiDocument_edit")
	public R moveDocuments(@Valid @RequestBody AiDocumentCategoryMoveDTO request) {
		return R.ok(categoryService.moveDocuments(request));
	}

}
