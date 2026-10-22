package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.Clock;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

/**
 * Configuration preparation is independent of the controlled-execution switch. No
 * auto-migration.
 */
@Configuration(proxyBeanMethods = false)
public class DataQueryStorageConfiguration {

	@Bean
	DataQueryReviewPolicy dataQueryReviewPolicy(
			@Value("${ai.dataquery.review.test-self-review-principal:}") String principal) {
		return DataQueryReviewPolicy.testPrincipal(principal);
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai.dataquery.persistence", name = "enabled", havingValue = "true")
	DataQueryMetadataRepository dataQueryMetadataRepository(DataSource platformDataSource, ObjectMapper json,
			DataQueryReviewPolicy reviewPolicy) {
		return new DataQueryMetadataRepository(platformDataSource, json, Clock.systemUTC(), reviewPolicy);
	}

	@Bean
	@ConditionalOnProperty(prefix = "ai.dataquery.persistence", name = "enabled", havingValue = "true")
	DataQueryCorrectionService dataQueryCorrectionService(DataSource source, DataQueryMetadataRepository metadata,
			ObjectMapper json) {
		return new DataQueryCorrectionService(source, metadata, json, Clock.systemUTC());
	}

	@Bean
	DataQuerySetupService dataQuerySetupService(DataSource source, ObjectMapper json,
			@Value("${ai.dataquery.persistence.enabled:false}") boolean persistence,
			@Value("${ai.dataquery.enabled:false}") boolean execution, DataQueryReviewPolicy reviewPolicy) {
		return new DataQuerySetupService(source, json, Clock.systemUTC(), persistence, execution, reviewPolicy);
	}

}
