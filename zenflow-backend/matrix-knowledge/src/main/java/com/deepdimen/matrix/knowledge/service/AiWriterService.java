package com.deepdimen.matrix.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiWriterReponseDto;
import com.deepdimen.matrix.knowledge.dto.AiWriterRequestDto;
import com.deepdimen.matrix.knowledge.dto.Html2DocxReqDto;
import com.deepdimen.matrix.knowledge.entity.AiDocumentTemplateEntity;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能写作服务接口
 * 
 * 整合了AI写作、文档模板管理和HTML转DOCX功能
 */
public interface AiWriterService extends IService<AiDocumentTemplateEntity> {

	// ==================== AI写作相关方法 ====================

	/**
	 * 生成AI文档
	 * @param requestDto 请求参数DTO
	 * @return 返回文档生成结果的Flux流
	 */
	Flux<AiWriterReponseDto> generateDocument(AiWriterRequestDto requestDto);

	/**
	 * 文本智能处理
	 * @param requestDto 请求参数DTO
	 * @return 返回文本处理结果的响应流
	 */
	Flux<AiWriterReponseDto> processText(AiWriterRequestDto requestDto);

	// ==================== 文档模板管理相关方法 ====================

	/**
	 * 分页查询文档模板
	 * @param page 分页对象
	 * @param templateQuery 查询条件
	 * @return 分页结果
	 */
	R getTemplatePage(Page page, AiDocumentTemplateEntity templateQuery);

	/**
	 * 根据条件查询文档模板
	 * @param templateQuery 查询条件
	 * @return 模板列表
	 */
	R searchTemplates(AiDocumentTemplateEntity templateQuery);

	/**
	 * 创建文档模板
	 * @param template 模板实体
	 * @return 创建结果
	 */
	R createTemplate(AiDocumentTemplateEntity template);

	/**
	 * 更新文档模板
	 * @param template 模板实体
	 * @return 更新结果
	 */
	R updateTemplate(AiDocumentTemplateEntity template);

	/**
	 * 批量删除文档模板
	 * @param templateIds 模板ID列表
	 * @return 删除结果
	 */
	R deleteTemplates(Long[] templateIds);

	/**
	 * 导出模板数据到Excel
	 * @param templateQuery 查询条件
	 * @param exportIds 指定导出的ID列表
	 * @return Excel文件流
	 */
	List<AiDocumentTemplateEntity> exportTemplatesToExcel(AiDocumentTemplateEntity templateQuery, Long[] exportIds);

	/**
	 * 从Excel导入模板数据
	 * @param templateList 模板实体列表
	 * @return 导入结果
	 */
	R importTemplatesFromExcel(List<AiDocumentTemplateEntity> templateList);

	// ==================== HTML转DOCX相关方法 ====================

	/**
	 * 将HTML内容转换为DOCX文档
	 * @param conversionRequest 包含HTML内容和基础URL的请求对象
	 * @return 转换后的DOCX文档，作为附件下载
	 */
	ResponseEntity<byte[]> convertHtmlToDocx(Html2DocxReqDto conversionRequest);

}
