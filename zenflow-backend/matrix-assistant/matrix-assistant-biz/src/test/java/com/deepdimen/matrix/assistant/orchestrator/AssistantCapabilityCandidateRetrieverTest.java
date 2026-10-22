package com.deepdimen.matrix.assistant.orchestrator;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ProviderCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;

import static org.junit.jupiter.api.Assertions.*;

class AssistantCapabilityCandidateRetrieverTest {

	private final AssistantCapabilityCandidateRetriever retriever = new AssistantCapabilityCandidateRetriever();

	@Test
	void retrievesFromDescriptionWithoutRequiringAgentName() {
		var catalog = catalog(List.of(workflow("water", "水质助手", "原水浊度异常分析", List.of()),
				workflow("task", "任务助手", "工单派发与人员管理", List.of())));
		var results = retriever.retrieve("原水浊度异常", catalog, 8);
		assertEquals("water", results.get(0).id());
		assertTrue(results.stream().noneMatch(item -> item.id().equals("task")));
	}

	@Test
	void keepsDuplicateNamesAsAmbiguousCandidates() {
		var catalog = catalog(List.of(workflow("one", "水质助手", "", List.of()), workflow("two", "水质助手", "", List.of())));
		var results = retriever.retrieve("用水质助手分析", catalog, 8);
		assertEquals(2, results.size());
		assertEquals(results.get(0).score(), results.get(1).score());
	}

	@Test
	void shortlistRetainsOnlyAuthorizedDependenciesAndStableCatalogIdentity() {
		var catalog = catalog(List.of(workflow("water", "水质助手", "", List.of("KNOWLEDGE", "UNAUTHORIZED"))));
		var result = retriever.shortlist("用水质助手分析", catalog, 1, List.of("UNAUTHORIZED"));
		assertEquals(List.of("KNOWLEDGE"),
				result.providers().stream().map(ProviderCapabilitySnapshot::providerCode).toList());
		assertEquals("frozen", result.digest());
		assertEquals(catalog.frozenAt(), result.frozenAt());
	}

	@Test
	void emptyQuestionAndEmptyAuthorizedCatalogProduceNoCandidates() {
		assertTrue(retriever.retrieve("", catalog(List.of()), 8).isEmpty());
		assertTrue(retriever
			.retrieve("secret-agent", new FrozenCapabilityCatalog(List.of(), List.of(), "empty", Instant.EPOCH), 8)
			.isEmpty());
		assertThrows(IllegalArgumentException.class, () -> retriever.retrieve("query", catalog(List.of()), 0));
	}

	@Test
	void aliasMatchesRemainAmbiguousBeforeCandidateLimit() {
		var first = workflow("one", "水质助手", "", List.of());
		var second = workflow("two", "客服助手", "", List.of());
		var aliases = List.of("水务小助手", "智能体");
		var one = new WorkflowCapabilitySnapshot(first.capabilityId(), first.releaseId(), "1", first.displayName(), "",
				List.of(), List.of(), "input", "output", "published", "digest", List.of(), aliases);
		var two = new WorkflowCapabilitySnapshot(second.capabilityId(), second.releaseId(), "1", second.displayName(),
				"", List.of(), List.of(), "input", "output", "published", "digest", List.of(), aliases);
		var catalog = catalog(List.of(one, two));
		assertEquals(1, retriever.retrieve("调用水务小助手", catalog, 1).size());
		assertEquals(List.of("one", "two"), retriever.exactWorkflowMatches("调用水务小助手", catalog));
		assertTrue(retriever.exactWorkflowMatches("调用智能体", catalog).isEmpty());
	}

	private FrozenCapabilityCatalog catalog(List<WorkflowCapabilitySnapshot> workflows) {
		return new FrozenCapabilityCatalog(List.of(new ProviderCapabilitySnapshot("KNOWLEDGE", "知识库", "制度", 1, "d")),
				workflows, "frozen", Instant.EPOCH);
	}

	private WorkflowCapabilitySnapshot workflow(String id, String name, String description, List<String> dependencies) {
		return new WorkflowCapabilitySnapshot(id, id.hashCode(), "1", name, description, List.of(), List.of(), "input",
				"output", "published", "digest", dependencies);
	}

}
