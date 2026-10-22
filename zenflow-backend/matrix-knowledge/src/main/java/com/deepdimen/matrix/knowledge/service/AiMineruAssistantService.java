package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.MineruBatchResponseDTO;
import com.deepdimen.matrix.knowledge.dto.MineruLocalResponseDTO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.net.URI;

/**
 * AI Mineru 文档解析助手服务接口
 * <p>
 * 集成Mineru服务，提供高精度的文档解析能力，专注于复杂文档 （如学术论文、技术文档）的结构化提取，支持批量处理。
 *
 * @author caoyuan
 * @date 2025/06/01
 */
public interface AiMineruAssistantService {

	/**
	 * 申请批量文件上传的预签名URL
	 * <p>
	 * 为批量文档处理申请上传凭证，获取每个文件的预签名上传地址， 支持大批量文档的并行上传和处理。
	 * @param request 批量上传请求参数，包含文件列表、处理选项等
	 * @param authorization 授权令牌，用于身份验证
	 * @return 批量上传响应，包含每个文件的预签名URL和批次ID
	 */
	@PostExchange(value = "/file-urls/batch", contentType = "application/json")
	MineruBatchResponseDTO applyBatchUploadUrls(@RequestBody String request,
			@RequestHeader("Authorization") String authorization);

	/**
	 * 上传文件到预签名URL
	 * <p>
	 * 使用获取的预签名URL上传文件内容，直接将文件数据推送到 对象存储服务，实现高效的文件传输。
	 * @param uri 预签名的上传URL地址
	 * @param file 文件内容的字节数组
	 * @param authorization 授权头信息（如果需要）
	 * @param contentType 文件的MIME类型
	 */
	@PutExchange
	void upload(URI uri, @RequestBody byte[] file, @RequestHeader("Authorization") String authorization,
			@RequestHeader("Content-Type") String contentType);

	/**
	 * 获取批量处理任务的结果
	 * <p>
	 * 查询批量文档解析任务的处理状态和结果，获取每个文档的 解析内容、元数据、处理状态等信息。
	 * @param batchId 批量任务的唯一标识符
	 * @param authorization 授权令牌（可选）
	 * @return 批量任务的处理结果，包含每个文档的解析状态和内容
	 */
	@GetExchange("/extract-results/batch/{batchId}")
	MineruBatchResponseDTO getBatchResults(@PathVariable("batchId") String batchId,
			@RequestHeader(value = "Authorization", required = false) String authorization);

	/**
	 * 获取批量处理任务的结果
	 * @param batchId 批量任务的唯一标识符
	 * @return 批量任务的处理结果，包含每个文档的解析状态和内容
	 */
	@GetExchange("/extract-results/batch/{batchId}")
	MineruLocalResponseDTO getLocalBatchResults(@PathVariable("batchId") String batchId);

}
