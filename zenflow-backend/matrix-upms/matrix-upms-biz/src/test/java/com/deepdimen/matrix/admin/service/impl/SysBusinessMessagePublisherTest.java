package com.deepdimen.matrix.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageRequest;
import com.deepdimen.matrix.admin.api.entity.SysMessageEntity;
import com.deepdimen.matrix.admin.api.entity.SysMessageRelationEntity;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.mapper.SysMessageMapper;
import com.deepdimen.matrix.admin.mapper.SysMessageRelationMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SysBusinessMessagePublisherTest {

	@Mock
	private SysMessageMapper messageMapper;

	@Mock
	private SysMessageRelationMapper relationMapper;

	@Mock
	private SysUserMapper userMapper;

	@Mock
	private TenantUserDirectory tenantUserDirectory;

	@InjectMocks
	private SysBusinessMessagePublisher publisher;

	@BeforeAll
	static void initializeTableInfo() {
		MybatisConfiguration configuration = new MybatisConfiguration();
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "business-message-test");
		if (TableInfoHelper.getTableInfo(SysMessageEntity.class) == null) {
			TableInfoHelper.initTableInfo(assistant, SysMessageEntity.class);
		}
		if (TableInfoHelper.getTableInfo(SysUser.class) == null) {
			TableInfoHelper.initTableInfo(assistant, SysUser.class);
		}
	}

	@BeforeEach
	void setTenant() {
		TenantContextHolder.setTenantId(1L);
	}

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void returnsExistingMessageForDuplicateBusinessKey() {
		SysMessageEntity existing = new SysMessageEntity();
		existing.setId(88L);
		when(messageMapper.selectOne(any())).thenReturn(existing);

		Long messageId = publisher.publish(request("TASK:1:ASSIGNED", List.of(2L)));

		assertEquals(88L, messageId);
		verify(messageMapper, never()).insert(any(SysMessageEntity.class));
		verify(relationMapper, never()).insert(any(SysMessageRelationEntity.class));
	}

	@Test
	void publishesOneRelationPerValidatedRecipient() {
		when(messageMapper.selectOne(any())).thenReturn(null);
		when(tenantUserDirectory.listAssignableUserIds(1L, List.of(2L, 3L))).thenReturn(List.of(2L, 3L));
		when(messageMapper.insert(any(SysMessageEntity.class))).thenAnswer(invocation -> {
			SysMessageEntity message = invocation.getArgument(0);
			message.setId(99L);
			return 1;
		});

		Long messageId = publisher.publish(request("TASK:1:ASSIGNED", List.of(2L, 3L, 3L)));

		assertEquals(99L, messageId);
		ArgumentCaptor<SysMessageEntity> messageCaptor = ArgumentCaptor.forClass(SysMessageEntity.class);
		verify(messageMapper).insert(messageCaptor.capture());
		assertEquals("TASK", messageCaptor.getValue().getSourceType());
		assertEquals("TASK_ASSIGNED", messageCaptor.getValue().getEventType());
		assertEquals("/todo", messageCaptor.getValue().getTargetPath());
		assertEquals(1, messageCaptor.getValue().getActionRequired());
		verify(relationMapper, times(2)).insert(any(SysMessageRelationEntity.class));
	}

	@Test
	void rejectsExternalTargetPaths() {
		BusinessMessageRequest request = request("TASK:2:ASSIGNED", List.of(2L));
		request.setTargetPath("https://example.com");
		when(messageMapper.selectOne(any())).thenReturn(null);
		when(tenantUserDirectory.listAssignableUserIds(1L, List.of(2L))).thenReturn(List.of(2L));

		assertThrows(IllegalArgumentException.class, () -> publisher.publish(request));
	}

	@Test
	void validatesRecipientsAgainstTheMessageOrganizationInOneDirectoryCall() {
		BusinessMessageRequest request = request("TASK:3:ASSIGNED", List.of(2L, 3L));
		request.setOrgId(10L);
		when(messageMapper.selectOne(any())).thenReturn(null);
		when(tenantUserDirectory.listOrganizationUserIds(1L, 10L)).thenReturn(List.of(2L, 3L, 4L));
		when(messageMapper.insert(any(SysMessageEntity.class))).thenAnswer(invocation -> {
			SysMessageEntity message = invocation.getArgument(0);
			message.setId(100L);
			return 1;
		});

		assertEquals(100L, publisher.publish(request));
		verify(tenantUserDirectory).listOrganizationUserIds(1L, 10L);
		verify(tenantUserDirectory, never()).listAssignableUserIds(any(), any());
	}

	@Test
	void rejectsMoreThanOneHundredExplicitRecipients() {
		List<Long> recipients = LongStream.rangeClosed(1, 101).boxed().toList();
		when(messageMapper.selectOne(any())).thenReturn(null);

		assertThrows(IllegalArgumentException.class,
				() -> publisher.publish(request("TASK:4:ASSIGNED", recipients)));
		verify(tenantUserDirectory, never()).listAssignableUserIds(any(), any());
		verify(messageMapper, never()).insert(any(SysMessageEntity.class));
	}

	private BusinessMessageRequest request(String key, List<Long> recipients) {
		return BusinessMessageRequest.builder()
			.bizKey(key)
			.sourceType("TASK")
			.sourceId("1")
			.eventType("TASK_ASSIGNED")
			.title("收到一项新任务")
			.content("测试任务")
			.priority("P1")
			.actionRequired(true)
			.targetPath("/todo")
			.targetQuery(Map.of("focus", "tasks"))
			.recipientUserIds(recipients)
			.build();
	}

}
