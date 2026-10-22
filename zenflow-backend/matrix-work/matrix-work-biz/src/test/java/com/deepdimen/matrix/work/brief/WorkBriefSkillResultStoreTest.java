package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkBriefRunEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefSkillResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class WorkBriefSkillResultStoreTest {

	private final WorkBriefSkillResultMapper mapper = mock(WorkBriefSkillResultMapper.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private final WorkBriefRunEntity run = new WorkBriefRunEntity();

	private final Identity identity = new Identity("42", "3", "3", "run");

	private final Consumer consumer = new Consumer("WORK_BRIEF", "7", "WORK_BRIEF", "default");

	private final WorkBriefSkillResultStore store = new WorkBriefSkillResultStore(mapper, users, new ObjectMapper());

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		when(users.isOrganizationMember(42L, 3L, 5L)).thenReturn(true);
		run.setOrgId(5L);
		run.setStatus("RUNNING");
		when(mapper.findOwned(42L, 3L, 7L, 8L)).thenReturn(run);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test void saveProducesBusinessReferenceWithoutApprovingReport(){
        when(mapper.saveOnce(42L,3L,7L,8L,9L,"{\"summary\":\"raw\"}")).thenReturn(1);
        assertEquals("work-brief-run:8:skill:9",store.save(identity,consumer,"8","9","{\"summary\":\"raw\"}"));
        assertEquals("RUNNING",run.getStatus());
        assertNull(run.getReviewerStatus());
    }

	@Test
	void replayChecksRunAssociationAndCurrentOrganizationMembership() {
		run.setSkillRunId(9L);
		run.setSkillOutputJson("{\"summary\":\"raw\"}");
		assertEquals(run.getSkillOutputJson(), store.read(identity, consumer, "8", "9", "work-brief-run:8:skill:9"));
		assertThrows(IllegalArgumentException.class,
				() -> store.read(identity, consumer, "8", "10", "work-brief-run:8:skill:10"));
		when(users.isOrganizationMember(42L, 3L, 5L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class,
				() -> store.read(identity, consumer, "8", "9", "work-brief-run:8:skill:9"));
	}

	@Test
	void conflictingSavedResultCannotBeOverwritten() {
		run.setSkillRunId(9L);
		run.setSkillOutputJson("{\"summary\":\"original\"}");
		assertThrows(IllegalStateException.class,
				() -> store.save(identity, consumer, "8", "9", "{\"summary\":\"changed\"}"));
		assertEquals("{\"summary\":\"original\"}", run.getSkillOutputJson());
		assertThrows(IllegalArgumentException.class,
				() -> store.save(new Identity("42", "3", "4", "run"), consumer, "8", "9", "{}"));
	}

	@Test
	void executionInputMustMatchPersistedPermissionFilteredSnapshot() {
		run.setInputSnapshot("{\"tasks\":[],\"count\":0}");
		assertEquals("{\"tasks\":[],\"count\":0}",
				store.authorizeInput(identity, consumer, "8", "{\"count\":0,\"tasks\":[]}"));
		assertThrows(IllegalArgumentException.class,
				() -> store.authorizeInput(identity, consumer, "8", "{\"tasks\":[],\"count\":9}"));
		assertThrows(IllegalArgumentException.class,
				() -> store.authorizeInput(identity, consumer, "8", "{\"tasks\":[],\"count\":0}{}"));
	}

}
