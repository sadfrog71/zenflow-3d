package com.deepdimen.matrix.common.sse.config;

import com.deepdimen.matrix.common.sse.distribute.LocalMessageDistributor;
import com.deepdimen.matrix.common.sse.distribute.MessageDistributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地的消息分发器配置
 *
 * @author caoyuan
 */
@ConditionalOnProperty(prefix = SseEmitterProperties.PREFIX, name = "message-distributor",
		havingValue = MessageDistributorTypeConstants.LOCAL)
@Configuration(proxyBeanMethods = false)
public class LocalMessageDistributorConfiguration {

	/**
	 * 本地基于内存的消息分发，不支持集群
	 * @return LocalMessageDistributor
	 */
	@Bean
	@ConditionalOnMissingBean(MessageDistributor.class)
	public LocalMessageDistributor sseMessageDistributor() {
		return new LocalMessageDistributor();
	}

}
