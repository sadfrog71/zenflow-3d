package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in only. Unconfigured adapters deny access; no TOP or platform credentials are
 * inferred.
 */
@org.springframework.context.annotation.Import(DataQueryStorageConfiguration.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai.dataquery", name = "enabled", havingValue = "true")
public class DataQueryConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "ai.dataquery.persistence", name = "enabled", havingValue = "true")
	DataQueryAiChatService dataQueryAiChatService(DataQueryMetadataRepository metadata) {
		return new DataQueryAiChatService(metadata);
	}

	@Bean
	@ConditionalOnMissingBean
	ControlledQueryService.AuthorizationResolver dataQueryAuthorizationResolver(
			ObjectProvider<DataQueryMetadataRepository> repositories) {
		var repository = repositories.getIfAvailable();
		if (repository != null)
			return repository::resolve;
		return identity -> {
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		};
	}

	@Bean
	@ConditionalOnMissingBean
	ControlledQueryService.RuleProvider dataQueryRuleProvider(
			ObjectProvider<DataQueryMetadataRepository> repositories) {
		var repository = repositories.getIfAvailable();
		if (repository != null)
			return repository::current;
		return identity -> null;
	}

	@Bean
	@ConditionalOnMissingBean
	ControlledQueryService.QueryExecutor dataQueryExecutor(ObjectProvider<DataQueryMetadataRepository> repositories) {
		var repository = repositories.getIfAvailable();
		if (repository != null)
			return new ApprovedDatasourceQueryExecutor(repository);
		return query -> {
			throw new DataQueryException("DATASOURCE_NOT_CONFIGURED");
		};
	}

	@Bean
	@ConditionalOnMissingBean
	ControlledQueryService.QueryAudit dataQueryAudit(ObjectProvider<DataQueryMetadataRepository> repositories) {
		var repository = repositories.getIfAvailable();
		return new ControlledQueryService.QueryAudit() {
			@Override
			public void started(String id, ControlledQueryService.Identity identity, String version, String revision,
					PatrolQueryCompiler.Candidate candidate) {
				if (repository == null)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
				repository.started(id, identity, version, revision, candidate);
			}

			@Override
			public void finished(String id, ControlledQueryService.Identity identity, String code, int groups) {
				if (repository == null)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
				repository.finished(id, identity, code, groups);
			}

			@Override
			public void origin(String id, ControlledQueryService.Identity identity,
					ControlledQueryService.QueryOrigin origin) {
				if (repository == null)
					throw new DataQueryException("AUDIT_UNAVAILABLE");
				repository.origin(id, identity, origin);
			}
		};
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai.dataquery.persistence", name = "enabled", havingValue = "true")
	DataQueryQuestionService dataQueryQuestionService(DataQueryMetadataRepository metadata,
			ControlledQueryService queries) {
		return new DataQueryQuestionService(metadata, queries);
	}

	@Bean
	ControlledQueryService controlledQueryService(ControlledQueryService.AuthorizationResolver authorization,
			ControlledQueryService.RuleProvider rules, ControlledQueryService.QueryExecutor executor,
			ControlledQueryService.QueryAudit audit) {
		return new ControlledQueryService(authorization, rules, executor, audit, Clock.systemUTC(), 1000,
				Duration.ofMinutes(5));
	}

}
