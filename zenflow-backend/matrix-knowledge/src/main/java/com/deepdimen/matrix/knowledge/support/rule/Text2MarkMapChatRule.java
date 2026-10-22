package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 文本转脑图聊天规则
 *
 * 该类实现了将文本内容转换为脑图（思维导图）格式的功能： 1. 接收用户输入的文本内容 2. 使用专门的提示词模板指导AI生成结构化的脑图数据 3.
 * 返回符合MarkMap格式的脑图内容 4. 支持流式输出，实时显示生成过程
 *
 * 使用场景： - 知识结构化展示 - 概念关系可视化 - 信息整理和归纳
 *
 * @author caoyuan
 * @date 2024/08/05
 */
@Slf4j
@Component("text2MarkMapChat")
@RequiredArgsConstructor
public class Text2MarkMapChatRule implements ChatRule {

	private final ModelProvider modelProvider;

	@Value("classpath:/prompts/gen-markmap.st")
	private Resource imageResource;

	/**
	 * 处理文本转脑图的请求
	 *
	 * 执行流程： 1. 加载脑图生成的提示词模板 2. 将用户输入注入到模板中 3. 调用AI模型生成结构化的脑图内容 4. 流式返回生成的脑图数据
	 * @param chatMessageDTO 聊天消息DTO，包含需要转换为脑图的文本内容
	 * @return 包含脑图格式数据的响应流
	 */
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		PromptTemplate promptTemplate = new PromptTemplate(imageResource);
		promptTemplate.add("input", chatMessageDTO.getContent());

		AiStreamAssistantService streamAssistantService = modelProvider
			.getAiStreamAssistant(chatMessageDTO.getModelName())
			.getValue();
		Flux<String> streamChat = streamAssistantService.chat(chatMessageDTO.getConversationId(),
				promptTemplate.render());
		// 转成 AiMessageResultDTO
		return streamChat.map(AiMessageResultDTO::new);
	}

}
