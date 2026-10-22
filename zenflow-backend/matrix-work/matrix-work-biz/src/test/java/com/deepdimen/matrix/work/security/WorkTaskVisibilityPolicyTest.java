package com.deepdimen.matrix.work.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class WorkTaskVisibilityPolicyTest {

	final TenantUserDirectory directory = mock(TenantUserDirectory.class);

	final CurrentOrganizationResolver org = mock(CurrentOrganizationResolver.class);

	final WorkTaskVisibilityPolicy policy = new WorkTaskVisibilityPolicy(directory, org);

	@BeforeEach
	void setup() {
		if (TableInfoHelper.getTableInfo(WorkTaskEntity.class) == null)
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "visibility"),
					WorkTaskEntity.class);
		TenantContextHolder.setTenantId(1L);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void missingAuthenticationIsNeverTreatedAsSystemPermission() {
		assertFalse(WorkTaskVisibilityPolicy.hasPermission("work_task_view"));
		assertThrows(ResponseStatusException.class, policy::actor);
	}

	@Test
	void inactiveTenantMembershipDeniedEvenValidLogin() {
		var user = new MatrixUser(10L, "u", List.of(), 100L, List.of(100L), List.of(), null, null, "u", "u", null, 1L,
				"password", true, true, "TOB", true, null, true, List.of());
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
		assertThrows(ResponseStatusException.class, policy::actor);
		verify(org, never()).resolve();
	}

	@Test
	void ordinaryScopeIsTenantBoundIncludesPendingAndParentButNotOrgEquality() {
		var actor = new WorkTaskVisibilityPolicy.Actor(1L, 10L, 100L,
				new TenantUserDirectory.TaskAuthority(10L, 3, false, List.of()));
		String sql = policy.query(actor).getSqlSegment();
		assertTrue(sql.contains("tenant_id"));
		assertTrue(sql.contains("pending_transfer_id"));
		assertTrue(sql.contains("parent_id"));
		assertFalse(sql.contains("organization_id"));
		assertFalse(sql.contains("p.parent_id"));
	}

	@Test void managerScopeUsesActualMembersNotTaskOriginDepartment() {
        when(directory.listOrganizationUserIds(1L,100L)).thenReturn(List.of(20L,30L));
        var actor=new WorkTaskVisibilityPolicy.Actor(1L,10L,100L,new TenantUserDirectory.TaskAuthority(10L,2,false,List.of(100L)));
        String sql=policy.query(actor).getSqlSegment();assertTrue(sql.contains("creator_id IN") || sql.contains("creatorId IN"),sql);assertTrue(sql.contains("primary_assignee_id IN") || sql.contains("primaryAssigneeId IN"),sql);assertFalse(sql.contains("organization_id"));
    }

	@Test
	void generalManagerOnlyRemovesRelationshipPredicateNotTenantBoundary() {
		var actor = new WorkTaskVisibilityPolicy.Actor(1L, 10L, 100L,
				new TenantUserDirectory.TaskAuthority(10L, 1, true, List.of()));
		String sql = policy.query(actor).getSqlSegment();
		assertTrue(sql.contains("tenant_id") || sql.contains("tenantId"), sql);
		assertFalse(sql.contains("creator_id") || sql.contains("creatorId"));
	}

}
