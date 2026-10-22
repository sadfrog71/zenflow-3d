package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.Map;
import java.util.UUID;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Shared narrow question path for existing databaseChat and optional direct question
 * HTTP.
 */
public final class DataQueryQuestionService {

	private final DataQueryMetadataRepository metadata;

	private final ControlledQueryService queries;

	private final PatrolQuestionInterpreter interpreter = new PatrolQuestionInterpreter();

	public DataQueryQuestionService(DataQueryMetadataRepository metadata, ControlledQueryService queries) {
		this.metadata = metadata;
		this.queries = queries;
	}

	public Map<String, Object> fromChat(Identity identity, String username, long messageId, long dataId) {
		String question = metadata.ownedChatQuestion(identity, username, messageId);
		Map<String, Object> response;
		try {
			response = ask(identity, dataId, question, messageId);
		}
		catch (DataQueryException exception) {
			response = DataQueryResponses.failure(exception).getBody();
		}
		catch (RuntimeException unavailable) {
			response = DataQueryResponses.envelope("ERROR", "SERVICE_UNAVAILABLE", "受控问数暂不可用，未回退到自由SQL。");
		}
		// No LLM callback runs on this path. Save only to the current owner's original
		// chat row.
		metadata.saveOwnedChatAnswer(identity, username, messageId, question, DataQueryResponses.chatText(response));
		return response;
	}

	public Map<String, Object> ask(Identity identity, long dataId, String question, Long messageId) {
		var origin = new QueryOrigin(question, messageId);
		var scope = metadata.resolve(identity);
		if (scope.source() == null || scope.source().dataId() != dataId)
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		var rule = metadata.current(identity);
		if (rule == null || !rule.published())
			throw new DataQueryException("METRIC_DEFINITION_UNCONFIRMED");
		PatrolQueryCompiler.Candidate candidate;
		try {
			candidate = interpreter.interpret(question, rule.intentTerms());
		}
		catch (DataQueryException exception) {
			String id = UUID.randomUUID().toString();
			metadata.started(id, identity, rule.version(), scope.revision(), null);
			metadata.origin(id, identity, origin);
			metadata.finished(id, identity, exception.getMessage(), 0);
			var response = DataQueryResponses.clarification(exception.getMessage(), question);
			response.put("queryId", id);
			response.put("ruleVersion", rule.version());
			return response;
		}
		var validated = queries.validate(identity, candidate);
		if (!validated.ruleVersion().equals(rule.version()))
			throw new DataQueryException("RULE_CHANGED");
		return DataQueryResponses.success(queries.execute(identity, validated.planHandle(), origin));
	}

}
