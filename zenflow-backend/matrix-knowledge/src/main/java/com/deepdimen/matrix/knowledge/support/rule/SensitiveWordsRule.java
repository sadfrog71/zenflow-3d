package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.extra.spring.SpringUtil;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.ChatMessageContext;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper;
import com.deepdimen.matrix.knowledge.support.constant.LLMUseStatusEnums;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 敏感词过滤规则
 *
 * 该类实现了基于敏感词库的风险控制规则，用于： 1. 检测用户输入中的敏感词汇 2. 拦截包含敏感词的对话请求 3. 返回预设的敏感词提示信息 4.
 * 支持知识库级别的敏感词开关配置
 *
 * 继承自LiteFlow的NodeComponent，作为流程编排中的一个节点
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("sensitive")
@RequiredArgsConstructor
public class SensitiveWordsRule extends NodeComponent {

	private final AiChatRecordMapper aiChatRecordMapper;

	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 执行敏感词过滤处理
	 *
	 * 处理流程： 1. 从上下文获取聊天消息和知识库配置 2. 检查是否启用敏感词过滤（支持全局配置和知识库级别配置） 3. 使用敏感词库检测用户输入内容 4.
	 * 如果包含敏感词： - 更新聊天记录状态为"触发敏感词" - 设置错误消息为预配置的敏感词提示 - 终止后续流程执行
	 */
	@Override
	public void process() {
		ChatMessageContext messageContext = this.getContextBean(ChatMessageContext.class);
		AiDatasetEntity aiDataset = messageContext.getAiDataset();

		// 默认的知识库
		if (Objects.isNull(aiDataset)) {
			aiDataset = new AiDatasetEntity();
			aiDataset.setSensitiveFlag(aiKnowledgeProperties.getRiskControl().isSensitiveWord()
					? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode());
			aiDataset.setSensitiveMsg(aiKnowledgeProperties.getRiskControl().getSensitiveMsg());
		}

		// 判断是否开启了敏感词过滤
		if (YesNoEnum.NO.getCode().equals(aiDataset.getSensitiveFlag())) {
			return;
		}

		// 敏感词过滤
		SensitiveWordBs sensitiveWordBs = SpringUtil.getBean(SensitiveWordBs.class);
		if (sensitiveWordBs.contains(messageContext.getChatMessageDTO().getContent())) {
			// 更新回答，触发敏感词
			Long messageKey = messageContext.getChatMessageDTO().getMessageKey();
			AiChatRecordEntity aiChatRecordEntity = new AiChatRecordEntity();
			aiChatRecordEntity.setRecordId(messageKey);
			aiChatRecordEntity.setLlmFlag(LLMUseStatusEnums.SENSITIVE.getStatus());
			aiChatRecordEntity.setAnswerText(aiDataset.getSensitiveMsg());
			aiChatRecordMapper.updateById(aiChatRecordEntity);
			// 更新聊天
			messageContext.setErrorMessage(aiDataset.getSensitiveMsg());
			// 设置结束
			setIsEnd(true);
		}
	}

}
