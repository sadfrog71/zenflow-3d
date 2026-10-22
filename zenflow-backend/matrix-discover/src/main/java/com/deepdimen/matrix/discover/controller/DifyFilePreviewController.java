package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Dify 文件预览公开接口（独立于 DifyProxyController，避免 catch-all 路由冲突）
 * <p>
 * 用于 img src 直接引用，浏览器无法携带自定义 Authorization/X-Agent-Id 头。
 * 安全性依赖 Dify 签名校验（timestamp + nonce + sign）。
 *
 * @author matrix
 * @date 2026/04/29
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "matrix.dify", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(description = "dify-file", name = "Dify 文件预览")
public class DifyFilePreviewController {

	private final DifyServiceClient difyServiceClient;

	private final AiAgentService aiAgentService;

	@GetMapping("/dify-file/preview")
	@Operation(summary = "Dify 文件预览（公开，无需认证）")
	public void filePreview(@RequestParam("agentId") Long agentId, @RequestParam("path") String filePath,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		AiAgentEntity agent = aiAgentService.getById(agentId);
		if (agent == null || !"dify".equals(agent.getAgentType())) {
			response.sendError(HttpStatus.NOT_FOUND.value());
			return;
		}
		String apiKey = agent.getDifyApiKey();
		if (apiKey == null || apiKey.isEmpty()) {
			response.sendError(HttpStatus.BAD_REQUEST.value());
			return;
		}
		Map<String, String> queryParams = extractQueryParams(request);
		queryParams.remove("agentId");
		queryParams.remove("path");
		// 从公共端点路径 /files/{id}/file-preview 提取 file_id，转换为 Service API 路径 /files/{id}/preview
		String difyPath = filePath.startsWith("/") ? filePath : "/" + filePath;
		difyPath = difyPath.replace("/file-preview", "/preview");
		log.debug("[Dify File] 文件预览 {} agentId={}", difyPath, agentId);
		try {
			ResponseEntity<byte[]> result = difyServiceClient.getRaw(difyPath, apiKey, queryParams).block();
			if (result == null || result.getBody() == null) {
				response.sendError(HttpStatus.NOT_FOUND.value());
				return;
			}
			MediaType contentType = result.getHeaders().getContentType();
			if (contentType != null) {
				response.setContentType(contentType.toString());
			}
			response.setHeader("Cache-Control", "private, max-age=3600");
			response.getOutputStream().write(result.getBody());
			response.getOutputStream().flush();
		}
		catch (WebClientResponseException e) {
			response.sendError(e.getStatusCode().value());
		}
		catch (Exception e) {
			log.warn("[Dify File] 文件预览失败: {}", e.getMessage());
			response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
		}
	}

	private Map<String, String> extractQueryParams(HttpServletRequest request) {
		Map<String, String> params = new HashMap<>();
		Enumeration<String> names = request.getParameterNames();
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			params.put(name, request.getParameter(name));
		}
		return params;
	}

}
