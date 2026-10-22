package com.deepdimen.matrix.common.audit;

import com.deepdimen.matrix.admin.api.feign.RemoteAuditLogService;
import com.deepdimen.matrix.common.audit.aop.AuditAspect;
import com.deepdimen.matrix.common.audit.handle.DefaultAuditLogHandle;
import com.deepdimen.matrix.common.audit.handle.IAuditLogHandle;
import com.deepdimen.matrix.common.audit.handle.ICompareHandle;
import com.deepdimen.matrix.common.audit.handle.JavesCompareHandle;
import com.deepdimen.matrix.common.audit.support.SpelParser;
import com.deepdimen.matrix.common.core.util.KeyStrResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Optional;

/**
 * 审计自动配置类
 *
 * @author caoyuan
 * @date 2023/2/26
 */
@EnableAsync
@AutoConfiguration
@Import({ AuditAspect.class, SpelParser.class })
public class AuditAutoConfiguration {

	/**
	 * 默认注入 javers 的比较器实现
	 * @param auditNameHandleOptional 注入审计用户来源
	 * @return ICompareHandle
	 */
	@Bean
	@ConditionalOnMissingBean
	public ICompareHandle compareHandle(Optional<IAuditLogHandle> auditNameHandleOptional) {
		return new JavesCompareHandle(auditNameHandleOptional);
	}

	/**
	 * 默认的审计日志存储策略
	 * @return DefaultAuditLogHandle
	 */
	@Bean
	@ConditionalOnMissingBean
	public IAuditLogHandle auditLogHandle(RemoteAuditLogService logService, KeyStrResolver tenantKeyStrResolver) {
		return new DefaultAuditLogHandle(logService, tenantKeyStrResolver);
	}

}
