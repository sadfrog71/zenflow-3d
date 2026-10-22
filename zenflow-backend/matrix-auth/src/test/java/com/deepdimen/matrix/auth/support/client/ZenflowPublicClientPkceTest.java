package com.deepdimen.matrix.auth.support.client;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
class ZenflowPublicClientPkceTest {
    @Test void productCodeMustReachStandardPkceProvider() {
        var provider=new PublicClientAuthenticationProvider(mock(RegisteredClientRepository.class),"zenflow-ios");
        assertNull(provider.authenticate(new OAuth2ClientAuthenticationToken("zenflow-ios",ClientAuthenticationMethod.NONE,null,Map.of("code_verifier","wrong"))));
        var request=new MockHttpServletRequest("POST","/admin/oauth2/token");
        request.setParameter("grant_type","authorization_code");request.setParameter("client_id","zenflow-ios");
        request.addHeader("Authorization","Basic emVuZmxvdy1pb3M6");
        assertNull(new PublicClientAuthenticationConverter("zenflow-ios").convert(request));
    }
    @Test void onlyRefreshUsesProductPublicClientAdapter() {
        var request=new MockHttpServletRequest("POST","/admin/oauth2/token");
        request.setParameter("client_id","zenflow-ios");request.setParameter("grant_type","refresh_token");
        var token=(OAuth2ClientAuthenticationToken)new PublicClientAuthenticationConverter("zenflow-ios").convert(request);
        assertEquals("refresh_token",token.getAdditionalParameters().get("grant_type"));
    }
    @Test void administratorUsesTheSameStandardPkceBoundaryAndRotatedRefreshAdapter() {
        var provider=new PublicClientAuthenticationProvider(mock(RegisteredClientRepository.class),"zenflow-ios");
        assertNull(provider.authenticate(new OAuth2ClientAuthenticationToken("zenflow-admin",ClientAuthenticationMethod.NONE,null,Map.of("code_verifier","wrong"))));
        var request=new MockHttpServletRequest("POST","/admin/oauth2/token");
        request.setParameter("grant_type","authorization_code");request.setParameter("client_id","zenflow-admin");
        request.addHeader("Authorization","Basic " + java.util.Base64.getEncoder().encodeToString("zenflow-admin:".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNull(new PublicClientAuthenticationConverter("zenflow-ios").convert(request));
        var refresh=new MockHttpServletRequest("POST","/admin/oauth2/token");
        refresh.setParameter("grant_type","refresh_token");refresh.setParameter("client_id","zenflow-admin");
        var token=(OAuth2ClientAuthenticationToken)new PublicClientAuthenticationConverter("zenflow-ios").convert(refresh);
        assertEquals("zenflow-admin",token.getPrincipal());
        assertEquals("refresh_token",token.getAdditionalParameters().get("grant_type"));
    }

}
