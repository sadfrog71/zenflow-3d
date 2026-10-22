package com.deepdimen.matrix.knowledge.support.handler.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.deepdimen.matrix.common.core.util.WebUtils;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.service.AiBillService;
import com.deepdimen.matrix.knowledge.service.AiChatRecordService;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 聊天模型监听器
 * <p>
 * 实现 LangChain4j 的 ChatModelListener 接口，用于监听和记录 AI 模型的请求和响应。 主要功能包括： 1.
 * 记录用户请求信息（IP、User-Agent、用户名等） 2. 计算和记录 Token 使用量 3. 保存聊天记录和账单信息 4. 处理错误情况
 * </p>
 *
 * @author caoyuan
 * @date 2024/9/26
 * @see <a href="https://docs.langchain4j.dev/tutorials/observability">LLM
 * Observability</a>
 */
@Service
@RequiredArgsConstructor
public class MatrixChatModelListener implements ChatModelListener {

	/**
	 * 聊天记录服务，使用 @Lazy 注解避免循环依赖
	 */
	@Lazy
	private final AiChatRecordService chatRecordService;

	/**
	 * Token 计数估算器，用于计算输入输出的 Token 数量
	 */
	private final TokenCountEstimator tokenCountEstimator;

	/**
	 * AI 账单服务，用于记录 Token 使用量和费用
	 */
	private final AiBillService aiBillService;

	/**
	 * 请求 LLM 之前的处理逻辑
	 * <p>
	 * 主要功能： 1. 获取并记录客户端信息（IP 地址、User-Agent） 2. 获取当前用户信息，如果未登录则使用 IP+UA 的 MD5 值作为用户标识 3.
	 * 将相关信息存储到请求上下文的 attributes 中 4. 处理 Function Calling 相关的上下文清理逻辑
	 * </p>
	 * @param requestContext 请求上下文，包含请求的所有信息
	 */
	@Override
	public void onRequest(ChatModelRequestContext requestContext) {
		HttpServletRequest request = WebUtils.getRequest();
		if (Objects.isNull(request)) {
			return;
		}

		String ip = JakartaServletUtil.getClientIP(request);
		String ua = request.getHeader(HttpHeaders.USER_AGENT);
		requestContext.attributes().put(AiChatRecordEntity.Fields.userAgent, ua);
		requestContext.attributes().put(AiChatRecordEntity.Fields.ip, ip);

		MatrixUser user = SecurityUtils.getUser();
		String username;
		if (Objects.nonNull(user)) {
			username = user.getUsername();
		}
		else {
			username = SecureUtil.md5((ip + ua));
		}

		if (Objects.isNull(ChatMessageContextHolder.get())
				|| Objects.isNull(ChatMessageContextHolder.get().getMessageKey())) {
			return;
		}

		requestContext.attributes()
			.put(AiChatRecordEntity.Fields.recordId, ChatMessageContextHolder.get().getMessageKey());

		requestContext.attributes().put(AiChatRecordEntity.Fields.username, username);
		requestContext.attributes().put(AiBillEntity.Fields.model, requestContext.chatRequest().modelName());
		// 记录请求开始时间戳，用于计算使用时长
		requestContext.attributes().put("requestStartTime", System.currentTimeMillis());

		// 如果不是 Function Calling 请求，清除上下文
		// Function Calling 是指 AI 可以调用外部工具/函数的功能
		if (CollUtil.isEmpty(requestContext.chatRequest().toolSpecifications())) {
			ChatMessageContextHolder.clear();
			return;
		}

		// 如果最后一条消息是工具执行结果，说明 Function Calling 已完成，清除上下文
		requestContext.chatRequest().messages().stream().reduce((first, second) -> second).ifPresent(message -> {
			if (message.type().equals(ChatMessageType.TOOL_EXECUTION_RESULT)) {
				ChatMessageContextHolder.clear();
			}
		});
	}

	/**
	 * 处理 LLM 响应的逻辑
	 * <p>
	 * 主要功能： 1. 从响应中获取 Token 使用情况 2. 如果模型未返回 Token 使用信息，则自行计算 3. 保存账单信息（记录 Token 消耗） 4.
	 * 保存聊天记录
	 * </p>
	 * @param responseContext 响应上下文，包含响应的所有信息
	 */
	@Override
	public void onResponse(ChatModelResponseContext responseContext) {

		Long messageKey = MapUtil.getLong(responseContext.attributes(), AiChatRecordEntity.Fields.recordId);
		if (Objects.isNull(messageKey)) {
			return;
		}

		TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();

		// 部分模型（如 Ollama）不返回 tokenUsage，需要自行计算
		if (Objects.isNull(tokenUsage)) {
			tokenUsage = getTokenUsage(responseContext);
		}

		// 计算使用时长（从请求开始到响应返回的时间）
		Long requestStartTime = MapUtil.getLong(responseContext.attributes(), "requestStartTime");
		if (Objects.nonNull(requestStartTime)) {
			long useTime = System.currentTimeMillis() - requestStartTime;
			responseContext.attributes().put(AiChatRecordEntity.Fields.useTime, useTime);
		}

		aiBillService.saveBill(responseContext.attributes(), tokenUsage);
		chatRecordService.saveRecord(responseContext.attributes(), responseContext.chatResponse().aiMessage().text(),
				true);
	}

	/**
	 * 计算 Token 使用情况
	 * <p>
	 * 针对 Ollama 等不返回 tokenUsage 的模型，手动计算输入和输出的 Token 数量。 使用 TokenCountEstimator 来估算文本的
	 * Token 数量。
	 * </p>
	 * @param responseContext 响应上下文
	 * @return Token 使用情况，包含输入和输出的 Token 数量
	 */
	@NotNull
	private TokenUsage getTokenUsage(ChatModelResponseContext responseContext) {
		TokenUsage tokenUsage;
		List<ChatMessage> messages = responseContext.chatRequest().messages();
		int inputTokenCount = messages.stream()
			.filter(message -> message.type().equals(ChatMessageType.AI))
			.map(message -> tokenCountEstimator.estimate(((AiMessage) messages).text()))
			.mapToInt(Integer::intValue) // 将 Integer 转换为 int
			.sum();

		int outputTokenCount = tokenCountEstimator.estimate(responseContext.chatResponse().aiMessage().text());
		tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);
		return tokenUsage;
	}

	/**
	 * 处理 LLM 调用出错的情况
	 * <p>
	 * 当 AI 模型调用失败时，记录错误信息到聊天记录中， 方便后续排查问题和统计错误率。
	 * </p>
	 * @param errorContext 错误上下文，包含错误信息和相关数据
	 */
	@Override
	public void onError(ChatModelErrorContext errorContext) {
		Throwable error = errorContext.error();

		if (Objects.isNull(ChatMessageContextHolder.get())
				|| Objects.isNull(ChatMessageContextHolder.get().getMessageKey())) {
			return;
		}

		Long messageKey = ChatMessageContextHolder.get().getMessageKey();
		if (Objects.isNull(messageKey)) {
			return;
		}

		chatRecordService.saveRecord(errorContext.attributes(), error.getMessage(), false);
	}

}
