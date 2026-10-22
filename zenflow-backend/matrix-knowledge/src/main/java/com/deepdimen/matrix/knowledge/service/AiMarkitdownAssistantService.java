package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.AiMarkitdownDTO;
import com.deepdimen.matrix.knowledge.dto.MarkitdownResponseDTO;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.PostExchange;

/**
 * AI Markitdown 文档解析服务接口
 * <p>
 * 集成Markitdown服务，提供文档到Markdown格式的智能转换功能。 支持PDF、Word、PPT等多种文档格式的解析和结构化提取。
 *
 * @author caoyuan
 * @date 2024/12/22
 */
public interface AiMarkitdownAssistantService {

	/**
	 * 上传文档并解析为Markdown格式
	 * <p>
	 * 接收文档文件，调用Markitdown服务进行智能解析， 将文档内容转换为结构化的Markdown格式，保留文档的格式、
	 * 表格、图片等元素，便于后续的AI处理和知识提取。
	 * @param file 需要解析的文档文件，支持PDF、DOCX、PPTX等格式
	 * @param aiMarkitdownDTO 解析配置参数，包含语言、OCR选项、表格识别等设置
	 * @return {@link MarkitdownResponseDTO} 解析结果，包含Markdown文本、元数据、解析状态等信息
	 * @apiNote 当文档格式不支持或解析失败时，可能返回422状态码
	 */
	@PostExchange(value = "/upload", contentType = "multipart/form-data")
	MarkitdownResponseDTO upload(@RequestPart("file") MultipartFile file,
			@RequestPart("request") AiMarkitdownDTO aiMarkitdownDTO);

}
