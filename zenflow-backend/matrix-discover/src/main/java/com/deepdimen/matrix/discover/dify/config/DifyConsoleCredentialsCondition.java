package com.deepdimen.matrix.discover.dify.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Only enable Dify Console API beans when both credentials are explicitly supplied.
 * Avoids putting secret values into a SpEL expression and keeps Service API-only
 * deployments free from accidental Console login attempts.
 */
public final class DifyConsoleCredentialsCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String email = context.getEnvironment().getProperty("matrix.dify.email");
		String password = context.getEnvironment().getProperty("matrix.dify.password");
		if (StringUtils.hasText(email) && StringUtils.hasText(password)) {
			return ConditionOutcome.match("Dify Console credentials are configured");
		}
		return ConditionOutcome.noMatch("Dify Console credentials are not configured");
	}

}
