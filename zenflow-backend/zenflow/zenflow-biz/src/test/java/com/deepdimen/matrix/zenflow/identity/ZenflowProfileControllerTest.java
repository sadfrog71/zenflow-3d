package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ZenflowProfileControllerTest {
    @AfterEach void clearPrincipal() { SecurityContextHolder.clearContext(); }

    @Test void verifiedProviderEmailIsDisplayOnlyAndQueryIsOwnerScoped() {
        var row = row(); row.put("identity_email", "quiet.space@example.invalid");
        var jdbc = mock(JdbcTemplate.class);
        var result = controller(jdbc, row).profile();
        assertEquals("quiet.space", result.get("displayName"));
        assertEquals("quiet.space@example.invalid", result.get("accountEmail"));
        assertEquals("", result.get("email"), "provider email must not become an email login identity");
        verify(jdbc).queryForList(argThat(sql -> sql.contains("e.tenant_id=p.tenant_id")
            && sql.contains("e.user_id=p.owner_user_id") && sql.contains("e.provider_email_verified=1")
            && sql.contains("p.tenant_id=? AND p.owner_user_id=?")), eq(1L), eq(42L));
    }

    @Test void nicknameWinsAndMissingIdentityDoesNotExposePublicId() {
        var row = row(); row.put("nickname", "  Calm Lake  ");
        assertEquals("Calm Lake", controller(mock(JdbcTemplate.class), row).profile().get("displayName"));
        row.put("nickname", null);
        assertEquals("", controller(mock(JdbcTemplate.class), row).profile().get("displayName"));
    }

    @Test void rejectsNonConsumerPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("administrator", null, List.of()));
        var controller = new ZenflowProfileController(mock(JdbcTemplate.class), new ZenflowProperties(),
            mock(ZenflowUserDetailsService.class), new ZenflowExternalProperties());
        assertThrows(IllegalStateException.class, controller::profile);
    }

    @Test void notesAndSceneStayIndependentOfModelReadiness() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("model unavailable"));
        var result = controller(jdbc, row()).bootstrap();
        var capabilities = (Map<?,?>) result.get("capabilities");
        assertEquals(true, capabilities.get("notes"));
        assertEquals(true, capabilities.get("scene"));
        assertEquals(false, capabilities.get("chat"));
        assertEquals(false, capabilities.get("social"));
        assertEquals(false, capabilities.get("voiceMedia"));
    }

    private ZenflowProfileController controller(JdbcTemplate jdbc, Map<String,Object> row) {
        var user = mock(MatrixUser.class); when(user.getId()).thenReturn(42L);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        when(jdbc.queryForList(anyString(), eq(1L), eq(42L))).thenReturn(List.of(row));
        var properties = new ZenflowProperties(); properties.setTenantId(1);
        return new ZenflowProfileController(jdbc, properties, mock(ZenflowUserDetailsService.class), new ZenflowExternalProperties());
    }
    private Map<String,Object> row() {
        var row = new HashMap<String,Object>(); row.put("public_id", "opaque-private-id");
        row.put("locale", "en-US"); row.put("memory_enabled", 0); row.put("version", 1L);
        return row;
    }
}
