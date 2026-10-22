package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.WebUtils;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageContext;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.service.ChatService;
import com.deepdimen.matrix.knowledge.support.constant.ChatTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.LLMUseStatusEnums;
import com.deepdimen.matrix.knowledge.support.rule.ChatRule;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.deepdimen.matrix.knowledge.dto.ChatSendDTO;

import static com.deepdimen.matrix.knowledge.support.constant.AiChatConstants.END_MSG;

/**
 * AI聊天服务实现类
 *
 * @author caoyuan
 * @date 2024/3/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements ChatService {

	public static final String CHAT_CHAIN_ID = "chat";

	private final Optional<FlowExecutor> flowExecutorOptional;

	private final Map<String, ChatRule> chatRuleMap;

	private final com.deepdimen.matrix.knowledge.support.dataquery.DataQueryPilotGate dataQueryPilotGate;

	private final AiDatasetMapper datasetMapper;

	private final AiChatRecordMapper recordMapper;

	private final ChatMemoryProvider chatMemoryProvider;

	private final MessageWindowChatMemory messageWindowChatMemory;

	@Autowired
	private ApplicationContext applicationContext;

	/** 缓存灰度路由反射方法，避免每次调用重复查找 */
	private volatile Method resolveGrayConfigMethod;

	/** 缓存智能体 getById 反射方法 */
	private volatile Method agentGetByIdMethod;

	/** 缓存智能体 getConfig 反射方法 */
	private volatile Method agentGetConfigMethod;

	@Override
	public R saveConnectionParams(ChatMessageDTO chatMessageDTO) {
		AiChatRecordEntity recordEntity = new AiChatRecordEntity();
		recordEntity.setQuestionText(chatMessageDTO.getContent());
		recordEntity.setConversationId(chatMessageDTO.getConversationId());
		recordEntity.setDatasetId(chatMessageDTO.getDatasetId());
		recordEntity.setLlmFlag(LLMUseStatusEnums.UNUSE.getStatus());
		recordEntity.setInnerFlag(YesNoEnum.NO.getCode());
		recordEntity.setStandardFlag(YesNoEnum.NO.getCode());
		recordEntity.setSuitability(YesNoEnum.NO.getCode());
		recordEntity.setModelName(chatMessageDTO.getModelName());
		if (Objects.nonNull(chatMessageDTO.getExtDetails())) {
			ChatMessageDTO.ExtDetails extDetails = chatMessageDTO.getExtDetails();
			extDetails.setAccessToken(SecurityUtils.getToken());
			extDetails.setTenantId(TenantContextHolder.getTenantId());
			recordEntity.setExtDetails(JSONUtil.toJsonPrettyStr(extDetails));
		}

		if (Objects.nonNull(SecurityUtils.getUser())) {
			chatMessageDTO.setInner(true);
			recordEntity.setInnerFlag(YesNoEnum.YES.getCode());
			recordEntity.setUsername(SecurityUtils.getUser().getUsername());
		}
		else {
			recordEntity.setIp(JakartaServletUtil.getClientIP(WebUtils.getRequest()));
			recordEntity.setUserAgent(WebUtils.getRequest().getHeader(HttpHeaders.USER_AGENT));
			recordEntity.setUsername(SecureUtil.md5(recordEntity.getIp() + recordEntity.getUserAgent()));
		}

		recordEntity.setAgentId(chatMessageDTO.getAgentId());
		recordEntity.setWebsearchFlag(YesNoEnum.getCode(chatMessageDTO.isWebsearch()));
		recordMapper.insert(recordEntity);

		if (chatMessageDTO.isInner()) {
			return R.ok(recordEntity.getRecordId());
		}

		AiDatasetEntity aiDatasetEntity = datasetMapper.selectById(chatMessageDTO.getDatasetId());
		if (Objects.isNull(aiDatasetEntity)) {
			return R.ok(recordEntity.getRecordId());
		}

		if (YesNoEnum.YES.getCode().equals(aiDatasetEntity.getPublicFlag())
				&& StrUtil.isNotBlank(aiDatasetEntity.getPublicPassword())) {
			if (!aiDatasetEntity.getPublicPassword().equals(chatMessageDTO.getAccessKey())) {
				return R.failed("此知识库为私有知识库，请先输入【访问密码】");
			}
		}

		return R.ok(recordEntity.getRecordId());
	}

	@Override
	public boolean clearMemory(String id) {
		ChatMemory chatMemory = chatMemoryProvider.get(id);
		if (Objects.nonNull(chatMemory)) {
			chatMemory.clear();
		}
		messageWindowChatMemory.clear(id);
		return true;
	}

	@Override
	public Flux<AiMessageResultDTO> chatList(Long key) {
		AiChatRecordEntity recordEntity = recordMapper.selectById(key);
		if (Objects.isNull(recordEntity)) {
			return Flux.just(new AiMessageResultDTO("链接已失效"), new AiMessageResultDTO(END_MSG));
		}

		ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
		chatMessageDTO.setMessageKey(key);
		chatMessageDTO.setModelName(recordEntity.getModelName());
		chatMessageDTO.setContent(recordEntity.getQuestionText());
		chatMessageDTO.setConversationId(recordEntity.getConversationId());
		chatMessageDTO.setDatasetId(recordEntity.getDatasetId());
		chatMessageDTO.setWebsearch(YesNoEnum.YES.getCode().equals(recordEntity.getWebsearchFlag()));
		chatMessageDTO.setInner(YesNoEnum.YES.getCode().equals(recordEntity.getInnerFlag()));
		if (Objects.nonNull(recordEntity.getExtDetails())) {
			ChatMessageDTO.ExtDetails extDetails = JSONUtil.toBean(recordEntity.getExtDetails(),
					ChatMessageDTO.ExtDetails.class);
			chatMessageDTO.setExtDetails(extDetails);
		}

		// 智能体灰度路由：单体模式下动态获取 AiAgentVersionService
		if (Objects.nonNull(recordEntity.getAgentId())) {
			chatMessageDTO.setAgentId(recordEntity.getAgentId());
			resolveAgentGrayConfig(chatMessageDTO, recordEntity.getAgentId());
		}

		if (ChatTypeEnums.fromCode(chatMessageDTO.getDatasetId()) == ChatTypeEnums.DATABASE_CHAT
				&& chatMessageDTO.getExtDetails() != null
				&& (dataQueryPilotGate.isControlled(chatMessageDTO.getExtDetails().getDataId())
						|| dataQueryPilotGate.isMetadataOverviewRequest(chatMessageDTO))) {
			ChatMessageContextHolder.set(chatMessageDTO);
			return chatRuleMap.get(ChatTypeEnums.DATABASE_CHAT.getType()).processStreaming(chatMessageDTO).map(result -> {
				result.setMessageKey(key.toString());
				return result;
			}).concatWithValues(new AiMessageResultDTO(END_MSG));
		}

		if (flowExecutorOptional.isPresent()) {
			Flux<AiMessageResultDTO> aiMessageResultDTO = flowRisk(chatMessageDTO);
			if (aiMessageResultDTO != null)
				return aiMessageResultDTO.concatWithValues(new AiMessageResultDTO(END_MSG));
		}

		ChatTypeEnums chatTypeEnums = ChatTypeEnums.fromCode(chatMessageDTO.getDatasetId());
		ChatMessageContextHolder.set(chatMessageDTO);
		return chatRuleMap.get(chatTypeEnums.getType()).processStreaming(chatMessageDTO).map(aiMessageResultDTO -> {
			aiMessageResultDTO.setMessageKey(key.toString());
			return aiMessageResultDTO;
		}).concatWithValues(new AiMessageResultDTO(END_MSG));
	}

	/**
	 * 通过反射动态调用 AiAgentVersionService.resolveGrayConfig，避免编译时依赖 matrix-discover
	 */
	private void resolveAgentGrayConfig(ChatMessageDTO chatMessageDTO, Long agentId) {
		try {
			Object versionService = applicationContext.getBean("aiAgentVersionServiceImpl");
			Long userId = Objects.nonNull(SecurityUtils.getUser()) ? SecurityUtils.getUser().getId() : null;
			if (userId == null) {
				// 未登录时也加载默认config，确保下游有配置
				loadAgentDefaultConfig(chatMessageDTO, agentId);
				return;
			}
			if (resolveGrayConfigMethod == null) {
				synchronized (this) {
					if (resolveGrayConfigMethod == null) {
						resolveGrayConfigMethod = versionService.getClass()
							.getMethod("resolveGrayConfig", Long.class, Long.class);
					}
				}
			}
			String grayConfig = (String) resolveGrayConfigMethod.invoke(versionService, agentId, userId);
			if (StrUtil.isNotBlank(grayConfig)) {
				chatMessageDTO.setAgentConfig(grayConfig);
				log.info("命中灰度版本, agentId={}, userId={}", agentId, userId);
			}
			else {
				// 未命中灰度时，加载智能体发布版本的默认config
				loadAgentDefaultConfig(chatMessageDTO, agentId);
			}
		}
		catch (Exception e) {
			log.debug("灰度路由跳过（非单体模式或服务未就绪）: {}", e.getMessage());
			// 灰度路由不可用时，也尝试加载默认config
			loadAgentDefaultConfig(chatMessageDTO, agentId);
		}
	}

	/**
	 * 通过反射加载智能体发布版本的默认config，避免编译时依赖 matrix-discover
	 */
	private void loadAgentDefaultConfig(ChatMessageDTO chatMessageDTO, Long agentId) {
		try {
			Object agentService = applicationContext.getBean("aiAgentServiceImpl");
			if (agentGetByIdMethod == null) {
				synchronized (this) {
					if (agentGetByIdMethod == null) {
						agentGetByIdMethod = agentService.getClass().getMethod("getById", java.io.Serializable.class);
					}
				}
			}
			Object agentEntity = agentGetByIdMethod.invoke(agentService, agentId);
			if (agentEntity != null) {
				if (agentGetConfigMethod == null) {
					synchronized (this) {
						if (agentGetConfigMethod == null) {
							agentGetConfigMethod = agentEntity.getClass().getMethod("getConfig");
						}
					}
				}
				String config = (String) agentGetConfigMethod.invoke(agentEntity);
				if (StrUtil.isNotBlank(config)) {
					chatMessageDTO.setAgentConfig(config);
					log.debug("加载智能体默认配置, agentId={}", agentId);
				}
			}
		}
		catch (Exception e) {
			log.debug("加载智能体默认配置失败, agentId={}: {}", agentId, e.getMessage());
		}
	}

	@Override
	public Flux<AiMessageResultDTO> chatSend(ChatSendDTO chatSendDTO) {
		// 使用第一个 datasetId 作为主知识库
		Long primaryDatasetId = chatSendDTO.getDatasetIds().get(0);

		// 构建 ChatMessageDTO 复用现有流程
		ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
		chatMessageDTO.setContent(chatSendDTO.getMessage());
		chatMessageDTO.setConversationId(chatSendDTO.getConversationId());
		chatMessageDTO.setDatasetId(primaryDatasetId);
		chatMessageDTO.setAgentId(chatSendDTO.getAgentId());
		chatMessageDTO.setModelName(chatSendDTO.getModelName());
		chatMessageDTO.setWebsearch(chatSendDTO.isWebsearch());

		// 处理文件URL列表
		if (chatSendDTO.getFileUrls() != null && !chatSendDTO.getFileUrls().isEmpty()) {
			ChatMessageDTO.ExtDetails extDetails = new ChatMessageDTO.ExtDetails();
			List<ChatMessageDTO.FileDTO> files = chatSendDTO.getFileUrls().stream().map(url -> {
				ChatMessageDTO.FileDTO fileDTO = new ChatMessageDTO.FileDTO();
				fileDTO.setUrl(url);
				return fileDTO;
			}).toList();
			extDetails.setFiles(files);
			chatMessageDTO.setExtDetails(extDetails);
		}

		// 保存连接参数（创建记录）
		R<String> connectionResult = saveConnectionParams(chatMessageDTO);
		if (!connectionResult.isOk()) {
			return Flux.just(new AiMessageResultDTO(connectionResult.getMsg()), new AiMessageResultDTO(END_MSG));
		}

		Long messageKey = Long.parseLong(connectionResult.getData().toString());

		// 复用 chatList 流程（含灰度路由、chatRuleMap 路由等）
		return chatList(messageKey);
	}

	private @Nullable Flux<AiMessageResultDTO> flowRisk(ChatMessageDTO chatMessageDTO) {
		AiDatasetEntity aiDatasetEntity = datasetMapper.selectById(chatMessageDTO.getDatasetId());
		ChatMessageContext content = new ChatMessageContext();
		content.setChatMessageDTO(chatMessageDTO);
		content.setAiDataset(aiDatasetEntity);
		content.setIp(JakartaServletUtil.getClientIP(WebUtils.getRequest()));

		if (Objects.nonNull(SecurityUtils.getUser())) {
			content.setUsername(SecurityUtils.getUser().getUsername());
			content.setUserId(SecurityUtils.getUser().getId());
		}

		LiteflowResponse response = flowExecutorOptional.get().execute2Resp(CHAT_CHAIN_ID, null, content);
		ChatMessageContext result = response.getContextBean(ChatMessageContext.class);
		if (Objects.isNull(result.getMessageResultDTO())) {
			AiMessageResultDTO aiMessageResultDTO = new AiMessageResultDTO();
			if (StrUtil.isNotBlank(result.getErrorMessage())) {
				aiMessageResultDTO.setMessage(result.getErrorMessage());
				return Flux.just(aiMessageResultDTO);
			}
		}
		return null;
	}

}
