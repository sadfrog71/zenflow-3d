package com.deepdimen.matrix.knowledge.support.rule;

import cn.hutool.core.collection.CollUtil;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 无限制用户判断规则
 * <p>
 * 基于LiteFlow的布尔节点组件，用于判断当前用户是否在风控白名单中。 白名单用户可以跳过风控限制，享有无限制的访问权限。
 *
 * @author caoyuan
 * @date 2024/4/14
 */
@Slf4j
@Component("isNoLimit")
@RequiredArgsConstructor
public class IsNoLimitRule extends NodeBooleanComponent {

	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 判断用户是否无限制
	 * <p>
	 * 检查当前登录用户是否在配置的白名单中。 白名单用户不受风控规则限制。
	 * @return true表示用户无限制，false表示需要进行风控检查
	 * @throws Exception 处理异常
	 */
	@Override
	public boolean processBoolean() throws Exception {
		// 如果白名单为空，所有用户都需要风控
		if (CollUtil.isEmpty(aiKnowledgeProperties.getRiskControl().getNoLimitUsernames())) {
			return false;
		}

		// 获取当前用户名并检查是否在白名单中
		String username = SecurityUtils.getUser().getUsername();
		return aiKnowledgeProperties.getRiskControl().getNoLimitUsernames().contains(username);
	}

}
