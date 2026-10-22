package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.config.properties.RiskControlProperties;
import com.deepdimen.matrix.knowledge.dto.ChatMessageContext;
import com.deepdimen.matrix.knowledge.entity.AiBillEntity;
import com.deepdimen.matrix.knowledge.service.AiBillService;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 基于用户的风险控制规则
 *
 * 该类实现了基于用户维度的访问频率控制： 1. 限制用户在指定时间窗口内的调用次数 2. 防止单个用户过度使用系统资源 3. 支持配置化的调用次数限制 4.
 * 提供友好的超限提示信息
 *
 * 风控策略： - 统计时间窗口：从昨天开始到现在 - 统计维度：基于用户名（username） - 限制配置：通过AiKnowledgeProperties配置
 *
 * 继承自LiteFlow的NodeComponent，作为流程编排中的风控节点
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("user")
@RequiredArgsConstructor
public class UserRiskControlRule extends NodeComponent {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	private final AiBillService aiBillService;

	/**
	 * 执行用户风控处理
	 *
	 * 处理流程： 1. 从上下文获取用户信息 2. 执行风控检查 3. 如果触发风控限制，设置错误信息并终止流程 4. 否则继续执行后续节点
	 */
	@Override
	public void process() {
		ChatMessageContext messageContext = this.getContextBean(ChatMessageContext.class);

		String risked = riskControl(messageContext.getUsername());
		if (StrUtil.isNotBlank(risked)) {
			messageContext.setErrorMessage(risked);
			// 设置结束
			setIsEnd(true);
		}
	}

	/**
	 * 执行风控检查
	 *
	 * 检查逻辑： 1. 首先判断是否启用风控功能 2. 查询用户从昨天到现在的调用次数 3. 与配置的限制次数进行比较 4. 超限则返回错误提示，否则返回null
	 * @param username 需要检查的用户名
	 * @return 风控提示信息，null表示未触发风控
	 */
	@Nullable
	private String riskControl(String username) {
		RiskControlProperties riskControl = aiKnowledgeProperties.getRiskControl();
		if (!riskControl.isEnabled()) {
			return null;
		}

		// 根据IP查询今天的调用次数
		long count = aiBillService.count(Wrappers.<AiBillEntity>lambdaQuery()
			.eq(StrUtil.isNotBlank(username), AiBillEntity::getCreateBy, username)
			.ge(AiBillEntity::getCreateTime, DateUtil.yesterday()));

		log.info("username:{},count:{}", username, count);
		if (count > riskControl.getTimes()) {
			return riskControl.getErrorMsg();
		}

		return null;
	}

}
