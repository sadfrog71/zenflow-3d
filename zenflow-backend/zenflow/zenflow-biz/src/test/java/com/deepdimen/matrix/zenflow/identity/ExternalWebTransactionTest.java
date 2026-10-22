package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

class ExternalWebTransactionTest {
    @Test void browserTransactionRejectsForeignOriginAndProviderDowngrade() throws Exception {
        var f = new Fixture();
        var body = new ExternalLoginController.Start("google", "a".repeat(43), "a".repeat(48), "LOGIN", "WEB");
        assertThrows(IllegalArgumentException.class, () -> f.controller.challenge(body, request("https://attacker.example")));
        assertThrows(IllegalArgumentException.class, () -> f.controller.challenge(
            new ExternalLoginController.Start("apple", "a".repeat(43), "a".repeat(48), "LOGIN", "WEB"), request("https://product.example")));
        verify(f.values, never()).set(anyString(), anyString(), any(Duration.class));
    }
    @Test void browserModeAndNonceAreBoundToStoredTransactionAndCannotBeReused() throws Exception {
        var f = new Fixture();
        var result = f.controller.challenge(new ExternalLoginController.Start("google", "a".repeat(43), "b".repeat(48), "LOGIN", "WEB"), request("https://product.example"));
        var stored = ArgumentCaptor.forClass(String.class);
        verify(f.values).set(anyString(), stored.capture(), eq(Duration.ofMinutes(5)));
        var tx = new ObjectMapper().readValue(stored.getValue(), ExternalLoginController.Transaction.class);
        assertEquals("WEB", tx.channel()); assertEquals(result.get("nonce"), tx.nonce());
        when(f.values.get(anyString())).thenReturn(stored.getValue());
        var complete = new ExternalLoginController.Complete((String)result.get("transactionId"), "signed-browser-proof", "zh-CN");
        assertThrows(IllegalArgumentException.class, () -> f.controller.complete(complete, request("https://attacker.example")));
        verifyZeroInteractions(f.verifier);
        when(f.verifier.verifyGoogleWeb(eq("signed-browser-proof"), eq(tx.nonce()))).thenReturn(
            new ExternalIdentityVerifier.Identity("google", "https://accounts.google.com", "web-client", "subject", null, false));
        // An unsuccessful atomic consumption must never create an account or product token.
        assertThrows(IllegalArgumentException.class, () -> f.controller.complete(complete, request("https://product.example")));
        verify(f.verifier).verifyGoogleWeb("signed-browser-proof", tx.nonce());
        verify(f.verifier, never()).verify(anyString(), anyString(), anyString());
        verifyZeroInteractions(f.accounts, f.authorizations);
    }
    private MockHttpServletRequest request(String origin) {
        var r = new MockHttpServletRequest(); r.addHeader("X-Requested-With", "Zenflow"); r.addHeader("Origin", origin); return r;
    }
    private static class Fixture {
        final StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") final ValueOperations<String,String> values = mock(ValueOperations.class);
        final ExternalIdentityVerifier verifier = mock(ExternalIdentityVerifier.class);
        final ExternalAccountService accounts = mock(ExternalAccountService.class);
        final OAuth2AuthorizationService authorizations = mock(OAuth2AuthorizationService.class);
        final ExternalLoginController controller;
        Fixture() {
            var p = new ZenflowProperties(); p.setTenantId(1); p.setOauthClientId("zenflow-ios");
            p.setPublicBaseUrl(URI.create("https://product.example")); p.setAuthHmacKey("test-only-key-with-thirty-two-characters");
            var providers = new ZenflowExternalProperties();
            for (String name : new String[]{"google","apple"}) {
                var provider = new ZenflowExternalProperties.Provider(); provider.setEnabled(true); provider.setAudience("web-client"); provider.setClientSecret("test-only"); providers.getProviders().put(name, provider);
            }
            when(redis.opsForValue()).thenReturn(values); when(values.increment(anyString())).thenReturn(1L);
            controller = new ExternalLoginController(p, providers, verifier, accounts, mock(ZenflowUserDetailsService.class), authorizations,
                mock(RegisteredClientRepository.class), redis, new ObjectMapper());
        }
    }
}
