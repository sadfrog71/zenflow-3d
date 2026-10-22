package com.deepdimen.matrix.admin.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.admin.api.entity.SysMessageEntity;
import com.deepdimen.matrix.admin.api.entity.SysMessageRelationEntity;
import com.deepdimen.matrix.admin.api.vo.SysMessageVO;
import com.deepdimen.matrix.admin.mapper.SysMessageMapper;
import com.deepdimen.matrix.admin.mapper.SysMessageRelationMapper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class SysMessageUserAccessTest {

	private final SysMessageMapper mapper = mock(SysMessageMapper.class);

	private final SysMessageRelationMapper relations = mock(SysMessageRelationMapper.class);

	private SysMessageServiceImpl service;

	@BeforeEach
	void setup() {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "user-message-test");
		TableInfoHelper.initTableInfo(assistant, SysMessageEntity.class);
		TableInfoHelper.initTableInfo(assistant, SysMessageRelationEntity.class);
		service = new SysMessageServiceImpl(null, null, relations, null, null, null);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		TenantContextHolder.setTenantId(7L);
		MatrixUser user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(8L);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	@Test
	void detailQueryEnforcesRecipientBothTenantsSentAndExpiry() {
		service.getUserMessage(9L);
		ArgumentCaptor<MPJLambdaWrapper> captor = ArgumentCaptor.forClass(MPJLambdaWrapper.class);
		verify(mapper).selectJoinOne(eq(SysMessageVO.class), captor.capture());
		MPJLambdaWrapper wrapper = captor.getValue();
		String sql = wrapper.getSqlSegment();
		assertTrue(sql.contains("tenant_id"));
		assertTrue(sql.contains("user_id"));
		assertTrue(sql.contains("send_flag"));
		assertTrue(sql.contains("expire_time"));
		assertEquals(2, wrapper.getParamNameValuePairs().values().stream().filter(Long.valueOf(7L)::equals).count());
		assertTrue(wrapper.getParamNameValuePairs().containsValue(8L));
		assertTrue(wrapper.getParamNameValuePairs().containsValue(9L));
	}

	@Test
	void missingTenantDoesNotIssueUnscopedQuery() {
		TenantContextHolder.clear();
		assertNull(service.getUserMessage(9L));
		verifyZeroInteractions(mapper);
	}

	@Test
	void inaccessibleMessageCannotBeMarkedRead() {
		assertFalse(service.readMessage(9L));
		verifyZeroInteractions(relations);
	}

}
