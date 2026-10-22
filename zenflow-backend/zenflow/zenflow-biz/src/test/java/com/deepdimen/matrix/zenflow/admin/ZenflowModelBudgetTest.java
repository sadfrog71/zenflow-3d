package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZenflowModelBudgetTest {

	AssistantModelRequest request(String question, List<ModelMessage> history, List<ModelContextFragment> fragments) {
		var model = new ResolvedAssistantModel("v1", "model", "Model", "compatible",
				new ModelCapabilities(true, 2048, 512, true),
				new AssistantEgress(DeploymentDomain.EXTERNAL, "", true, DataClassification.SENSITIVE, ""));
		var identity = new IdentityEnvelope("1", "42", null, List.of(), List.of(), List.of(), "test",
				"TEXT_CONVERSATION", "APP_USER");
		return new AssistantModelRequest("v1", "run", "test", identity, model, "Core instructions", history, fragments,
				question, null, null, 512, true);
	}

	@Test
	void countsChineseUtf8AndReservedOutputRatherThanLatinCharacterHeuristic() {
		assertDoesNotThrow(() -> ZenflowModelBudget.validate(request("hello", List.of(), List.of())));
		var e = assertThrows(AssistantModelException.class,
				() -> ZenflowModelBudget.validate(request("中".repeat(600), List.of(), List.of())));
		assertEquals("MODEL_CONTEXT_BUDGET_EXCEEDED", e.errorCode());
	}

	@Test
	void includesHistoryReferenceWrappersAndPrivateContextInOneBudget() {
		var history = List.of(new ModelMessage(MessageRole.USER, "h".repeat(800)));
		var fragments = List
			.of(new ModelContextFragment("private", "Record", "p".repeat(500), DataClassification.SENSITIVE, Map.of()));
		assertDoesNotThrow(() -> ZenflowModelBudget.validate(request("hello", history, List.of())));
		assertThrows(AssistantModelException.class,
				() -> ZenflowModelBudget.validate(request("hello", history, fragments)));
	}

    @Test void applicationCeilingRemains200kEvenWhenProviderWindowIsLarger(){
        var base=request("x".repeat(200100),List.of(),List.of());
        var model=new ResolvedAssistantModel("v1","model","Model","compatible",new ModelCapabilities(true,1000000,512,true),base.model().assistantEgress());
        var large=new AssistantModelRequest("v1","run","test",base.identity(),model,base.systemPrompt(),List.of(),List.of(),base.userMessage(),null,null,512,true);
        assertThrows(AssistantModelException.class,()->ZenflowModelBudget.validate(large));
    }
    @Test void compactionReplacesOldTurnsWithLabeledSummaryAndRetainsRecentCompletePair(){
        var history=List.of(new ModelMessage(MessageRole.USER,"old-question".repeat(100)),new ModelMessage(MessageRole.ASSISTANT,"old-answer".repeat(100)),new ModelMessage(MessageRole.USER,"recent-question"),new ModelMessage(MessageRole.ASSISTANT,"recent-answer"));
        var compact=ZenflowContextCompaction.compact(request("next",history,List.of()),(old,limit)->"Confirmed: key decision. Open: follow-up. Sources: uncertain.");
        assertEquals(3,compact.history().size());
        assertTrue(compact.history().get(0).content().contains("历史摘要"));
        assertEquals(history.subList(2,4),compact.history().subList(1,3));
        assertFalse(compact.history().toString().contains("old-question"));
        assertDoesNotThrow(()->ZenflowModelBudget.validate(compact));
    }
}
