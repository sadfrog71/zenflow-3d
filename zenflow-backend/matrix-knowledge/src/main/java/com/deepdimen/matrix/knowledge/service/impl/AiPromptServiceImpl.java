package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.dto.AiPromptDTO;
import com.deepdimen.matrix.knowledge.entity.AiPromptEntity;
import com.deepdimen.matrix.knowledge.mapper.AiPromptMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiPromptService;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI提示词服务实现类
 * <p>
 * 管理和优化AI提示词 支持使用不同模型进行提示词优化 提供系统提示词和用户提示词的组合优化
 *
 * @author matrix
 * @date 2024-03-20 16:29:05
 */
@Service
@RequiredArgsConstructor
public class AiPromptServiceImpl extends ServiceImpl<AiPromptMapper, AiPromptEntity> implements AiPromptService {

	/**
	 * 模型提供者 管理各种AI模型实例
	 */
	private final ModelProvider modelProvider;

	/**
	 * 优化提示词
	 * <p>
	 * 使用指定的AI模型优化用户提示词 支持自定义温度参数和系统提示词 返回优化后的更有效提示词
	 * @param promptDTO 包含提示词、模型名和温度参数的请求对象
	 * @return 优化后的提示词文本
	 */
	@Override
	public String optimizePrompt(AiPromptDTO promptDTO) {
		Pair<ChatModel, AiAssistantService> aiAssistant = modelProvider.getAiAssistant(promptDTO.getModelName());

		ChatRequest chatRequest = ChatRequest.builder()
			.temperature(promptDTO.getTemperature())
			.messages(SystemMessage.systemMessage(promptDTO.getSystemPrompt()), UserMessage.from(promptDTO.getPrompt()))
			.build();

		ChatResponse chatResponse = aiAssistant.getKey().chat(chatRequest);
		return chatResponse.aiMessage().text();
	}

}
