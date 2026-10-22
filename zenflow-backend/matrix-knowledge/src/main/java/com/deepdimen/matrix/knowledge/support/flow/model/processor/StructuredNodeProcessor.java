package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiStructuredNode;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 结构化输出节点处理器 负责处理流程中的结构化JSON生成节点，包括JSON Schema生成、模型调用、格式验证和重试
 *
 * @author caoyuan
 * @date 2025/10/24
 */
@Slf4j
@Component(NodeTypeConstants.STRUCTURED)
@RequiredArgsConstructor
public class StructuredNodeProcessor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于变量替换
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	private final ModelProvider modelProvider;

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 1. 验证节点配置
			AiStructuredNode config = validateNodeConfig(node);

			// 2. 生成 JSON Schema
			String jsonSchema = generateJsonSchema(config.getJsonFields());

			// 3. 获取输入参数并处理消息模板（注入 Schema）
			Dict variables = getInputVariables(node, context);
			List<ChatMessage> chatMessages = processChatMessages(config.getMessages(), variables);

			return processNonStreamModel(chatMessages, config.getModelConfig(), context, node, jsonSchema);
		}
		catch (Exception e) {
			throw FlowException.nodeError(node.getId(), "[结构化输出节点] -> " + e.getMessage());
		}
	}

	/**
	 * 验证结构化输出节点配置的有效性
	 * @param node 节点定义
	 * @return 有效的结构化输出节点配置
	 */
	private AiStructuredNode validateNodeConfig(AiNodeDefinition node) {
		// 检查结构化输出节点配置
		AiStructuredNode config = node.getStructuredParams();
		if (config == null) {
			throw FlowException.invalidParam("结构化输出节点配置无效");
		}

		// 检查模型配置
		AiStructuredNode.Model modelConfig = config.getModelConfig();
		if (modelConfig == null) {
			throw FlowException.invalidParam("结构化输出节点模型配置无效");
		}

		// 检查消息配置
		List<AiStructuredNode.Message> messages = config.getMessages();
		if (messages == null || messages.isEmpty()) {
			throw FlowException.invalidParam("结构化输出节点消息配置无效");
		}

		// 检查 JSON 字段配置
		List<AiStructuredNode.JsonField> jsonFields = config.getJsonFields();
		if (jsonFields == null || jsonFields.isEmpty()) {
			throw FlowException.invalidParam("结构化输出节点JSON字段配置无效");
		}

		return config;
	}

	/**
	 * 根据字段定义生成 JSON Schema
	 * @param fields JSON 字段定义列表
	 * @return JSON Schema 字符串
	 */
	private String generateJsonSchema(List<AiStructuredNode.JsonField> fields) {
		JSONObject schema = new JSONObject();
		schema.set("type", "object");

		JSONObject properties = new JSONObject();
		List<String> required = new ArrayList<>();

		for (AiStructuredNode.JsonField field : fields) {
			if (StrUtil.isBlank(field.getName())) {
				continue;
			}

			JSONObject fieldSchema = new JSONObject();

			// 转换类型（前端类型 -> JSON Schema 类型）
			String type = convertType(field.getType());
			fieldSchema.set("type", type);

			// 添加描述（如果有）
			if (StrUtil.isNotBlank(field.getDescription())) {
				fieldSchema.set("description", field.getDescription());
			}

			properties.set(field.getName(), fieldSchema);
			required.add(field.getName());
		}

		schema.set("properties", properties);
		schema.set("required", required);
		return schema.toString();
	}

	/**
	 * 类型转换（前端类型 -> JSON Schema 类型）
	 * @param frontendType 前端定义的类型
	 * @return JSON Schema 标准类型
	 */
	private String convertType(String frontendType) {
		if (StrUtil.isBlank(frontendType)) {
			return "string";
		}

		return switch (frontendType.toLowerCase()) {
			case "number" -> "number";
			case "boolean" -> "boolean";
			case "object" -> "object";
			case "array" -> "array";
			default -> "string";
		};
	}

	/**
	 * 处理聊天消息，替换变量并注入 JSON Schema
	 * @param messages 原始消息配置
	 * @param variables 变量字典
	 * @return 处理后的ChatMessage列表
	 */
	private List<ChatMessage> processChatMessages(List<AiStructuredNode.Message> messages, Dict variables) {
		// 渲染用户消息
		return messages.stream().map(msg -> {
			// 使用模板引擎渲染消息内容，替换变量
			String renderedContent = engine.getTemplate(msg.getContent()).render(variables);

			// 根据消息角色创建对应类型的ChatMessage
			if (msg.getRole().equalsIgnoreCase(ChatMessageType.AI.name())) {
				return AiMessage.aiMessage(renderedContent);
			}
			return UserMessage.from(renderedContent);
		}).collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * 处理非流式模型请求
	 * @param chatMessages 聊天消息列表
	 * @param modelConfig 模型配置信息
	 * @param context 流程上下文
	 * @param node 节点定义
	 * @param jsonSchema
	 * @return 处理结果字典
	 */
	private Dict processNonStreamModel(List<ChatMessage> chatMessages, AiStructuredNode.Model modelConfig,
			FlowContextHolder context, AiNodeDefinition node, String jsonSchema) {
		// 获取非流式AI助手
		Pair<ChatModel, AiAssistantService> assistantServicePair = modelProvider.getAiAssistant(modelConfig.getModel());

		String userMessage = chatMessages.stream()
			.filter(chatMessage -> chatMessage.type().equals(ChatMessageType.USER))
			.findFirst()
			.map(chatMessage -> {
				UserMessage message = (UserMessage) chatMessage;
				return message.singleText();
			})
			.orElse(StrUtil.EMPTY);

		// 直接调用 AiAssistantService.chat()，内存由框架自动管理
		String systemMsg = PromptBuilder.render("flow-format-json.st") + jsonSchema;
		String response = assistantServicePair.getValue().chat(context.getConversationId(), systemMsg, userMessage);

		// 更新节点执行状态
		node.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
		node.setDuration(context.getDuration());
		context.getExecutedNodes().add(node);

		// JSON 验证和解析
		return validateAndParseJson(response, node);
	}

	/**
	 * 验证并解析 JSON 字符串
	 * @param content 模型输出的内容
	 * @param node 节点定义
	 * @return 包含 JSON 对象的结果字典
	 */
	private Dict validateAndParseJson(String content, AiNodeDefinition node) {
		// 清理字符串（移除代码块标记）
		String repairedContent = JSONRepairUtil.repair(content);

		try {
			// 使用 JSONRepairUtil 修复 JSON 字符串
			log.debug("[结构化输出节点] JSON修复完成，原始内容: {}, 修复后: {}", content, repairedContent);

			// 尝试解析 JSON
			JSONObject jsonObject = JSONUtil.parseObj(repairedContent);

			log.info("[结构化输出节点] JSON解析成功: {}", jsonObject);

			// 返回结果
			return Dict.create()
				.set(FlowConstant.JSON, jsonObject)
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (JSONException e) {
			// JSON 解析失败
			log.error("[结构化输出节点] JSON解析失败，清理后内容: {}", content, e);
			throw FlowException.nodeError(node.getId(), "JSON解析失败: " + e.getMessage() + ", 内容: " + content);
		}
		catch (Exception e) {
			// JSON 修复失败或其他异常
			log.error("[结构化输出节点] JSON修复失败，原始内容: {}", content, e);
			throw FlowException.nodeError(node.getId(), "JSON修复失败: " + e.getMessage() + ", 内容: " + content);
		}
	}

}
