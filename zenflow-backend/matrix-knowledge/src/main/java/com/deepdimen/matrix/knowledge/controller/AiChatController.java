package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.AiGenerateDTO;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatEvaluateDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.dto.ChatSendDTO;
import com.deepdimen.matrix.knowledge.dto.openai.OpenAiChatCompletionsResponse;
import com.deepdimen.matrix.knowledge.dto.openai.OpenAiChoice;
import com.deepdimen.matrix.knowledge.dto.openai.OpenAiDelta;
import com.deepdimen.matrix.knowledge.dto.openai.OpenAiMessage;
import com.deepdimen.matrix.knowledge.dto.openai.OpenAiPathChatCompletionsRequest;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.service.AiChartGenerateService;
import com.deepdimen.matrix.knowledge.service.AiChatRecordService;
import com.deepdimen.matrix.knowledge.service.AiGenerateService;
import com.deepdimen.matrix.knowledge.service.ChatService;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionException;
import com.deepdimen.matrix.knowledge.support.speech.SpeechCorrelationId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.deepdimen.matrix.knowledge.support.constant.AiChatConstants.END_MSG;

/**
 * AI聊天控制器 提供AI聊天相关功能接口，包括会话创建、消息流处理、文本/音频生成、图表生成等功能
 *
 * @author caoyuan
 * @date 2024/3/15
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class AiChatController {

	/**
	 * 聊天服务，处理聊天相关的业务逻辑
	 */
	private final ChatService chatService;

	/**
	 * AI聊天记录服务
	 */
	private final AiChatRecordService chatRecordService;

	/**
	 * AI生成服务，处理文本、音频等内容生成
	 */
	private final AiGenerateService generateService;

	/**
	 * AI图表生成服务，处理图表类内容的生成
	 */
	private final AiChartGenerateService chartGenerateService;

	/**
	 * 创建公开的聊天连接 保存客户端的连接参数，返回连接标识用于后续的SSE消息推送
	 * @param chatMessageDTO 聊天消息内容，包含用户输入、会话ID等信息
	 * @return 连接标识符UUID
	 */
	@Inner(value = false)
	@PostMapping("/create")
	public R<String> createPublicConnection(@Valid @RequestBody ChatMessageDTO chatMessageDTO) {
		return chatService.saveConnectionParams(chatMessageDTO);
	}

	/**
	 * 获取SSE消息流 实际调用大模型并通过SSE方式返回消息流 需要先调用createPublicConnection接口获取消息键值
	 * @param key 消息键值，通过createPublicConnection接口获取
	 * @return AI响应消息流
	 */
	@Inner(value = false)
	@RequestMapping(method = { RequestMethod.POST, RequestMethod.GET }, value = "/msg/list",
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<AiMessageResultDTO> msg(@RequestParam Long key) {
		try {
			return chatService.chatList(key);
		}
		catch (Exception e) {
			log.error("chat error", e);
			return Flux.just(new AiMessageResultDTO(e.getMessage())).concatWithValues(new AiMessageResultDTO(END_MSG));
		}
	}

	/**
	 * 删除会话 清除指定会话ID的内存数据和相关记录
	 * @param id 会话ID
	 * @return 删除结果
	 */
	@Inner(value = false)
	@DeleteMapping("/conversation/{id}")
	public R deleteConversation(@PathVariable String id) {
		String username = SecurityUtils.getUser() != null ? SecurityUtils.getUser().getUsername() : null;
		if (username == null) {
			return R.failed("未登录，无法删除会话");
		}
		chatService.clearMemory(id);
		return R.ok(chatRecordService.removeConversation(id, username));
	}

	/**
	 * 生成文本内容 根据用户输入的提示词生成对应的文本内容
	 * @param generateDTO 生成请求参数，包含提示词、模型等信息
	 * @return 生成的文本内容
	 */
	@PostMapping("/generate/text")
	public R<String> generateText(@RequestBody AiGenerateDTO generateDTO) {
		return R.ok(generateService.generateText(generateDTO));
	}

	/**
	 * 音频转文本 将上传的音频文件转换为文本内容（语音识别）
	 * @param fileData 音频文件的字节数组
	 * @return 识别出的文本内容
	 */
	@PostMapping("/audio")
	public R<String> generateTextByAudio(@RequestBody byte[] fileData,
			@RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
		try {
			return R.ok(generateService.generateTextByAudio(fileData, SpeechCorrelationId.resolve(correlationId)));
		}
		catch (SpeechRecognitionException exception) {
			return R.failed(exception.getCode(), exception.getMessage());
		}
	}

	/**
	 * 文本转音频 将文本内容转换为语音音频文件（TTS文本转语音）
	 * @param generateDTO 生成请求参数，包含需要转换的文本内容
	 * @return 音频文件的访问路径
	 * @throws IOException IO异常
	 */
	@PostMapping("/generate/tts")
	public R<String> generateAudioByText(@RequestBody AiGenerateDTO generateDTO) throws IOException {
		return R.ok(generateService.generateAudioByText(generateDTO.getPrompt()));
	}

	/**
	 * 获取图表数据 根据图表ID获取已生成的图表数据和配置信息
	 * @param chartId 图表唯一标识符
	 * @return 图表的JSON数据
	 */
	@GetMapping("/generate/chart/{chartId}")
	public R<String> getChart(@PathVariable String chartId) {
		return R.ok(chartGenerateService.getChart(chartId));
	}

	/**
	 * 前台对话发送（合并消息发送与流式响应）
	 * @param chatSendDTO 对话发送请求体
	 * @return SSE流式响应
	 */
	@Inner(value = false)
	@PostMapping(value = "/msg/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<AiMessageResultDTO> chatSend(@Valid @RequestBody ChatSendDTO chatSendDTO) {
		try {
			return chatService.chatSend(chatSendDTO);
		}
		catch (Exception e) {
			log.error("chat send error", e);
			return Flux.just(new AiMessageResultDTO(e.getMessage())).concatWithValues(new AiMessageResultDTO(END_MSG));
		}
	}

	/**
	 * 消息评价（点赞/反对/取消）
	 * @param chatEvaluateDTO 评价请求体
	 * @return 操作结果
	 */
	@Inner(value = false)
	@PostMapping("/record/evaluate")
	public R evaluate(@Valid @RequestBody ChatEvaluateDTO chatEvaluateDTO) {
		return chatRecordService.evaluate(chatEvaluateDTO);
	}

	/**
	 * 前台历史消息查询（按conversationId + 当前用户过滤，防止越权）
	 * @param conversationId 会话ID
	 * @return 对话记录列表
	 */
	@Inner(value = false)
	@GetMapping("/record/list")
	public R<List<AiChatRecordEntity>> recordList(@RequestParam String conversationId) {
		String username = SecurityUtils.getUser() != null ? SecurityUtils.getUser().getUsername() : null;
		if (username == null) {
			return R.failed("未登录，无法查询历史消息");
		}
		return R.ok(chatRecordService.listByConversation(conversationId, username));
	}

	/**
	 * OpenAI兼容的Chat Completions接口（仅支持流式响应）
	 * @param datasetId 数据集ID（路径参数）
	 * @param request OpenAI格式的请求
	 * @return OpenAI格式的流式响应
	 */
	@Inner(value = false)
	@PostMapping(value = "/{datasetId}/chat/completions")
	public Flux<String> chatCompletions(@PathVariable("datasetId") Long datasetId,
			@Valid @RequestBody OpenAiPathChatCompletionsRequest request) {
		try {
			// 转换为内部格式并保存连接参数
			ChatMessageDTO chatMessageDTO = convertToInternalFormat(request, datasetId);
			R<String> connectionResult = chatService.saveConnectionParams(chatMessageDTO);

			if (!connectionResult.isOk()) {
				// 连接创建失败错误
				return Flux.just(
						"data: {\"error\": \"Failed to create connection: " + connectionResult.getMsg() + "\"}\n\n",
						"data: [DONE]\n\n");
			}

			Long messageKey = parseMessageKey(connectionResult.getData());

			// 创建流式响应
			String sessionId = "chatcmpl-" + UUID.randomUUID();
			long timestamp = Instant.now().getEpochSecond();

			return createOpenAiStreamResponse(messageKey, request, sessionId, timestamp);

		}
		catch (Exception e) {
			log.error("Chat completions error", e);
			// 流式响应错误
			return Flux.just("data: {\"error\": \"Internal server error: " + e.getMessage() + "\"}\n\n",
					"data: [DONE]\n\n");
		}
	}

	/**
	 * 解析消息key
	 */
	private Long parseMessageKey(Object data) {
		if (data instanceof String) {
			return Long.parseLong((String) data);
		}
		else if (data instanceof Long) {
			return (Long) data;
		}
		else {
			throw new IllegalArgumentException("Invalid connection key type");
		}
	}

	/**
	 * 创建OpenAI标准流式响应
	 */
	private Flux<String> createOpenAiStreamResponse(Long messageKey, OpenAiPathChatCompletionsRequest request,
			String sessionId, long timestamp) {
		// 获取原始流数据
		Flux<AiMessageResultDTO> originalStream = chatService.chatList(messageKey);

		// 创建初始chunk（包含role: assistant）
		Flux<String> initialChunk = Flux
			.just(createStreamChunk(sessionId, timestamp, request.getModel(), "", "assistant", null));

		// 处理内容chunks
		Flux<String> contentChunks = originalStream
			.filter(result -> result.getMessage() != null && !result.getMessage().equals("[DONE]"))
			.map(result -> createStreamChunk(sessionId, timestamp, request.getModel(), result.getMessage(), null,
					null));

		// 创建结束chunk（finish_reason: stop）
		Flux<String> finishChunk = Flux
			.just(createStreamChunk(sessionId, timestamp, request.getModel(), "", null, "stop"));

		// 创建DONE标记
		Flux<String> doneChunk = Flux.just("data: [DONE]\n\n");

		// 合并所有chunks（移除usage chunk）
		return initialChunk.concatWith(contentChunks)
			.concatWith(finishChunk)
			.concatWith(doneChunk)
			.onErrorResume(error -> {
				log.error("Stream processing error", error);
				return Flux.just("data: {\"error\": \"Stream processing error: " + error.getMessage() + "\"}\n\n",
						"data: [DONE]\n\n");
			});
	}

	/**
	 * 创建流式chunk
	 */
	private String createStreamChunk(String sessionId, long timestamp, String model, String content, String role,
			String finishReason) {
		OpenAiChatCompletionsResponse response = new OpenAiChatCompletionsResponse();
		response.setId(sessionId);
		response.setObject("chat.completion.chunk");
		response.setCreated(timestamp);
		response.setModel(model);
		response.setSystemFingerprint(null);
		response.setUsage(null);

		// 创建choice
		OpenAiChoice choice = new OpenAiChoice();
		choice.setIndex(0);
		choice.setLogprobs(null);
		choice.setFinishReason(finishReason);

		// 创建delta
		OpenAiDelta delta = new OpenAiDelta();
		if (role != null) {
			delta.setRole(role);
		}
		delta.setContent(content != null ? content : "");
		choice.setDelta(delta);

		response.setChoices(List.of(choice));

		try {
			String jsonResponse = JSONUtil.toJsonStr(response);
			return "data: " + jsonResponse + "\n\n";
		}
		catch (Exception e) {
			log.error("Error serializing stream chunk", e);
			return "data: {\"error\": \"Serialization error\"}\n\n";
		}
	}

	/**
	 * 转换为内部ChatMessageDTO格式
	 */
	private ChatMessageDTO convertToInternalFormat(OpenAiPathChatCompletionsRequest request, Long datasetId) {
		ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
		chatMessageDTO.setModelName(request.getModel());
		chatMessageDTO.setDatasetId(datasetId);
		chatMessageDTO.setWebsearch(Boolean.TRUE.equals(request.getWebSearch()));

		// 提取最后一个用户消息作为当前内容（支持多模态）
		String content = request.getMessages()
			.stream()
			.filter(msg -> "user".equals(msg.getRole()))
			.reduce((first, second) -> second)
			.map(OpenAiMessage::getContentAsString)
			.orElse("");

		chatMessageDTO.setContent(content);

		// 生成符合格式的会话ID: chat-{datasetId}-1-{uuid}
		String conversationId = request.getConversationId();
		if (conversationId == null || conversationId.isEmpty()) {
			conversationId = String.format("chat-%s-1-%s", datasetId, UUID.randomUUID().toString());
		}
		chatMessageDTO.setConversationId(conversationId);

		// 设置扩展详情
		ChatMessageDTO.ExtDetails extDetails = new ChatMessageDTO.ExtDetails();

		// 从request的extDetails中提取信息（如果有的话）
		if (request.getExtDetails() != null && request.getExtDetails() instanceof java.util.Map) {
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> extDetailsMap = (java.util.Map<String, Object>) request.getExtDetails();

			// 设置funcName
			Object funcName = extDetailsMap.get("funcName");
			if (funcName != null) {
				extDetails.setFuncName(String.valueOf(funcName));
			}
			else {
				extDetails.setFuncName("");
			}

			// 设置dataId
			Object dataId = extDetailsMap.get("dataId");
			if (dataId != null && !dataId.toString().isEmpty()) {
				try {
					extDetails.setDataId(Long.valueOf(dataId.toString()));
				}
				catch (NumberFormatException e) {
					extDetails.setDataId(null);
				}
			}
			else {
				extDetails.setDataId(null);
			}

			// 设置mcpId
			Object mcpId = extDetailsMap.get("mcpId");
			if (mcpId != null) {
				extDetails.setMcpId(String.valueOf(mcpId));
			}
			else {
				extDetails.setMcpId("");
			}
		}
		else {
			// 如果没有提供extDetails，设置默认值
			extDetails.setFuncName("");
			extDetails.setDataId(null);
			extDetails.setMcpId("");
		}

		chatMessageDTO.setExtDetails(extDetails);

		return chatMessageDTO;
	}

}
