package com.deepdimen.matrix.assistant.orchestrator;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Real deterministic router observations only; deferred decisions require model/E2E
 * evaluation.
 */
class AssistantRoutingGoldenTest {

	@Test
	void fixedCorpusExercisesRouterWithoutPretendingDeferredDecisionsWereEvaluated() throws Exception {
		Path root = Path.of("").toAbsolutePath();
		while (!Files.isRegularFile(root.resolve("docs/assistant/fixtures/unified-assistant-routing-golden.jsonl"))) {
			root = root.getParent();
			assertNotNull(root, "Repository fixture not found");
		}
		var mapper = new ObjectMapper();
		var router = new AssistantSemanticRouter();
		var observed = new ArrayList<Map<String, Object>>();
		var ids = new HashSet<String>();
		var acceptance = new HashSet<String>();
		for (String line : Files
			.readAllLines(root.resolve("docs/assistant/fixtures/unified-assistant-routing-golden.jsonl"))) {
			var row = mapper.readTree(line);
			String id = row.path("id").asText();
			assertTrue(ids.add(id), "duplicate " + id);
			acceptance.add(row.path("acceptance").asText());
			assertTrue(Set.of("ROUTE", "DEFER").contains(row.path("fastExpected").asText()), id);
			var result = router.fastRoute(row.path("question").asText(), catalog(row.path("catalog").asText()));
			assertEquals(row.path("fastExpected").asText().equals("ROUTE"), result.isPresent(), id);
			var item = new LinkedHashMap<String, Object>();
			item.put("id", id);
			item.put("evidenceLevel", "RULE_ONLY");
			item.put("observed", result.isPresent());
			item.put("finalEvaluation", "NOT_EVALUATED");
			item.put("contextApplied", false);
			if (result.isPresent()) {
				var route = result.orElseThrow();
				var actual = mapper.valueToTree(route);
				assertEquals(row.path("expected").path("intent").asText(), actual.path("conversationIntent").asText(),
						id);
				assertEquals(row.path("expected").path("mode").asText(), actual.path("executionMode").asText(), id);
				assertEquals(row.path("expected").path("workflowIds"), actual.path("selectedWorkflowIds"), id);
				item.put("route", route);
			}
			observed.add(item);
		}
		for (int i = 1; i <= 10; i++)
			assertTrue(acceptance.contains("AE" + i), "Missing AE" + i);
		Path out = Path.of("target/unified-assistant-routing-rule-observations.json");
		Files.createDirectories(out.getParent());
		mapper.writerWithDefaultPrettyPrinter()
			.writeValue(out.toFile(), Map.of("schemaVersion", 1, "evidenceLevel", "RULE_ONLY", "cases", observed,
					"releaseReady", false, "fixtureSha256",
					HexFormat.of()
						.formatHex(java.security.MessageDigest.getInstance("SHA-256")
							.digest(Files.readAllBytes(
									root.resolve("docs/assistant/fixtures/unified-assistant-routing-golden.jsonl")))),
					"observedRoutes",
					observed.stream().filter(item -> Boolean.TRUE.equals(item.get("observed"))).count(),
					"deferredCases",
					observed.stream().filter(item -> Boolean.FALSE.equals(item.get("observed"))).count()));
	}

	private WorkflowCapabilitySnapshot workflow(String id, String name) {
		return new WorkflowCapabilitySnapshot(id, 1, "v1", name, "监测水质与异常分析", List.of(), List.of(), "in", "out", "app",
				"digest");
	}

	private FrozenCapabilityCatalog catalog(String name) {
		List<WorkflowCapabilitySnapshot> workflows = switch (name) {
			case "inspection" -> List.of(workflow("equipment", "设备巡检智能体"), workflow("network", "管网巡检智能体"));
			case "same-name" -> List.of(workflow("equipment", "巡检智能体"), workflow("network", "巡检智能体"));
			case "water" -> List.of(workflow("water", "水质分析智能体"));
			default -> throw new IllegalArgumentException("Unknown catalog");
		};
		return new FrozenCapabilityCatalog(List.of(), workflows, "golden-v1-" + name, Instant.EPOCH);
	}

}
