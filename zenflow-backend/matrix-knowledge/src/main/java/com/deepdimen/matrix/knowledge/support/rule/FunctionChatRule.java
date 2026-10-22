package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiFunctionAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 函数调用聊天规则实现
 * <p>
 * 实现基于函数调用的聊天功能，支持自动匹配目标函数并执行。 通过向量搜索自动识别用户意图，选择合适的函数进行调用。
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("functionChat")
@RequiredArgsConstructor
public class FunctionChatRule implements ChatRule {

	private final ModelProvider modelProvider;

	/**
	 * 处理函数调用聊天请求
	 * <p>
	 * 流程说明： 1. 如果用户未指定函数名称，通过向量搜索自动匹配 2. 使用AI函数助手服务处理用户请求 3. 返回流式响应结果
	 * @param chatMessageDTO 聊天上下文信息
	 * @return AI响应结果流
	 */
	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		AiFunctionAssistantService aiFunctionAssistant = modelProvider
			.getAiFunctionAssistant(chatMessageDTO.getModelName())
			.getValue();

		// 处理function，如果用户没有传递，则根据用户语义查询一个 function
		if (Objects.isNull(chatMessageDTO.getExtDetails())
				|| StrUtil.isBlank(chatMessageDTO.getExtDetails().getFuncName())) {
			Flux<AiMessageResultDTO> just = autoChoice(chatMessageDTO);
			if (just != null)
				return just;
		}

		return aiFunctionAssistant.chatFlux(chatMessageDTO.getContent()).map(AiMessageResultDTO::new);
	}

	/**
	 * 基于向量相似度自动匹配函数
	 * <p>
	 * 当用户没有明确指定函数时，通过向量搜索自动匹配语义最相似的函数。 使用嵌入向量搜索FUNCTION类型的函数。
	 * @param chatMessageDTO 聊天消息DTO，包含用户输入
	 * @return 如果找不到匹配的函数返回错误提示，找到则返回null并更新上下文
	 */
	@Nullable
	private static Flux<AiMessageResultDTO> autoChoice(ChatMessageDTO chatMessageDTO) {
		EmbeddingSearchResult<TextSegment> searchResult = EmbeddingProvider.search(chatMessageDTO.getContent(),
				metadataKey(EmbedBizTypeEnums.Fields.type).isEqualTo(EmbedBizTypeEnums.FUNCTION.getType()));

		if (Objects.isNull(searchResult) || searchResult.matches().isEmpty()) {
			return Flux.just(new AiMessageResultDTO("未找到相关功能，请点击下方+按钮选择目标功能"));
		}

		String functionName = searchResult.matches().get(0).embedded().metadata().getString(TEMP_ID);
		// 修改上下文中目标功能名称
		chatMessageDTO.getExtDetails().setFuncName(functionName);
		ChatMessageContextHolder.set(chatMessageDTO);
		return null;
	}

}
