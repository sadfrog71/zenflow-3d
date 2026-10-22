package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.UmiOcrImageModelDTO;
import com.deepdimen.matrix.knowledge.dto.UmiOcrPDFModelDTO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.PostExchange;

/**
 * AI OCR（光学字符识别）助手服务接口
 * <p>
 * 集成UmiOCR服务，提供图片和PDF文档的文字识别能力， 支持多语言识别、版面分析、表格提取等高级功能。
 *
 * @author caoyuan
 * @date 2024/10/1
 */
public interface AiOCRAssistantService {

	/**
	 * 图片文字识别
	 * <p>
	 * 对图片进行OCR识别，提取其中的文字内容， 支持多种图片格式和多语言文字识别。
	 * @param request 图片OCR请求参数，包含图片数据、识别语言、输出格式等配置
	 * @return 识别结果的JSON字符串，包含文字内容、位置信息、置信度等
	 */
	@PostExchange("/api/ocr")
	String image(@RequestBody UmiOcrImageModelDTO.UmiOcrImageModelRequest request);

	/**
	 * 上传PDF文档进行OCR处理
	 * <p>
	 * 上传PDF文档到OCR服务，创建异步处理任务， 适用于大文件或批量页面的识别场景。
	 * @param file PDF文档文件
	 * @return PDF上传响应，包含任务ID和处理状态
	 */
	@PostExchange("/api/doc/upload")
	UmiOcrPDFModelDTO.UmiOcrPDFModelResponse uploadDoc(@RequestPart MultipartFile file);

	/**
	 * 查询PDF文档OCR处理结果
	 * <p>
	 * 根据任务ID查询PDF文档的OCR处理进度和结果， 获取每页的识别内容和状态信息。
	 * @param request 查询请求，包含任务ID等参数
	 * @return 处理结果响应，包含识别状态、进度、文本内容等
	 */
	@PostExchange("/api/doc/result")
	UmiOcrPDFModelDTO.UmiOcrPDFModelResultResponse docResult(
			@RequestBody UmiOcrPDFModelDTO.UmiOcrPDFModelResultRequest request);

	/**
	 * 下载PDF文档OCR处理结果
	 * <p>
	 * 下载已完成OCR处理的结果文件，支持多种格式输出， 如纯文本、带坐标的JSON、可搜索的PDF等。
	 * @param request 下载请求，包含任务ID、输出格式等参数
	 * @return 下载响应，包含结果文件的URL或内容
	 */
	@PostExchange("/api/doc/download")
	UmiOcrPDFModelDTO.UmiOcrPDFModelDownResponse downloadDoc(
			@RequestBody UmiOcrPDFModelDTO.UmiOcrPDFModelDownRequest request);

}
