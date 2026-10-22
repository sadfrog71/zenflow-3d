package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiMcpConfigEntity;
import com.deepdimen.matrix.knowledge.mapper.AiMcpConfigMapper;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.McpClientProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;
import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * MCP聊天规则实现
 * <p>
 * 实现基于MCP（Model Context Protocol）的聊天功能。 支持动态加载MCP客户端，通过MCP协议调用外部工具和服务。
 *
 * @author caoyuan
 * @date 2025/03/22
 */
@Slf4j
@Component("mcpChat")
@RequiredArgsConstructor
public class McpChatRule implements ChatRule {

	private final ModelProvider modelProvider;

	private final AiMcpConfigMapper mcpConfigMapper;

	private final McpClientProvider mcpClientProvider;

	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 基于向量相似度自动匹配MCP功能
	 * <p>
	 * 当用户没有明确指定MCP ID时，通过向量搜索自动匹配语义最相似的MCP配置。 使用嵌入向量搜索CHAT2MCP类型的配置。
	 * @param chatMessageDTO 聊天消息DTO，包含用户输入
	 * @return 如果找不到匹配的MCP返回错误提示，找到则返回null并更新上下文
	 */
	@Nullable
	private List<Long> autoChoice(ChatMessageDTO chatMessageDTO) {

		EmbeddingSearchResult<TextSegment> searchResult = EmbeddingProvider.search(chatMessageDTO.getContent(), 3,
				aiKnowledgeProperties.getInMemorySearch().getMinScore(),
				metadataKey(EmbedBizTypeEnums.Fields.type).isEqualTo(EmbedBizTypeEnums.CHAT2MCP.getType()));

		if (Objects.isNull(searchResult) || searchResult.matches().isEmpty()) {
			return null;
		}

		return searchResult.matches()
			.stream()
			.map(result -> result.embedded().metadata().getLong(TEMP_ID))
			.collect(Collectors.toList());
	}

	/**
	 * 处理MCP聊天请求
	 * <p>
	 * 主要流程： 1. 如果用户未指定MCP，通过向量搜索自动选择 2. 获取MCP配置并创建或获取MCP客户端 3. 使用MCP工具提供者构建助手服务 4.
	 * 使用模板处理用户输入并返回流式响应
	 * @param chatMessageDTO 聊天上下文信息
	 * @return AI响应结果流
	 */
	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		List<Long> mcpIdList = new ArrayList<>();
		// 如果用户未提供MCP，则自动选择
		if (Objects.isNull(chatMessageDTO.getExtDetails())
				|| StrUtil.isBlank(chatMessageDTO.getExtDetails().getMcpId())) {
			mcpIdList = autoChoice(chatMessageDTO);
			if (CollUtil.isEmpty(mcpIdList)) {
				return Flux.just(new AiMessageResultDTO("未找到相关MCP配置，请点击下方+按钮选择目标MCP"));
			}
		}
		else {
			// 如果用户提供了MCP ID，则直接使用
			mcpIdList.add(Long.parseLong(chatMessageDTO.getExtDetails().getMcpId()));
		}

		// 获取MCP配置
		List<AiMcpConfigEntity> mcpConfigEntityList = mcpConfigMapper
			.selectByIds(mcpIdList.stream().distinct().toList());

		// 通过McpClientProvider获取或创建MCP客户端
		List<McpClient> mcpClientList = mcpConfigEntityList.stream()
			.map(mcpClientProvider::getOrCreateMcpClient)
			.filter(Objects::nonNull)
			.toList();

		// 使用MCP客户端设置工具提供者
		ToolProvider toolProvider = McpToolProvider.builder().mcpClients(mcpClientList).build();

		// 根据请求的模型名称获取AI模型和助手服务
		Pair<StreamingChatModel, AiStreamAssistantService> servicePair = modelProvider
			.getAiStreamAssistant(chatMessageDTO.getModelName());

		// 使用工具提供者构建助手服务
		AiNoMemoryStreamAssistantService assistant = AiServices.builder(AiNoMemoryStreamAssistantService.class)
			.streamingChatModel(servicePair.getKey())
			.toolProvider(toolProvider)
			.build();

		// 使用模板处理聊天并返回响应流
		Map<String, Object> promptData = Map.of("mcpName",
				mcpClientList.stream().map(McpClient::key).collect(Collectors.joining(StrUtil.COMMA)), "inputMessage",
				chatMessageDTO.getContent(), "accessToken", SecurityUtils.getToken(), systemTime, DateUtil.now());

		TokenStream tokenStream = assistant.chatTokenStream(PromptBuilder.render("mcp.st", promptData));

		return Flux.create(fluxSink -> {
			tokenStream.beforeToolExecution(beforeToolExecution -> {
				AiMessageResultDTO.ToolInfo toolInfo = new AiMessageResultDTO.ToolInfo();
				toolInfo.setName(beforeToolExecution.request().name());
				toolInfo.setParams(beforeToolExecution.request().arguments());
				AiMessageResultDTO aiMessageResultDTO = new AiMessageResultDTO();
				aiMessageResultDTO.setToolInfo(toolInfo);
				fluxSink.next(aiMessageResultDTO);
			})
				.onPartialResponse(message -> fluxSink.next(new AiMessageResultDTO(message)))
				.onCompleteResponse(chatResponse -> fluxSink.complete())
				.onError(fluxSink::error)
				.start();
		});
	}

}
