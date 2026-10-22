package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 无限制规则组件
 * <p>
 * 基于LiteFlow的节点组件，用于处理无需风控限制的场景。 当用户被判定为无限制用户时，执行此空任务节点，仅记录日志。
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("noLimit")
@RequiredArgsConstructor
public class NoLimitRule extends NodeComponent {

	/**
	 * 执行无限制处理
	 * <p>
	 * 对于无需风控的用户，此方法仅记录日志信息。 实际业务处理将继续执行，不会被风控规则阻断。
	 */
	@Override
	public void process() {
		// 记录当前用户的无限制状态
		log.debug("当前用户无风控，无限制任务 {}", SecurityUtils.getUser().getUsername());
	}

}
