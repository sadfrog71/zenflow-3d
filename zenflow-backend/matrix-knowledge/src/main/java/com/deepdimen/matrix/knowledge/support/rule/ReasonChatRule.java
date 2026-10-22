package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.date.DateUtil;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.websearch.WebSearchProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 深度推理聊天规则实现类
 * <p>
 * 该类实现了基于DeepSeek模型的深度推理聊天功能，支持： 1. 深度推理能力 - 使用DeepSeek的推理模型进行复杂问题分析 2. Web搜索集成 -
 * 可选的网络搜索功能增强回答准确性 3. 对话记忆管理 - 维护对话上下文历史 4. 系统提示词增强 - 使用专门的推理系统提示词模板
 *
 * @author caoyuan
 * @date 2025/06/19
 */
@Slf4j
@Component("reasonChat")
@RequiredArgsConstructor
public class ReasonChatRule implements ChatRule {

	private final WebSearchProvider webSearchProvider;

	private final ModelProvider modelProvider;

	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		// 构建用户输入内容
		String userContent;

		// 如果启用了 Web 搜索功能，则增强用户内容
		if (chatMessageDTO.isWebsearch()) {
			String searchResult = webSearchProvider.search(chatMessageDTO.getContent());
			userContent = PromptBuilder.render("web-search.st", Map.of("input", chatMessageDTO.getContent(),
					"currentDate", DateUtil.now(), "searchResult", searchResult));
		}
		else {
			userContent = chatMessageDTO.getContent();
		}

		// 使用推理系统提示和当前时间
		String systemPrompt = PromptBuilder.render("reason-system.st", Map.of("systemTime", DateUtil.now()));

		// 获取流式聊天模型
		StreamingChatModel streamingChatModel = modelProvider
			.getAiStreamAssistant(chatMessageDTO.getModelName(), ModelTypeEnums.REASON)
			.getKey();

		return Flux.create(emitter -> {
			// 构建聊天请求
			ChatRequest chatRequest = ChatRequest.builder()
				.messages(SystemMessage.from(systemPrompt), UserMessage.from(userContent))
				.build();

			// 使用自定义的 StreamingChatResponseHandler 支持推理内容
			streamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
				@Override
				public void onPartialResponse(String partialResponse) {
					AiMessageResultDTO result = new AiMessageResultDTO();
					result.setMessage(partialResponse);
					emitter.next(result);
				}

				@Override
				public void onPartialThinking(PartialThinking partialThinking) {
					AiMessageResultDTO result = new AiMessageResultDTO();
					result.setReasoningContent(partialThinking.text());
					emitter.next(result);
				}

				@Override
				public void onCompleteResponse(ChatResponse completeResponse) {
					// 发送对话完成的标志
					AiMessageResultDTO finalMessage = new AiMessageResultDTO();
					finalMessage.setFinish(true);
					emitter.next(finalMessage);
					emitter.complete();
				}

				@Override
				public void onError(Throwable error) {
					emitter.error(error);
				}
			});
		});
	}

}
