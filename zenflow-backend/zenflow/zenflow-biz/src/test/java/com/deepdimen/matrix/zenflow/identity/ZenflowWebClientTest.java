package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ZenflowWebClientTest {
    @Test void consumerUsesOnlyFixedNativeAndProductWebCallbacks() {
        var properties = properties();
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(1L), eq("zenflow-ios"))).thenReturn(List.of(row("zenflow", "zenflow://auth/callback")));
        var client = new ZenflowClientRepository(jdbc, properties).findByClientId("zenflow-ios");
        assertEquals(Set.of("zenflow://auth/callback", "https://product.example/auth/callback"), client.getRedirectUris());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertFalse(client.getTokenSettings().isReuseRefreshTokens());
    }
    @Test void administratorDoesNotGainConsumerCallback() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(1L), eq("zenflow-admin"))).thenReturn(List.of(row("zenflow-admin", "https://product.example/manage/callback")));
        var repository = new ZenflowClientRepository(jdbc, properties());
        assertEquals(Set.of("https://product.example/manage/callback"), repository.findByClientId("zenflow-admin").getRedirectUris());
        assertNull(repository.findByClientId("untrusted-web-client"));
    }
    @Test void databaseCannotInjectAnAdditionalCallback() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(1L), eq("zenflow-ios"))).thenReturn(List.of(row("zenflow", "zenflow://auth/callback,https://attacker.example")));
        assertThrows(IllegalStateException.class, () -> new ZenflowClientRepository(jdbc, properties()).findByClientId("zenflow-ios"));
    }
    private ZenflowProperties properties() {
        var p = new ZenflowProperties(); p.setTenantId(1); p.setOauthClientId("zenflow-ios"); p.setPublicBaseUrl(URI.create("https://product.example")); return p;
    }
    private Map<String,Object> row(String scope, String callback) {
        return Map.of("client_secret", "", "scope", scope, "authorized_grant_types", "authorization_code,refresh_token", "web_server_redirect_uri", callback, "access_token_validity", 3600, "refresh_token_validity", 2592000);
    }
}
