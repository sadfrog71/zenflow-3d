package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.common.security.component.ApiTokenValidator;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.dto.dify.DifyRetrievalRequestDTO;
import com.deepdimen.matrix.knowledge.dto.dify.DifyRetrievalResponseDTO;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dify 外部知识库适配控制器
 * <p>
 * 接收 Dify 标准外部知识库检索请求，通过 API Token 认证后复用 AiSliceService.recall() 完成 RAG 检索，将结果转换为 Dify
 * 期望的响应格式返回。
 *
 * @author matrix
 * @date 2026/04/06
 */
@Slf4j
@RestController
@ConditionalOnBean(ApiTokenValidator.class)
@RequiredArgsConstructor
@RequestMapping("/external-knowledge")
@Tag(description = "external-knowledge", name = "Dify外部知识库适配")
public class DifyExternalKnowledgeController {

	private final AiSliceService aiSliceService;

	private final ApiTokenValidator apiTokenValidator;

	@Inner(value = false)
	@Operation(summary = "Dify外部知识库检索", description = "接收Dify标准检索请求，返回知识库召回结果")
	@PostMapping("/retrieval")
	public ResponseEntity<?> retrieval(@RequestBody(required = false) DifyRetrievalRequestDTO request,
			HttpServletRequest httpRequest) {
		// 1. 认证: 从 Authorization: Bearer 头提取 token
		String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
		if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error_code", 1001, "error_msg", "Authorization header missing or invalid"));
		}
		String tokenValue = authHeader.substring(7).trim();

		ApiTokenValidator.ApiTokenInfo tokenInfo = apiTokenValidator.findValidToken(tokenValue);
		if (tokenInfo == null) {
			log.warn("Dify retrieval: API Token 验证失败, token前缀: {}", StrUtil.subPre(tokenValue, 8));
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error_code", 1002, "error_msg", "API Token invalid or expired"));
		}

		// 2. 设置租户上下文
		apiTokenValidator.setTenantContext(tokenInfo.getTenantId());
		try {
			// 2.5 空请求体兼容(Dify验证endpoint连通性时发送空body)
			if (request == null || (StrUtil.isBlank(request.getQuery()) && StrUtil.isBlank(request.getKnowledgeId()))) {
				return ResponseEntity.ok(DifyRetrievalResponseDTO.builder().records(List.of()).build());
			}

			// 3. 参数校验
			if (StrUtil.isBlank(request.getKnowledgeId())) {
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2001, "error_msg", "knowledge_id is required"));
			}
			if (StrUtil.isBlank(request.getQuery())) {
				return ResponseEntity.badRequest().body(Map.of("error_code", 2002, "error_msg", "query is required"));
			}
			Long datasetId;
			try {
				datasetId = Long.parseLong(request.getKnowledgeId());
			}
			catch (NumberFormatException e) {
				log.warn("Dify retrieval: knowledge_id 格式错误: {}", request.getKnowledgeId());
				return ResponseEntity.badRequest()
					.body(Map.of("error_code", 2001, "error_msg", "Invalid knowledge_id format, must be numeric"));
			}

			// 4. 参数转换 (fullTextSearch/reRanking 使用默认值, Dify retrieval_setting 不包含这些参数)
			AiSliceReCallRequestDTO recallRequest = new AiSliceReCallRequestDTO();
			recallRequest.setDatasetId(datasetId);
			recallRequest.setContent(request.getQuery());
			if (request.getRetrievalSetting() != null && request.getRetrievalSetting().getTopK() != null) {
				recallRequest.setTopK(request.getRetrievalSetting().getTopK());
			}

			// 5. 调用已有召回逻辑
			List<AiSliceReCallDTO> recallResults = aiSliceService.recall(recallRequest);

			// 6. 结果转换 + score_threshold 过滤
			Double scoreThreshold = (request.getRetrievalSetting() != null)
					? request.getRetrievalSetting().getScoreThreshold() : null;

			List<DifyRetrievalResponseDTO.Record> records = recallResults.stream().map(dto -> {
				double score = parseScore(dto.getScore());
				if (scoreThreshold != null && score < scoreThreshold) {
					return null;
				}
				return DifyRetrievalResponseDTO.Record.builder()
					.content(dto.getContent())
					.title("slice-" + dto.getSliceId())
					.score(score)
					.metadata(parseMetadata(dto.getMetadata()))
					.build();
			}).filter(Objects::nonNull).collect(Collectors.toList());

			// 7. 更新 token 最后使用时间
			try {
				apiTokenValidator.updateLastUsedTime(tokenInfo.getId());
			}
			catch (Exception e) {
				log.warn("更新 API Token 最后使用时间失败, id: {}", tokenInfo.getId(), e);
			}

			return ResponseEntity.ok(DifyRetrievalResponseDTO.builder().records(records).build());
		}
		catch (Exception e) {
			log.error("Dify retrieval: 检索异常", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error_code", 5000, "error_msg", "Internal retrieval error"));
		}
		finally {
			apiTokenValidator.clearTenantContext();
		}
	}

	/**
	 * 解析 score 字符串为 0-1 范围的 double 值
	 * <p>
	 * 兼容两种格式: "95.12%"(百分比) 和 "0.9512"(小数)
	 */
	private double parseScore(String scoreStr) {
		if (StrUtil.isBlank(scoreStr)) {
			return 0.0;
		}
		try {
			if (scoreStr.contains("%")) {
				return Double.parseDouble(scoreStr.replace("%", "").trim()) / 100.0;
			}
			return Double.parseDouble(scoreStr.trim());
		}
		catch (Exception e) {
			return 0.0;
		}
	}

	/**
	 * 解析 metadata 字符串为 Map
	 * <p>
	 * 优先尝试 JSON 解析；若失败（如 LangChain4j Metadata.toString() 输出的 "Metadata{key=value}"
	 * 格式），则将原始字符串放入 raw 字段保留信息
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> parseMetadata(String metadataStr) {
		if (StrUtil.isBlank(metadataStr)) {
			return Map.of();
		}
		try {
			if (metadataStr.trim().startsWith("{") && metadataStr.trim().endsWith("}")) {
				return JSONUtil.toBean(metadataStr, Map.class);
			}
		}
		catch (Exception ignored) {
		}
		return Map.of("raw", metadataStr);
	}

}
