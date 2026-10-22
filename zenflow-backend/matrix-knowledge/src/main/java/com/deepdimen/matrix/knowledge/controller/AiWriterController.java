package com.deepdimen.matrix.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.RequestExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.knowledge.dto.AiWriterReponseDto;
import com.deepdimen.matrix.knowledge.dto.AiWriterRequestDto;
import com.deepdimen.matrix.knowledge.dto.Html2DocxReqDto;
import com.deepdimen.matrix.knowledge.entity.AiDocumentTemplateEntity;
import com.deepdimen.matrix.knowledge.service.AiWriterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能写作控制器
 * 
 * 整合了AI写作、文档模板管理和HTML转DOCX功能
 */
@RestController
@RequestMapping("/ai-writer")
@RequiredArgsConstructor
@Tag(description = "aiWriter", name = "智能写作管理")
@SecurityRequirement(name = "Authorization")
public class AiWriterController {

	private final AiWriterService aiWriterService;

	// ==================== AI写作相关方法 ====================

	/**
	 * 生成AI文档
	 * @param requestDto 请求参数DTO
	 * @return 返回文档生成结果的Flux流
	 */
	@Operation(summary = "生成AI文档", description = "根据模板生成AI文档")
	@PostMapping(value = "/generate/document", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<AiWriterReponseDto> generateDocument(@RequestBody AiWriterRequestDto requestDto) {
		return aiWriterService.generateDocument(requestDto);
	}

	/**
	 * 文本智能处理
	 * @param requestDto 请求参数DTO
	 * @return 返回文本处理结果的响应流
	 */
	@Operation(summary = "文本智能处理", description = "对文本进行各种AI操作")
	@PostMapping(value = "/process/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<AiWriterReponseDto> processText(@RequestBody AiWriterRequestDto requestDto) {
		return aiWriterService.processText(requestDto);
	}

	// ==================== 文档模板管理相关方法 ====================

	/**
	 * 分页查询文档模板
	 * @param page 分页对象
	 * @param templateQuery 查询条件
	 * @return 分页结果
	 */
	@Operation(summary = "分页查询文档模板", description = "分页查询文档模板")
	@GetMapping("/template/page")
	@HasPermission("aiDocumentTemplate_view")
	public R getTemplatePage(@ParameterObject Page page, @ParameterObject AiDocumentTemplateEntity templateQuery) {
		return aiWriterService.getTemplatePage(page, templateQuery);
	}

	/**
	 * 根据条件查询文档模板
	 * @param templateQuery 查询条件
	 * @return 模板列表
	 */
	@Operation(summary = "根据条件查询文档模板", description = "根据条件查询文档模板对象")
	@GetMapping("/template/details")
	@HasPermission("aiDocumentTemplate_view")
	public R details(@ParameterObject AiDocumentTemplateEntity templateQuery) {
		return aiWriterService.searchTemplates(templateQuery);
	}

	/**
	 * 创建文档模板
	 * @param template 模板实体
	 * @return 创建结果
	 */
	@Operation(summary = "创建文档模板", description = "创建新的AI文档模板")
	@SysLog("创建文档模板")
	@PostMapping("/template")
	@HasPermission("aiDocumentTemplate_add")
	public R createTemplate(@RequestBody AiDocumentTemplateEntity template) {
		return aiWriterService.createTemplate(template);
	}

	/**
	 * 更新文档模板
	 * @param template 模板实体
	 * @return 更新结果
	 */
	@Operation(summary = "更新文档模板", description = "更新现有的AI文档模板")
	@SysLog("更新文档模板")
	@PutMapping("/template")
	@HasPermission("aiDocumentTemplate_edit")
	public R updateTemplate(@RequestBody AiDocumentTemplateEntity template) {
		return aiWriterService.updateTemplate(template);
	}

	/**
	 * 批量删除文档模板
	 * @param templateIds 模板ID列表
	 * @return 删除结果
	 */
	@Operation(summary = "批量删除文档模板", description = "根据ID列表批量删除文档模板")
	@SysLog("批量删除文档模板")
	@DeleteMapping("/template")
	@HasPermission("aiDocumentTemplate_del")
	public R deleteTemplates(@RequestBody Long[] templateIds) {
		return aiWriterService.deleteTemplates(templateIds);
	}

	/**
	 * 导出模板数据到Excel
	 * @param templateQuery 查询条件
	 * @param exportIds 指定导出的ID列表
	 * @return Excel文件流
	 */
	@GetMapping("/template/export")
	@HasPermission("aiDocumentTemplate_export")
	public List<AiDocumentTemplateEntity> exportTemplatesToExcel(AiDocumentTemplateEntity templateQuery,
			Long[] exportIds) {
		return aiWriterService.exportTemplatesToExcel(templateQuery, exportIds);
	}

	/**
	 * 从Excel导入模板数据
	 * @param templateList Excel导入的模板实体列表
	 * @return 导入结果
	 */
	@PostMapping("/template/import")
	@HasPermission("aiDocumentTemplate_export")
	public R importTemplatesFromExcel(@RequestExcel List<AiDocumentTemplateEntity> templateList) {
		return aiWriterService.importTemplatesFromExcel(templateList);
	}

	// ==================== HTML转DOCX相关方法 ====================

	/**
	 * 将HTML内容转换为DOCX文档 接收POST请求，支持两种Content-Type： - text/plain：直接发送HTML字符串 -
	 * application/json：发送包含HTML字段的JSON对象
	 * @param conversionRequest 包含HTML内容和基础URL的请求对象
	 * @return 转换后的DOCX文档，作为附件下载
	 */
	@Operation(summary = "HTML转DOCX", description = "将HTML内容转换为DOCX文档")
	@PostMapping(value = "/convert/html-to-docx",
			consumes = { MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<byte[]> convertHtmlToDocx(@RequestBody Html2DocxReqDto conversionRequest) {
		return aiWriterService.convertHtmlToDocx(conversionRequest);
	}

}
