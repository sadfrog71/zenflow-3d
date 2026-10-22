package com.deepdimen.matrix.auth.support.base;

import com.deepdimen.matrix.common.core.constant.CommonConstants;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuth2ResourceOwnerRefreshTokenPolicyTest {

    @Test
    void allowsRefreshTokensForAnExplicitlyOptedInPublicClient() {
        RegisteredClient client = client(ClientAuthenticationMethod.NONE, true, true);

        assertTrue(OAuth2ResourceOwnerBaseAuthenticationProvider.shouldIssueRefreshToken(
                client, authenticated(client, ClientAuthenticationMethod.NONE)));
    }

    @Test
    void keepsUnmarkedPublicClientsAccessTokenOnly() {
        RegisteredClient client = client(ClientAuthenticationMethod.NONE, true, false);

        assertFalse(OAuth2ResourceOwnerBaseAuthenticationProvider.shouldIssueRefreshToken(
                client, authenticated(client, ClientAuthenticationMethod.NONE)));
    }

    @Test
    void preservesRefreshTokensForConfidentialClients() {
        RegisteredClient client = client(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, true, false);

        assertTrue(OAuth2ResourceOwnerBaseAuthenticationProvider.shouldIssueRefreshToken(
                client, authenticated(client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC)));
    }

    @Test
    void requiresTheRefreshTokenGrantForEveryClient() {
        RegisteredClient client = client(ClientAuthenticationMethod.NONE, false, true);

        assertFalse(OAuth2ResourceOwnerBaseAuthenticationProvider.shouldIssueRefreshToken(
                client, authenticated(client, ClientAuthenticationMethod.NONE)));
    }

    private static RegisteredClient client(
            ClientAuthenticationMethod method,
            boolean refreshGrant,
            boolean allowPublicRefreshToken) {
        RegisteredClient.Builder builder = RegisteredClient.withId("client")
                .clientId("client")
                .clientAuthenticationMethod(method)
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .clientSettings(ClientSettings.builder()
                        .setting(CommonConstants.ALLOW_PUBLIC_REFRESH_TOKEN,
                                allowPublicRefreshToken ? "1" : "0")
                        .build());
        if (refreshGrant) {
            builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }
        return builder.build();
    }

    private static OAuth2ClientAuthenticationToken authenticated(
            RegisteredClient client,
            ClientAuthenticationMethod method) {
        return new OAuth2ClientAuthenticationToken(client, method, null);
    }
}
