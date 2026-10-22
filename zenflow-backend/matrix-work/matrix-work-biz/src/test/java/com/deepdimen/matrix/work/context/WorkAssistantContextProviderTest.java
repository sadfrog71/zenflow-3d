package com.deepdimen.matrix.work.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.testkit.AssistantContextProviderContract;
import com.deepdimen.matrix.work.api.enums.WorkScheduleStatus;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkScheduleEntity;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.mapper.WorkScheduleMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;

@ExtendWith(MockitoExtension.class)
class WorkAssistantContextProviderTest extends AssistantContextProviderContract {

	private static final IdentityEnvelope AUTHORIZED = identity("42");

	@Mock
	private WorkTaskMapper taskMapper;

	@Mock
	private WorkScheduleMapper scheduleMapper;

	private WorkAssistantContextProvider provider;

	private WorkTaskEntity task;

	@BeforeEach
	void setUp() {
		var directory = org.mockito.Mockito.mock(com.deepdimen.matrix.admin.api.service.TenantUserDirectory.class);
		org.mockito.Mockito.lenient()
			.when(directory.isAssignableUser(org.mockito.ArgumentMatchers.anyLong(),
					org.mockito.ArgumentMatchers.anyLong()))
			.thenReturn(true);
		org.mockito.Mockito.lenient()
			.when(directory.taskAuthority(org.mockito.ArgumentMatchers.anyLong(),
					org.mockito.ArgumentMatchers.anyLong()))
			.thenAnswer(i -> new com.deepdimen.matrix.admin.api.service.TenantUserDirectory.TaskAuthority(
					i.getArgument(1), null, false, java.util.List.of()));
		var visibility = new com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy(directory, null);
		provider = new WorkAssistantContextProvider(taskMapper, scheduleMapper, visibility);
		task = task(10L);
		lenient().when(taskMapper.selectList(any())).thenReturn(List.of(task));
		lenient().when(scheduleMapper.selectList(any())).thenReturn(List.of());
		lenient().when(taskMapper.selectOne(any())).thenAnswer(invocation -> {
			AbstractWrapper<?, ?, ?> wrapper = invocation.getArgument(0);
			return wrapper.getParamNameValuePairs().containsValue(42L) ? task : null;
		});
	}

	@Test
	void duplicateTaskAndScheduleIdsCannotMaskAMissingExplicitObject() {
		WorkScheduleEntity schedule = schedule(10L);
		when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));

		var result = provider.query(query(List.of("10", "11"), Map.of(), new ProviderLimits(20, 12000, 3000)));

		assertEquals(ProviderStatus.FORBIDDEN_SCOPE, result.status());
		assertEquals(1, result.permissionFiltered());
		assertTrue(result.fragments().isEmpty());
	}

	@Test
	void providerEnforcesCharacterLimitAndClassification() {
		var result = provider.query(query(List.of(), Map.of("type", "TASK"), new ProviderLimits(20, 5, 3000)));

		assertEquals(1, result.fragments().size());
		assertEquals(5, result.fragments().get(0).excerpt().length());
		assertEquals(DataClassification.SENSITIVE, result.fragments().get(0).dataClassification());
		assertTrue(result.truncated());
	}

	@Test
	void citationRejectsProviderMismatchBeforeDomainLookup() {
		Citation forged = new Citation("work-task:10", ProviderId.CAPSULE, "TASK", "10", "1", "INTERNAL",
				"WorkTaskDetail", Map.of("id", "10"));

		assertEquals(ProviderStatus.FORBIDDEN_SCOPE,
				provider.resolve(new CitationResolveRequest(forged, AUTHORIZED)).status());
		verify(taskMapper, never()).selectOne(any());
	}

	@Override
	protected AssistantContextProvider provider() {
		return provider;
	}

	@Override
	protected ProviderQuery query() {
		return query(List.of(), Map.of("type", "TASK"), new ProviderLimits(20, 12000, 3000));
	}

	@Override
	protected CitationResolveRequest authorizedCitation() {
		return new CitationResolveRequest(citation(), AUTHORIZED);
	}

	@Override
	protected IdentityEnvelope unauthorizedIdentity() {
		return identity("43");
	}

	private ProviderQuery query(List<String> ids, Map<String, String> filters, ProviderLimits limits) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "run-1", AUTHORIZED.correlationId(),
				AUTHORIZED, "work", new ProviderScope(null, null, ids, null, filters), limits, List.of());
	}

	private static IdentityEnvelope identity(String userId) {
		return new IdentityEnvelope("100", userId, "200", List.of("200"), List.of(), List.of(), "work-test",
				"ASSISTANT_CONTEXT");
	}

	private static WorkTaskEntity task(long id) {
		WorkTaskEntity task = new WorkTaskEntity();
		task.setId(id);
		task.setTenantId(100L);
		task.setOwnerId(42L);
		task.setTitle("Authorized task");
		task.setPriority("HIGH");
		task.setStatus(WorkTaskStatus.TODO);
		task.setDueAt(LocalDateTime.of(2026, 8, 4, 10, 0));
		task.setVersion(1);
		task.setUpdateTime(LocalDateTime.of(2026, 8, 3, 9, 0));
		return task;
	}

	private static WorkScheduleEntity schedule(long id) {
		WorkScheduleEntity schedule = new WorkScheduleEntity();
		schedule.setId(id);
		schedule.setTenantId(100L);
		schedule.setTargetUserId(42L);
		schedule.setTitle("Authorized schedule");
		schedule.setStatus(WorkScheduleStatus.CONFIRMED);
		schedule.setStartAt(LocalDateTime.of(2026, 8, 4, 14, 0));
		schedule.setEndAt(LocalDateTime.of(2026, 8, 4, 15, 0));
		schedule.setVersion(1);
		schedule.setUpdateTime(LocalDateTime.of(2026, 8, 3, 9, 0));
		return schedule;
	}

	private static Citation citation() {
		return new Citation("work-task:10", ProviderId.WORK, "TASK", "10", "1", "INTERNAL", "WorkTaskDetail",
				Map.of("id", "10"));
	}

}
