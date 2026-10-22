package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DifyChatConfigurationSnapshotTest {

	final ObjectMapper json = new ObjectMapper();

	final String app = "{\"id\":\"app\",\"mode\":\"chat\",\"model_config\":{\"id\":\"config\",\"pre_prompt\":\"Rules {{extended_system_prompt}}\",\"model\":{\"name\":\"fixed\"}}}";

	DifyChatConfigurationSnapshot capture(String text) throws Exception {
		return DifyChatConfigurationSnapshot.capture("app", json.readTree(text), json);
	}

	@Test
	void configurationRequiresActualPromptVariable() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> capture(app.replace("{{extended_system_prompt}}", "plain")));
	}

	@Test
	void wrongAppOrWorkflowCannotBeChatProof() {
		assertThrows(IllegalArgumentException.class, () -> capture(app.replace("\"id\":\"app\"", "\"id\":\"other\"")));
		assertThrows(IllegalArgumentException.class, () -> capture(app.replace("\"chat\"", "\"workflow\"")));
	}

	@Test
	void modelAndPromptDriftRejected() throws Exception {
		var snapshot = capture(app);
		assertThrows(IllegalArgumentException.class,
				() -> snapshot.requireSame(capture(app.replace("fixed", "changed"))));
		assertThrows(IllegalArgumentException.class,
				() -> snapshot.requireSame(capture(app.replace("Rules", "Changed rules"))));
	}

	@Test
	void objectOrderDoesNotCreateFalseDrift() throws Exception {
		var value = json.readTree(app);
		var reordered = json.createObjectNode();
		reordered.set("model_config", value.get("model_config"));
		reordered.put("mode", "chat");
		reordered.put("id", "app");
		capture(app).requireSame(capture(reordered.toString()));
	}

	@Test
	void inconsistentActiveConfigIdRejected() throws Exception {
		var value = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(app);
		value.put("app_model_config_id", "other");
		assertThrows(IllegalArgumentException.class, () -> capture(value.toString()));
	}

}
