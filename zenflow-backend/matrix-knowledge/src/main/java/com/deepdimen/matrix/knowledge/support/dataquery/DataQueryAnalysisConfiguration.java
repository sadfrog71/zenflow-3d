package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.*;
import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai.dataquery", name = { "enabled", "persistence.enabled" }, havingValue = "true")
class DataQueryAnalysisConfiguration {

	@Bean(destroyMethod = "close")
	DataQueryResultAnalysis dataQueryResultAnalysis(DataSource platform, DataQueryMetadataRepository metadata,
			ObjectMapper json, ModelProvider provider) {
		var jdbc = new JdbcTemplate(platform);
		jdbc.setQueryTimeout(5);
		DataQueryResultAnalysis.Access access = (owner, id) -> {
			var view = metadata.visualizations().read(owner, id);
			if (!"AVAILABLE".equals(view.status()) || view.snapshot() == null)
				throw new DataQueryException("ANALYSIS_SNAPSHOT_UNAVAILABLE");
			if (view.snapshot().rows().isEmpty())
				throw new DataQueryException("ANALYSIS_EMPTY_RESULT");
			var query = metadata.findOwnedQuery(owner, id).orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
			try {
				return new DataQueryResultAnalysis.Source(query.originalQuestion(), view.snapshot(),
						DataQueryMetadataRepository.fingerprint(json.writeValueAsString(view.snapshot())));
			}
			catch (Exception e) {
				throw new DataQueryException("ANALYSIS_SNAPSHOT_UNAVAILABLE");
			}
		};
		DataQueryResultAnalysis.Models models = (owner, id, requested) -> {
			String alias = requested;
			if (alias == null) {
				var names = jdbc.queryForList(
						"SELECT c.model_name FROM ai_chat_record c JOIN ai_dataquery_query q ON q.chat_record_id=c.record_id AND q.tenant_id=c.tenant_id WHERE q.query_id=? AND q.tenant_id=? AND q.user_id=? AND c.del_flag='0'",
						String.class, id, owner.tenantId(), owner.userId());
				if (names.size() != 1 || names.get(0) == null)
					throw new DataQueryException("ANALYSIS_MODEL_UNAVAILABLE");
				alias = names.get(0);
			}
			var choices = jdbc.query(
					"SELECT * FROM ai_model WHERE tenant_id=? AND name=? AND model_type='Chat' AND del_flag='0'",
					new BeanPropertyRowMapper<>(AiModelEntity.class), owner.tenantId(), alias);
			if (choices.size() != 1 || choices.get(0).getId() == null || choices.get(0).getModelName() == null)
				throw new DataQueryException("ANALYSIS_MODEL_UNAVAILABLE");
			var model = choices.get(0);
			// Exact tenant-scoped choice, with its existing thinking/sampling/budget
			// settings.
			// No tool registration, global/default lookup, or persistent configuration
			// changes.
			return new DataQueryResultAnalysis.Model(model.getName(), model.getModelName(),
					() -> provider.buildResolvedAiNoMemoryStreamAssistant(model).getKey());
		};
		return new DataQueryResultAnalysis(platform, json, Clock.systemUTC(), access, models);
	}

}
