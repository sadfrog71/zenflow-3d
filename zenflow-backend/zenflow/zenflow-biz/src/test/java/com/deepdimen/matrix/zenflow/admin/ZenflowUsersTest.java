package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class ZenflowUsersTest {

	final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	final ZenflowAdminAccess access = mock(ZenflowAdminAccess.class);

	ZenflowUsers service() {
		var product = new ZenflowProperties();
		product.setTenantId(1);
		var manager = mock(PlatformTransactionManager.class);
		when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		return new ZenflowUsers(jdbc, product, access, manager);
	}

	void fixture() {
		var actor = mock(MatrixUser.class);
		when(actor.getId()).thenReturn(7L);
		when(access.require()).thenReturn(actor);
		when(jdbc.queryForList(startsWith("SELECT owner_user_id"), eq(1L), eq(42L)))
			.thenReturn(List.of(Map.of("owner_user_id", 42L)));
		when(jdbc.queryForList(startsWith("SELECT auth_version"), eq(1L), eq(42L)))
			.thenReturn(List.of(Map.of("auth_version", 1L, "lock_flag", "0")));
	}

	@Test
	void disablingUsesOriginalAccountVersionAndRevocationOutbox() {
		fixture();
		var result = service().state("42", Map.of("state", "DISABLED", "expectedVersion", 1));
		assertEquals(2L, result.get("version"));
		verify(jdbc).update(startsWith("UPDATE app_user SET lock_flag"), eq("1"), eq("7"), eq(1L), eq(42L), eq(1L));
		verify(jdbc).update(startsWith("INSERT INTO zf_outbox"), anyLong(), eq(1L), eq("42"), anyString(), eq(2L),
				eq(7L), eq("DISABLED"));
	}

	@Test
	void staleOrMissingUserCannotChangeState() {
		fixture();
		var service = service();
		assertEquals(409,
				assertThrows(ResponseStatusException.class,
						() -> service.state("42", Map.of("state", "DISABLED", "expectedVersion", 2)))
					.getStatusCode()
					.value());
		assertEquals(404,
				assertThrows(ResponseStatusException.class,
						() -> service.state("43", Map.of("state", "DISABLED", "expectedVersion", 1)))
					.getStatusCode()
					.value());
		verify(jdbc, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void noOpDoesNotRevokeAndUnknownFieldsAreRejected() {
		fixture();
		var service = service();
		assertEquals(1L, service.state("42", Map.of("state", "ACTIVE", "expectedVersion", 1)).get("version"));
		assertThrows(ResponseStatusException.class,
				() -> service.state("42", Map.of("state", "DISABLED", "expectedVersion", 1, "tenantId", 2)));
		verify(jdbc, never()).update(anyString(), any(Object[].class));
	}

	@Test void missingAdministratorFailsBeforeAnyDataAccess(){when(access.require()).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));var service=service();assertThrows(ResponseStatusException.class,()->service.list("","ALL",null,25));assertThrows(ResponseStatusException.class,()->service.state("42",Map.of("state","ACTIVE","expectedVersion",1)));verifyNoMoreInteractions(jdbc);}

	@Test void identityProjectionIsPagedAndOmitsSecretFields() {
        when(access.require()).thenReturn(mock(MatrixUser.class));
        var first=new HashMap<String,Object>(Map.of("id",42L,"publicId","public42","nickname","Person","email","person@fixture.invalid","emailLogin",0,"locked","0","version",1L,"createdAt","2026-10-07T00:00:00Z"));
        first.put("password","must-not-leak");
        var second=new HashMap<>(first);second.put("id",41L);var third=new HashMap<>(first);third.put("id",40L);
        doAnswer(invocation->{String sql=invocation.getArgument(0);assertFalse(sql.contains("ai_capsule"));assertFalse(sql.contains("draft_content"));assertFalse(sql.contains("ai_assistant_message"));assertTrue(sql.contains("tenant_id=?"));
            if(sql.startsWith("SELECT user_id,provider"))return List.of(Map.of("user_id",42L,"provider","GOOGLE"));
            return sql.contains("FROM app_user")?List.of(first,second,third):List.of();
        }).when(jdbc).queryForList(anyString(),any(Object.class),any(Object.class),any(Object.class));
        var result=service().list("","ALL",null,2);
        var items=(List<?>)result.get("items");assertEquals(2,items.size());assertEquals(true,result.get("hasMore"));assertEquals("41",result.get("nextCursor"));
        var user=(Map<?,?>)items.get(0);assertEquals(List.of("GOOGLE"),user.get("loginMethods"));assertFalse(user.containsKey("password"));assertEquals("NOT_CONFIGURED",user.get("membershipState"));
    }

}
