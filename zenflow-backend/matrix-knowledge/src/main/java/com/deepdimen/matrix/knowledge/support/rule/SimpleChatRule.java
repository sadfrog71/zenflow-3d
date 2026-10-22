package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.websearch.WebSearchProvider;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import feign.Response;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;
import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 简单聊天规则实现类
 * <p>
 * 该类实现了基础的聊天功能，支持： 1. 纯文本对话 - 基于上下文的普通聊天 2. 网络搜索增强 - 支持通过网络搜索增强回答的准确性和时效性 3. 图片理解 -
 * 支持用户上传图片并进行视觉问答 4. 文件对话 - 支持上传文档文件并基于文件内容进行问答 5. 流式响应 - 所有响应都以流的形式返回，提供更好的用户体验
 * <p>
 * 该规则使用不同的AI模型处理不同类型的输入： - 文本对话使用标准聊天模型 - 网络搜索对话使用web-search模板增强用户输入 - 图片理解使用视觉模型 -
 * 文件对话使用向量检索增强生成
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("simpleChat")
@RequiredArgsConstructor
public class SimpleChatRule implements ChatRule {

	private final RemoteFileService remoteFileService;

	private final ModelProvider modelProvider;

	private final WebSearchProvider webSearchProvider;

	/**
	 * 处理聊天消息主入口
	 * <p>
	 * 根据消息中的附件类型和搜索设置自动路由到不同的处理方法： - 如果包含图片，调用图片理解功能 - 如果包含文件，调用文档问答功能 -
	 * 如果启用了网络搜索，使用web-search模板增强用户输入 - 否则执行普通文本聊天
	 * @param chatMessageDTO 聊天消息DTO，包含文本内容、可能的附件信息和搜索设置
	 * @return 包含AI响应的Flux流
	 * @throws Exception 处理过程中可能抛出的异常
	 */
	@SneakyThrows
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		// 如果图片不为空
		if (Objects.nonNull(chatMessageDTO.getExtDetails())
				&& CollUtil.isNotEmpty(chatMessageDTO.getExtDetails().getImages())) {
			return chat2Image(chatMessageDTO);
		}

		// 如果office不为空
		if (Objects.nonNull(chatMessageDTO.getExtDetails())
				&& CollUtil.isNotEmpty(chatMessageDTO.getExtDetails().getFiles())) {
			return chat2File(chatMessageDTO);
		}

		// 构建用户输入内容
		String userContent;
		String systemPrompt;

		// 如果启用了 Web 搜索功能，则增强用户内容
		if (chatMessageDTO.isWebsearch()) {
			String searchResult = webSearchProvider.search(chatMessageDTO.getContent());
			userContent = PromptBuilder.render("web-search.st", Map.of("input", chatMessageDTO.getContent(),
					"currentDate", DateUtil.now(), "searchResult", searchResult));
			// 使用网络搜索系统提示模板
			systemPrompt = PromptBuilder.render("base-simple-system.st", Map.of(systemTime, DateUtil.now()));
		}
		else {
			userContent = chatMessageDTO.getContent();
			systemPrompt = PromptBuilder.render("base-simple-system.st");
		}

		AiStreamAssistantService streamAssistantService = modelProvider
			.getAiStreamAssistant(chatMessageDTO.getModelName())
			.getValue();

		Flux<String> chatResponseFlux = streamAssistantService.chat(chatMessageDTO.getConversationId(), systemPrompt,
				userContent);

		// 转成 AiMessageResultDTO
		return chatResponseFlux.map(AiMessageResultDTO::new);

	}

	/**
	 * 处理文件对话
	 * <p>
	 * 功能说明： 1. 从内存向量库中检索与用户问题相关的文档片段 2. 使用文件的MD5值作为临时ID进行精确匹配 3. 将检索到的内容作为上下文，结合用户问题生成回答
	 * 4. 如果未找到相关内容，返回预设的提示信息
	 * @param chatMessageDTO 包含文件信息的聊天消息DTO
	 * @return 基于文档内容的AI响应流
	 */
	private Flux<AiMessageResultDTO> chat2File(ChatMessageDTO chatMessageDTO) {
		List<ChatMessageDTO.FileDTO> files = chatMessageDTO.getExtDetails().getFiles();

		EmbeddingSearchResult<TextSegment> searchResult = EmbeddingProvider.search(chatMessageDTO.getContent(),
				metadataKey(TEMP_ID).isEqualTo(SecureUtil.md5(files.get(0).getName())),
				metadataKey(EmbedBizTypeEnums.Fields.type).isEqualTo(EmbedBizTypeEnums.CHAT2FILE.getType()));

		if (searchResult.matches().isEmpty()) {
			return Flux.just(new AiMessageResultDTO("未找到相关的答案"));
		}

		// 对结果进行总结
		AiStreamAssistantService streamAssistantService = modelProvider
			.getAiStreamAssistant(chatMessageDTO.getModelName())
			.getValue();

		List<String> stringList = searchResult.matches()
			.stream()
			.map(segmentEmbeddingMatch -> segmentEmbeddingMatch.embedded().text())
			.toList();
		return streamAssistantService
			.chat(chatMessageDTO.getConversationId(), PromptBuilder.render("knowledge-system.st"),
					PromptBuilder.render("knowledge-user.st",
							Map.of("contents", CollUtil.join(stringList, StrUtil.CRLF), "userMessage",
									chatMessageDTO.getContent(), "emptyDesc", "未找到相关的答案", systemTime, DateUtil.now())))
			.map(AiMessageResultDTO::new);
	}

	/**
	 * 处理图片对话
	 * <p>
	 * 功能说明： 1. 支持多张图片的同时理解 2. 从远程文件服务下载图片数据 3. 将图片转换为Base64编码格式 4. 使用视觉模型理解图片内容并回答用户问题 5.
	 * 流式返回视觉模型的响应
	 * @param chatMessageDTO 包含图片信息的聊天消息DTO
	 * @return 基于图片理解的AI响应流
	 * @throws IOException 图片处理过程中的IO异常
	 */
	@NotNull
	private Flux<AiMessageResultDTO> chat2Image(ChatMessageDTO chatMessageDTO) throws IOException {
		StreamingChatModel visionModel = modelProvider.getAiVisionStreamAssistant().getKey();

		List<Content> contentList = new ArrayList<>();
		contentList.add(TextContent.from(chatMessageDTO.getContent()));
		for (ChatMessageDTO.FileDTO image : chatMessageDTO.getExtDetails().getImages()) {
			@Cleanup
			Response response = remoteFileService.getFile(image.getName());
			@Cleanup
			InputStream inputStream = response.body().asInputStream();
			String encodeToString = Base64.getEncoder().encodeToString(IoUtil.readBytes(inputStream));
			contentList.add(ImageContent.from(encodeToString, MediaType.IMAGE_PNG_VALUE));
		}

		return Flux.create(sink -> {

			visionModel.chat(ChatRequest.builder().messages(UserMessage.from(contentList)).build(),
					new StreamingChatResponseHandler() {
						@Override
						public void onPartialResponse(String partialResponse) {
							sink.next(new AiMessageResultDTO(partialResponse));
						}

						@Override
						public void onCompleteResponse(ChatResponse completeResponse) {
							sink.complete();
						}

						@Override
						public void onError(Throwable error) {
							sink.error(error);
						}
					});
		});
	}

}
