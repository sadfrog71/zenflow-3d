package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.JinaReadAndFetchContentResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

import java.util.Map;

/**
 * AI Jina Reader 助手服务接口
 * <p>
 * 集成Jina Reader服务，提供网页内容的智能提取和解析功能。 支持将各种网页转换为结构化的、便于AI处理的内容格式。
 *
 * @author caoyuan
 * @date 2025/04/30
 */
public interface AiJinaReaderAssistantService {

	/**
	 * 读取和解析网页内容
	 * <p>
	 * 使用Jina Reader服务抓取指定URL的网页内容，并将其转换为 结构化的文本格式，去除广告、导航栏等无关内容，保留核心信息。
	 * @param url 目标网页的URL地址，支持HTTP和HTTPS协议
	 * @param headers 请求头信息，必须包含Authorization认证信息
	 * @return JinaReadAndFetchContentResponse 解析后的网页内容响应对象，包含标题、正文、元数据等信息
	 */
	@GetExchange(value = "/{url}", accept = "application/json")
	JinaReadAndFetchContentResponse readAndFetchContent(@PathVariable("url") String url,
			@RequestHeader Map<String, String> headers);

}
