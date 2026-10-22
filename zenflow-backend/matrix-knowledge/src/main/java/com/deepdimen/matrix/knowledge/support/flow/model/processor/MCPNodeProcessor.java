package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiMCPNode;
import com.deepdimen.matrix.knowledge.support.rule.McpChatRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP节点处理器 负责处理流程中的MCP（Model Context Protocol）节点，直接调用McpChatRule避免代码重复
 *
 * @author caoyuan
 * @date 2025/03/22
 */
@Slf4j
@Component(NodeTypeConstants.MCP)
@RequiredArgsConstructor
public class MCPNodeProcessor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于变量替换
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	private final McpChatRule mcpChatRule;

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 验证节点配置
			AiMCPNode config = validateNodeConfig(node);

			// 获取输入参数并处理消息模板
			Dict variables = getInputVariables(node, context);
			String inputMessage = engine.getTemplate(config.getPrompt()).render(variables);

			// 构建ChatMessageDTO
			ChatMessageDTO chatMessageDTO = buildChatMessageDTO(config, inputMessage, context);

			// 调用McpChatRule处理MCP调用
			return processMcpCallViaRule(chatMessageDTO, context, node);

		}
		catch (Exception e) {
			log.error("MCP节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[MCP节点] -> " + e.getMessage());
		}
	}

	/**
	 * 验证MCP节点配置的有效性
	 * @param node 节点定义
	 * @return 有效的MCP节点配置
	 */
	private AiMCPNode validateNodeConfig(AiNodeDefinition node) {
		AiMCPNode config = node.getMcpParams();
		if (config == null) {
			throw FlowException.invalidParam("MCP节点配置无效");
		}

		if (StrUtil.isBlank(config.getPrompt())) {
			throw FlowException.invalidParam("MCP节点输入消息不能为空");
		}

		return config;
	}

	/**
	 * 构建ChatMessageDTO对象
	 * @param config MCP节点配置
	 * @param inputMessage 处理后的输入消息
	 * @param context 流程上下文
	 * @return ChatMessageDTO对象
	 */
	private ChatMessageDTO buildChatMessageDTO(AiMCPNode config, String inputMessage, FlowContextHolder context) {
		ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
		chatMessageDTO.setContent(inputMessage);

		// 设置扩展详情（MCP配置）
		if (StrUtil.isNotBlank(config.getMcpId())) {
			ChatMessageDTO.ExtDetails extDetails = new ChatMessageDTO.ExtDetails();
			extDetails.setMcpId(config.getMcpId());
			chatMessageDTO.setExtDetails(extDetails);
		}

		return chatMessageDTO;
	}

	/**
	 * 通过McpChatRule处理MCP调用
	 * @param chatMessageDTO 聊天消息DTO
	 * @param context 流程上下文
	 * @param node 节点定义
	 * @return 处理结果
	 */
	private Dict processMcpCallViaRule(ChatMessageDTO chatMessageDTO, FlowContextHolder context,
			AiNodeDefinition node) {
		// 使用AtomicReference来收集流式响应结果
		AtomicReference<String> resultContent = new AtomicReference<>("");
		AtomicInteger totalTokens = new AtomicInteger(0);

		try {
			// 调用McpChatRule处理
			Flux<AiMessageResultDTO> resultFlux = mcpChatRule.process(chatMessageDTO);

			// 收集流式响应
			resultFlux.doOnNext(result -> {
				String content = result.getMessage();
				if (StrUtil.isNotBlank(content)) {
					resultContent.updateAndGet(current -> current + content);
				}

				// 使用统一的流式内容发送方法
				context.sendStreamContent(content);
			}).doOnComplete(() -> {
				// 流式处理完成 - 使用统一的流式结束方法，自动处理节点状态
				context.sendStreamEnd(node, totalTokens.get() > 0 ? totalTokens.get() : null);
			}).doOnError(error -> {
				// 处理错误情况 - 使用统一的流式错误方法
				log.error("MCP调用过程中发生错误", error);
				context.sendStreamError("MCP调用失败：" + error.getMessage());
			}).onErrorComplete().blockLast();
		}
		catch (Exception e) {
			log.error("MCP调用执行失败", e);
			throw FlowException.nodeError(context.getFlowId().toString(), "[MCP节点] -> " + e.getMessage());
		}

		// 格式化并返回结果
		return Dict.create()
			.set(FlowConstant.CONTENT, resultContent.get())
			.set(FlowConstant.ROLE, "assistant")
			.set(FlowConstant.TOKENS, totalTokens.get())
			.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
	}

}