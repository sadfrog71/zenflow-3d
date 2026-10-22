package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.config.properties.RiskControlProperties;
import com.deepdimen.matrix.knowledge.dto.ChatMessageContext;
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper;
import com.deepdimen.matrix.knowledge.service.AiBillService;
import com.deepdimen.matrix.knowledge.support.constant.LLMUseStatusEnums;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * IP风控规则组件
 * <p>
 * 基于LiteFlow的节点组件，实现基于IP地址的风控功能。 通过统计IP在一定时间内的调用次数，限制高频访问，防止恶意调用。
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("ip")
@RequiredArgsConstructor
public class IPRiskControlRule extends NodeComponent {

	private final AiChatRecordMapper aiChatRecordMapper;

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final AiBillService aiBillService;

	/**
	 * 执行IP风控检查
	 * <p>
	 * 从上下文中获取IP地址，进行风控检查。 如果触发风控规则，则设置错误信息并终止流程。
	 */
	@Override
	public void process() {
		ChatMessageContext messageContext = this.getContextBean(ChatMessageContext.class);

		String risked = riskControl(messageContext.getIp());
		if (StrUtil.isNotBlank(risked)) {
			messageContext.setErrorMessage(risked);
			// 设置结束
			setIsEnd(true);
		}
	}

	/**
	 * 风控检查逻辑
	 * <p>
	 * 检查指定IP在过去24小时内的调用次数。 如果超过配置的阈值，返回风控错误提示。
	 * @param ip 需要检查的IP地址
	 * @return 风控提示信息，没有触发风控返回null
	 */
	@Nullable
	private String riskControl(String ip) {
		RiskControlProperties riskControl = aiKnowledgeProperties.getRiskControl();
		if (!riskControl.isEnabled()) {
			return null;
		}

		// 根据IP查询今天的调用次数
		long count = aiBillService.count(Wrappers.<AiBillEntity>lambdaQuery()
			.eq(StrUtil.isNotBlank(ip), AiBillEntity::getIp, ip)
			.ge(AiBillEntity::getCreateTime, DateUtil.yesterday()));

		log.info("ip:{},count:{}", ip, count);
		if (count > riskControl.getTimes()) {
			ChatMessageContext messageContext = this.getContextBean(ChatMessageContext.class);
			Long messageKey = messageContext.getChatMessageDTO().getMessageKey();
			AiChatRecordEntity aiChatRecordEntity = new AiChatRecordEntity();
			aiChatRecordEntity.setRecordId(messageKey);
			aiChatRecordEntity.setLlmFlag(LLMUseStatusEnums.USE_LIMIT.getStatus());
			aiChatRecordEntity.setAnswerText(riskControl.getErrorMsg());
			aiChatRecordMapper.updateById(aiChatRecordEntity);
			return riskControl.getErrorMsg();
		}

		return null;
	}

}
