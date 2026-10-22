package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;

/** Listed IDs are fenced even if controlled service is disabled or unavailable. */
@Component
public final class DataQueryPilotGate {

	private final Set<Long> dataIds;

	@org.springframework.beans.factory.annotation.Autowired
	private ObjectProvider<DataQueryLegacyFence> legacyFences;

	public void rejectLegacy(Long dataId) {
		Set<Long> pilots = pilotIds();
		if (pilots.isEmpty())
			return;
		if (dataId != null && pilots.contains(dataId))
			throw new DataQueryException("CONTROLLED_QUERY_REQUIRED");
		if (dataId == null || legacyFences == null || legacyFences.getIfAvailable() == null)
			throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
		legacyFences.getIfAvailable().rejectProtectedAlias(dataId, pilots);
	}

	@Value("${ai.dataquery.persistence.enabled:false}")
	private boolean registryRequired;

	private Set<Long> pilotIds() {
		var pilots = new HashSet<>(dataIds);
		if (legacyFences != null && legacyFences.getIfAvailable() != null)
			pilots.addAll(legacyFences.getIfAvailable().registeredPilotIds(registryRequired));
		else if (registryRequired)
			throw new DataQueryException("PILOT_INVENTORY_UNAVAILABLE");
		return pilots;
	}

	private final ObjectProvider<DataQueryQuestionService> questions;

	public DataQueryPilotGate(@Value("${ai.dataquery.pilot-data-ids:}") String ids,
			ObjectProvider<DataQueryQuestionService> questions) {
		this.questions = questions;
		dataIds = ids.isBlank() ? Set.of()
				: Arrays.stream(ids.split(","))
					.map(String::trim)
					.map(Long::valueOf)
					.collect(Collectors.toUnmodifiableSet());
		if (dataIds.stream().anyMatch(id -> id <= 0))
			throw new IllegalArgumentException("Pilot dataset IDs must be positive");
	}

	@org.springframework.beans.factory.annotation.Autowired
	private ObjectProvider<DataQueryDatasetGuidance> datasetGuidance;

	public boolean isMetadataOverviewRequest(ChatMessageDTO message) {
		return DataQueryDatasetOverview.matches(message.getContent());
	}

	public Optional<AiMessageResultDTO> tryMetadataOverview(ChatMessageDTO message,
			DataQueryDatasetGuidance.Generator generator) {
		if (!isMetadataOverviewRequest(message))
			return Optional.empty();
		var service = datasetGuidance == null ? null : datasetGuidance.getIfAvailable();
		return service == null ? Optional.empty() : service.respond(message, generator);
	}

	public boolean isControlled(Long dataId) {
		return dataId != null && pilotIds().contains(dataId);
	}

	@org.springframework.beans.factory.annotation.Autowired
	private ObjectProvider<DataQueryAiChatService> aiChats;

	public DataQueryAiChatService aiChatService() {
		var service = aiChats == null ? null : aiChats.getIfAvailable();
		if (service == null)
			throw new DataQueryException("CONTROLLED_SERVICE_UNAVAILABLE");
		return service;
	}

	public void beginAiChat(ChatMessageDTO message) {
		if (message.getExtDetails() == null || !isControlled(message.getExtDetails().getDataId()))
			throw new DataQueryException("CONTROLLED_QUERY_REQUIRED");
		try {
			message.setDataQueryContext(
					aiChatService().begin(DataQuerySecurity.identity(), DataQuerySecurity.username(), message));
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new DataQueryException("SERVICE_UNAVAILABLE");
		}
	}

	public AiMessageResultDTO process(ChatMessageDTO message) {
		Map<String, Object> response;
		try {
			var identity = DataQuerySecurity.identity();
			if (message.getMessageKey() == null || message.getExtDetails() == null
					|| !isControlled(message.getExtDetails().getDataId()))
				throw new DataQueryException("CONTROLLED_QUERY_REQUIRED");
			var service = questions.getIfAvailable();
			if (service == null)
				throw new DataQueryException("CONTROLLED_SERVICE_UNAVAILABLE");
			response = service.fromChat(identity, DataQuerySecurity.username(), message.getMessageKey(),
					message.getExtDetails().getDataId());
		}
		catch (DataQueryException e) {
			response = DataQueryResponses.failure(e).getBody();
		}
		catch (RuntimeException e) {
			response = DataQueryResponses.envelope("ERROR", "SERVICE_UNAVAILABLE", "受控问数暂不可用，未回退到自由SQL。");
		}
		AiMessageResultDTO result = new AiMessageResultDTO(DataQueryResponses.chatText(response));
		result.setDataQuery(response);
		result.setFunctionName("controlledDataQuery");
		return result;
	}

}
