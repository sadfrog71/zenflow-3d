package com.deepdimen.matrix.auth.support.client;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.token.*;
/** Product native client uses PKCE and rotated opaque refresh tokens. */
public final class ZenflowRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
    private final String productClientId;
    private final SecureRandom random=new SecureRandom();
    public ZenflowRefreshTokenGenerator(String productClientId) { this.productClientId=productClientId; }
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        var client=context.getRegisteredClient();
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())
                || !(client.getClientId().equals(productClientId) || "zenflow-admin".equals(client.getClientId()))
                || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                || !client.getClientSettings().isRequireProofKey()
                || client.getTokenSettings().isReuseRefreshTokens()
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)
                || !(AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                    || AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType()))) return null;
        byte[] bytes=new byte[48];random.nextBytes(bytes);Instant now=Instant.now();
        return new OAuth2RefreshToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),now,
                now.plus(client.getTokenSettings().getRefreshTokenTimeToLive()));
    }
}
