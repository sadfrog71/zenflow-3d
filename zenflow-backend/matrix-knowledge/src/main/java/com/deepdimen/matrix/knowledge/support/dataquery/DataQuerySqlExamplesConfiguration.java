package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai.dataquery.sql-examples", name = "enabled", havingValue = "true")
class DataQuerySqlExamplesConfiguration {

	@Bean
	DataQuerySqlExamples dataQuerySqlExamples(DataSource source, ObjectMapper json,
			DataQueryMetadataRepository metadata, @Value("${ai.dataquery.enabled:false}") boolean executionEnabled) {
		var store = new DataQuerySqlExamples(source, json, Clock.systemUTC(), metadata, (identity, scope, sql) -> {
			if (!executionEnabled)
				throw new DataQueryException("CONTROLLED_SERVICE_UNAVAILABLE");
			return new DataQuerySqlExecutor(metadata).execute(identity, scope, sql);
		}, new DataQueryInvestigationAccess(source));
		metadata.sqlExamples(store);
		return store;
	}

}
