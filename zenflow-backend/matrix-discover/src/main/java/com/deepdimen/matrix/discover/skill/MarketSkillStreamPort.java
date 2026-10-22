package com.deepdimen.matrix.discover.skill;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.deepdimen.matrix.skill.api.AgentSkillStreamPort;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Uses the current market HTTP response; never opens a second stream or writes success.
 */
@Component
public class MarketSkillStreamPort implements AgentSkillStreamPort {

	private final MarketSkillRequestStore requests;

	private final ObjectMapper json;

	public MarketSkillStreamPort(MarketSkillRequestStore requests, ObjectMapper json) {
		this.requests = requests;
		this.json = json;
	}

	@Override
	public Observer open(Identity identity, Consumer consumer, String businessRunId, String inputJson, String runId,
			String releaseId) {
		String authorized = requests.authorizeInput(identity, consumer, businessRunId, inputJson);
		try {
			if (!"streaming".equals(json.readTree(authorized).path("response_mode").asText()))
				return delta -> {
				};
		}
		catch (java.io.IOException failure) {
			throw new IllegalArgumentException("INPUT_INVALID");
		}
		if (runId == null || !runId.matches("[1-9][0-9]{0,18}") || releaseId == null
				|| !releaseId.matches("[1-9][0-9]{0,18}"))
			throw new IllegalArgumentException("INPUT_INVALID");
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
				|| attributes.getResponse() == null || attributes.getResponse().isCommitted())
			throw new IllegalArgumentException("MARKET_REQUEST_CONTEXT_REQUIRED");
		var response = attributes.getResponse();
		response.setCharacterEncoding("UTF-8");
		response.setContentType("text/event-stream");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("X-Skill-Run-Id", runId);
		response.setHeader("X-Skill-Release-Id", releaseId);
		// Capture the authorized response before the model callback changes threads.
		return new Observer() {
			@Override
			public void accept(String delta) {
				if (delta == null || delta.isEmpty())
					return;
				write("message",
						json.createObjectNode().put("event", "message").put("answer", delta).put("message_id", runId));
			}

			@Override
			public void phase(Phase phase) {
				write("skill_progress",
						json.createObjectNode()
							.put("event", "skill_progress")
							.put("phase", phase.name())
							.put("skillRunId", runId)
							.put("actualReleaseId", releaseId));
			}

			private void write(String type, com.fasterxml.jackson.databind.JsonNode event) {
				synchronized (response) {
					try {
						response.getWriter()
							.write("event: " + type + "\ndata: " + json.writeValueAsString(event) + "\n\n");
						response.getWriter().flush();
						if (response.getWriter().checkError())
							throw new IllegalStateException("MARKET_STREAM_DISCONNECTED");
					}
					catch (java.io.IOException failure) {
						throw new IllegalStateException("MARKET_STREAM_DISCONNECTED", failure);
					}
				}
			}
		};
	}

}
