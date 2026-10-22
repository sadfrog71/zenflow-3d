package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiRagNode;
import com.deepdimen.matrix.knowledge.support.rule.VectorChatRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 知识库节点执行器
 */
@Slf4j
@Component(NodeTypeConstants.RAG)
@RequiredArgsConstructor
public class RagNodeExecutor extends AbstractNodeProcessor {

	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	private final VectorChatRule vectorChat;

	private final AiSliceService aiSliceService;

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 验证RAG节点配置
			AiRagNode ragParams = validateRagConfig(node);

			// 获取输入参数并处理模板
			Dict variables = getInputVariables(node, context);
			String prompt = processPromptTemplate(ragParams.getPrompt(), variables);

			// 构建聊天消息DTO
			ChatMessageDTO chatMessageDTO = buildChatMessageDTO(ragParams, prompt, context);

			// 根据配置决定处理方式
			if (YesNoEnum.YES.getCode().equals(ragParams.getOnlyRecall())) {
				return processRecallOnly(context, ragParams, prompt);
			}
			else {
				return processStreamingRag(chatMessageDTO, context, node);
			}
		}
		catch (Exception e) {
			log.error("RAG节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[RAG节点] -> " + e.getMessage());
		}
	}

	/**
	 * 验证RAG节点配置的有效性
	 * @param node 节点定义
	 * @return 有效的RAG节点配置
	 */
	private AiRagNode validateRagConfig(AiNodeDefinition node) {
		AiRagNode ragParams = node.getRagParams();
		if (ragParams == null || ragParams.getPrompt() == null) {
			throw FlowException.invalidParam("知识库节点配置无效");
		}
		return ragParams;
	}

	/**
	 * 处理提示模板，替换变量
	 * @param promptTemplate 提示模板
	 * @param variables 变量字典
	 * @return 处理后的提示内容
	 */
	private String processPromptTemplate(String promptTemplate, Dict variables) {
		Template template = engine.getTemplate(promptTemplate);
		return template.render(variables);
	}

	/**
	 * 构建聊天消息DTO
	 * @param ragParams RAG节点参数
	 * @param prompt 处理后的提示内容
	 * @param context 流程上下文
	 * @return 聊天消息DTO
	 */
	private ChatMessageDTO buildChatMessageDTO(AiRagNode ragParams, String prompt, FlowContextHolder context) {
		ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
		chatMessageDTO.setDatasetId(ragParams.getDatasetId());
		chatMessageDTO.setContent(prompt);
		chatMessageDTO.setConversationId(context.getConversationId());
		return chatMessageDTO;
	}

	/**
	 * 处理仅召回模式
	 * @param context
	 * @param ragParams RAG节点参数
	 * @param prompt 处理后的提示内容
	 * @return 处理结果字典
	 */
	private Dict processRecallOnly(FlowContextHolder context, AiRagNode ragParams, String prompt) {
		AiSliceReCallRequestDTO requestDTO = new AiSliceReCallRequestDTO();
		requestDTO.setDatasetId(ragParams.getDatasetId());
		requestDTO.setContent(prompt);
		List<AiSliceReCallDTO> recall = aiSliceService.recall(requestDTO);

		return Dict.create()
			.set(FlowConstant.RESULT, context.isStream()
					? recall.stream().map(AiSliceReCallDTO::getContent).collect(Collectors.joining(StrUtil.CRLF))
					: recall)
			.set(FlowConstant.RAG_SEARCHED,
					CollUtil.isNotEmpty(recall) ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode())
			.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
	}

	/**
	 * 处理流式RAG请求
	 * @param chatMessageDTO 聊天消息DTO
	 * @param context 流程上下文
	 * @param node 节点定义
	 * @return 处理结果字典
	 */
	private Dict processStreamingRag(ChatMessageDTO chatMessageDTO, FlowContextHolder context, AiNodeDefinition node) {
		// 用于累积响应和状态管理
		AtomicReference<String> accumulatedResponse = new AtomicReference<>(StrUtil.EMPTY);
		AtomicBoolean hasExtLinks = new AtomicBoolean(false);
		AtomicReference<Integer> totalTokens = new AtomicReference<>(0);

		// 处理流式响应
		vectorChat.process(chatMessageDTO).doOnNext(result -> {
			// 累积响应消息
			if (StrUtil.isNotBlank(result.getMessage())) {
				accumulatedResponse.updateAndGet(current -> current + result.getMessage());
			}

			// 检查是否有扩展链接
			if (result.getExtLinks() != null && !result.getExtLinks().isEmpty()) {
				hasExtLinks.set(true);
			}

			// 实时流式回调 - 使用统一的流式内容发送方法
			context.sendStreamContent(result.getMessage());
		}).doOnComplete(() -> {
			// 流式处理完成 - 使用统一的流式结束方法，自动处理节点状态
			context.sendStreamEnd(node, totalTokens.get() > 0 ? totalTokens.get() : null);
		}).doOnError(throwable -> {
			// 处理错误情况 - 使用统一的流式错误方法
			log.error("RAG流式处理出现错误", throwable);
			context.sendStreamError("RAG处理失败：" + throwable.getMessage());
		})
			.onErrorComplete() // 在异常时完成流，停止后续处理
			.blockLast(); // 等待流式处理完成

		// 构建返回结果
		Dict resultDict = Dict.create()
			.set(FlowConstant.RESULT, accumulatedResponse.get())
			.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());

		// 设置RAG搜索状态
		if (hasExtLinks.get()) {
			resultDict.set(FlowConstant.RAG_SEARCHED, YesNoEnum.YES.getCode());
		}
		else {
			resultDict.set(FlowConstant.RAG_SEARCHED, YesNoEnum.NO.getCode());
		}

		return resultDict;
	}

}
