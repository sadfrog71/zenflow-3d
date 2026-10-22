package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.DbgptProperties;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.config.properties.DefaultModelProperties;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.dto.dbgpt.DbgptChatRequest;
import com.deepdimen.matrix.knowledge.dto.dbgpt.DbgptChatResponse;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.DbgptApiService;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * DBGPT聊天规则实现
 * <p>
 * 基于DBGPT API的数据库查询聊天模式，将用户的自然语言转换为SQL查询并执行。 支持自动解析DBGPT返回的图表数据，并提供结构化的响应。
 *
 * @author caoyuan
 * @date 2024/10/9
 */
@Slf4j
@Component("dbgptChat")
@RequiredArgsConstructor
public class DbgptChatRule implements ChatRule {

	private final DbgptApiService dbgptApiService;

	private final DbgptProperties dbgptProperties;

	/**
	 * 处理DBGPT聊天请求
	 * <p>
	 * 主要流程： 1. 构建DBGPT API请求 2. 调用DBGPT API获取响应 3. 解析响应内容，提取图表数据（如果有） 4. 返回格式化的响应结果
	 * @param chatMessageDTO 聊天上下文信息
	 * @return AI响应结果流，包含查询结果和可选的图表信息
	 */
	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		try {
			// 构建DBGPT请求
			DbgptChatRequest request = buildDbgptRequest(chatMessageDTO);

			log.info("Processing DBGPT chat request: {}", JSONUtil.toJsonStr(request));

			// 调用DBGPT API
			DbgptChatResponse response = dbgptApiService.chatCompletions(request);

			// 解析响应
			return parseDbgptResponse(response);

		}
		catch (Exception e) {
			log.error("DBGPT聊天处理异常", e);
			return Flux.just(new AiMessageResultDTO("DBGPT处理失败: " + e.getMessage()));
		}
	}

	/**
	 * 构建DBGPT API请求
	 * @param chatMessageDTO 聊天消息DTO
	 * @return DBGPT请求对象
	 */
	private DbgptChatRequest buildDbgptRequest(ChatMessageDTO chatMessageDTO) {
		// 从扩展详情中获取数据库名称，如果没有则使用默认值
		String database = dbgptProperties.getDatabase();

		// 从消息中获取模型名称，如果没有则使用默认值
		String model = StrUtil.isNotBlank(chatMessageDTO.getModelName())
				? getModelByName(chatMessageDTO.getModelName(), ModelTypeEnums.CHAT).getModelName()
				: dbgptProperties.getModel();

		return DbgptChatRequest.builder()
			.messages(chatMessageDTO.getContent())
			.model(model)
			.chat_mode(dbgptProperties.getChatMode())
			.chat_param(database)
			.build();
	}

	/**
	 * 解析DBGPT API响应
	 * @param response DBGPT API响应
	 * @return 解析后的消息结果流
	 */
	private Flux<AiMessageResultDTO> parseDbgptResponse(DbgptChatResponse response) {
		if (Objects.isNull(response) || Objects.isNull(response.getChoices()) || response.getChoices().isEmpty()) {
			return Flux.just(new AiMessageResultDTO("DBGPT返回空响应"));
		}

		DbgptChatResponse.Choice choice = response.getChoices().get(0);
		if (Objects.isNull(choice.getMessage()) || StrUtil.isBlank(choice.getMessage().getContent())) {
			return Flux.just(new AiMessageResultDTO("DBGPT返回空内容"));
		}

		String content = choice.getMessage().getContent();
		log.info("DBGPT response content: {}", content);

		// 直接返回content内容，不再解析图表数据
		AiMessageResultDTO resultDTO = new AiMessageResultDTO(content);

		return Flux.just(resultDTO);
	}

	/**
	 * 根据模型名称获取模型配置
	 * <p>
	 * 从数据库中查询指定名称的模型配置。 如果没有指定模型名称，则返回指定类型的默认模型。 如果模型不存在，则使用系统内置的默认模型配置。
	 * </p>
	 * @param modelName 模型名称，可以为空
	 * @param modelTypeEnum 模型类型枚举
	 * @return 模型配置实体
	 */
	public AiModelEntity getModelByName(String modelName, ModelTypeEnums modelTypeEnum) {
		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);

		AiModelEntity aiModelEntity;
		if (StrUtil.isNotBlank(modelName)) {
			aiModelEntity = modelMapper
				.selectOne(Wrappers.<AiModelEntity>lambdaQuery().eq(AiModelEntity::getName, modelName), false);
		}
		else {
			aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
				.eq(AiModelEntity::getModelType, modelTypeEnum.getType())
				.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode()), false);
		}

		if (Objects.isNull(aiModelEntity)) {
			log.error("模型不存在:{}，请先在模型配置中添加，默认使用系统内置模型", modelName);
			AiModelEntity defaultModel = new AiModelEntity();
			AiKnowledgeProperties aiKnowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
			DefaultModelProperties modelProperties = aiKnowledgeProperties.getDefaultModel();
			defaultModel.setName(modelProperties.getModel().getCode());
			defaultModel.setApiKey(modelProperties.getApiKey());
			defaultModel.setBaseUrl(modelProperties.getBaseUrl());
			defaultModel.setModelName(modelProperties.getModel().getCode());
			return defaultModel;
		}
		return aiModelEntity;
	}

}
