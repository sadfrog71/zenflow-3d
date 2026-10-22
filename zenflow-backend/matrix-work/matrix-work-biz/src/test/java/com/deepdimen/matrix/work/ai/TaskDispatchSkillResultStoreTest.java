package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TaskDispatchSkillResultStoreTest {

	private final WorkTaskSkillInvocationMapper runs = mock(WorkTaskSkillInvocationMapper.class);

	private final TaskDispatchSkillInputs inputs = mock(TaskDispatchSkillInputs.class);

	private final WorkTaskVisibilityPolicy visibility = mock(WorkTaskVisibilityPolicy.class);

	private final WorkTaskV2Service tasks = mock(WorkTaskV2Service.class);

	private final TaskSkillResultStore store = new TaskSkillResultStore(runs, mock(WorkTaskPolicyMapper.class),
			visibility, tasks, new ObjectMapper(), inputs);

	private final Identity actor = new Identity("42", "3", "3", "work-task-dispatch:10");

	private final Consumer consumer = new Consumer("TASK", "7", "TASK_DISPATCH", "default");

	private final WorkTaskSkillInvocationEntity run = new WorkTaskSkillInvocationEntity();

	private final String output = "{\"assigneeId\":\"4\",\"reason\":\"match\"}";

	@BeforeEach
	void setup() {
		run.setId(10L);
		run.setTenantId(42L);
		run.setOwnerUserId(3L);
		run.setPolicyId(7L);
		run.setDispatchRunId(8L);
		run.setSlotCode("TASK_DISPATCH");
		run.setStatus("PREPARED");
		run.setInputSnapshot("{\"title\":\"T\",\"content\":\"C\",\"candidates\":[{\"userId\":4}]}");
		when(inputs.authorize(actor, consumer, "10")).thenReturn(run);
	}

	@Test
	void authorizedSnapshotIsRequiredAndCannotBeReplacedByCaller() {
		assertTrue(store.supportsConsumer(consumer));
		assertEquals(run.getInputSnapshot(), store.authorizeInput(actor, consumer, "10", run.getInputSnapshot()));
		assertThrows(IllegalArgumentException.class, () -> store.authorizeInput(actor, consumer, "10", "{}"));
		when(inputs.authorize(actor, consumer, "10")).thenThrow(new IllegalArgumentException("LEASE_LOST"));
		assertThrows(IllegalArgumentException.class, () -> store.save(actor, consumer, "10", "20", output));
		verify(runs, never()).saveOnce(any(), any(), any(), any(), any(), any(), any());
		verifyZeroInteractions(tasks, visibility);
	}

	@Test void validSuggestionIsSavedOnceAndReadRequiresTheSameAuthorizedScope() {
        when(runs.saveOnce(42L,3L,7L,10L,"TASK_DISPATCH",20L,output)).thenReturn(1).thenReturn(0);
        assertEquals("work-task-skill:10:20",store.save(actor,consumer,"10","20",output));
        run.setStatus("RAW_RESULT");run.setSkillRunId(20L);run.setSkillOutputJson(output);
        assertEquals("work-task-skill:10:20",store.save(actor,consumer,"10","20",output));
        assertEquals(output,store.read(actor,consumer,"10","20","work-task-skill:10:20"));
        assertThrows(IllegalStateException.class,()->store.save(actor,consumer,"10","21",output));
        when(inputs.authorize(actor,consumer,"10")).thenThrow(new IllegalArgumentException("LEASE_LOST"));
        assertThrows(IllegalArgumentException.class,()->store.read(actor,consumer,"10","20","work-task-skill:10:20"));verifyZeroInteractions(tasks,visibility);
    }

	@Test
	void candidateOutsideFrozenInputNeverReachesPersistence() {
		assertThrows(IllegalArgumentException.class,
				() -> store.save(actor, consumer, "10", "20", output.replace("\"4\"", "\"5\"")));
		verify(runs, never()).saveOnce(any(), any(), any(), any(), any(), any(), any());
		verifyZeroInteractions(tasks);
	}

}
