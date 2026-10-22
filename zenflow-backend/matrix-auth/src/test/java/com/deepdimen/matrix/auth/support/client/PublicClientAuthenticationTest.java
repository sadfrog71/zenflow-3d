package com.deepdimen.matrix.auth.support.client;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicClientAuthenticationTest {

    @Test
    void convertsOnlyEmptySecretBasicAuthenticationOnTokenEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addHeader("Authorization", basic("matrix-mobile:"));

        Authentication converted = new PublicClientAuthenticationConverter().convert(request);

        assertInstanceOf(OAuth2ClientAuthenticationToken.class, converted);
        assertEquals("matrix-mobile", converted.getPrincipal());
        assertEquals(ClientAuthenticationMethod.NONE,
                ((OAuth2ClientAuthenticationToken) converted).getClientAuthenticationMethod());
    }

    @Test
    void leavesConfidentialClientsToStandardConverter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addHeader("Authorization", basic("matrix:secret"));
        assertNull(new PublicClientAuthenticationConverter().convert(request));
    }

    @Test
    void authenticatesRegisteredPublicClientAndRejectsConfidentialClient() {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        RegisteredClient publicClient = RegisteredClient.withId("matrix-mobile")
                .clientId("matrix-mobile")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .build();
        RegisteredClient confidentialClient = RegisteredClient.withId("matrix")
                .clientId("matrix")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .build();
        when(repository.findByClientId("matrix-mobile")).thenReturn(publicClient);
        when(repository.findByClientId("matrix")).thenReturn(confidentialClient);
        PublicClientAuthenticationProvider provider = new PublicClientAuthenticationProvider(repository);

        Authentication authenticated = provider.authenticate(unauthenticated("matrix-mobile"));

        assertTrue(authenticated.isAuthenticated());
        assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(unauthenticated("matrix")));
        assertNull(provider.authenticate(new OAuth2ClientAuthenticationToken(
                "matrix", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret", Map.of())));
    }

    private static OAuth2ClientAuthenticationToken unauthenticated(String clientId) {
        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, Map.of());
    }

    private static String basic(String value) {
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
