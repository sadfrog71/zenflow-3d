package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkBriefSkillContractValidatorTest {

	static final String VALID = """
			{"type":"object","additionalProperties":false,"required":["title","executiveSummary"],
			 "properties":{"title":{"type":"string"},"executiveSummary":{"type":"string"}}}
			""";

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void allowsMinimalBriefWithOptionalSectionsAbsent() {
		assertDoesNotThrow(() -> validate(VALID, List.of()));
	}

	@Test
	void rejectsGenericJsonUnknownFieldsAndWrongSummaryType() {
		for (String schema : List.of("{}", VALID.replace("false", "true"),
				VALID.replace("executiveSummary\":{\"type\":\"string", "executiveSummary\":{\"type\":\"array"),
				VALID.replace("\"required\":[\"title\",\"executiveSummary\"]", "\"required\":[\"title\"]"))) {
			assertThrows(IllegalArgumentException.class, () -> validate(schema, List.of()));
		}
	}

	@Test
	void validatesReferenceItemsAndProtectsBusinessInput() throws Exception {
		var schema = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(VALID);
		var properties = (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties");
		properties.set("citations", json.readTree("""
				{"type":"array","items":{"type":"object","additionalProperties":false,
				 "required":["refType","refId"],"properties":{"refType":{"type":"string"},"refId":{"type":"integer"}}}}
				"""));
		assertDoesNotThrow(() -> validate(schema.toString(), List.of("tone")));
		assertThrows(IllegalArgumentException.class, () -> validate(schema.toString(), List.of("tasks")));
		((com.fasterxml.jackson.databind.node.ObjectNode) properties.path("citations")
			.path("items")
			.path("properties")
			.path("refId")).put("type", "string");
		assertThrows(IllegalArgumentException.class, () -> validate(schema.toString(), List.of()));
	}

	private void validate(String schema, List<String> parameters) {
		WorkBriefSkillContractValidator.validate(json,
				new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
						new ExecutorRef("PROMPT", null, null, null), "{}", schema, "instructions", List.of(), "{}",
						parameters));
	}

}
