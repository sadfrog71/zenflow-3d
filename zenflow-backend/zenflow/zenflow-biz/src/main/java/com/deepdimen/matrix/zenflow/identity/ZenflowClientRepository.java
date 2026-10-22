package com.deepdimen.matrix.zenflow.identity;

import java.time.Duration;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;

/** Product-only public client. Does not expose inherited client management HTTP routes. */
@Component
@Primary
public class ZenflowClientRepository implements RegisteredClientRepository {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    public ZenflowClientRepository(JdbcTemplate jdbc,ZenflowProperties product) { this.jdbc=jdbc;this.product=product; }
    public void save(RegisteredClient client) { throw new UnsupportedOperationException("Managed by product migration"); }
    public RegisteredClient findById(String id) { return findByClientId(id); }
    public RegisteredClient findByClientId(String id) {
        boolean administrator="zenflow-admin".equals(id);
        if(!product.getOauthClientId().equals(id) && !administrator) return null;
        String callback=administrator?product.getPublicBaseUrl().toString().replaceAll("/$","")+"/manage/callback":"zenflow://auth/callback";
        String scope=administrator?"zenflow-admin":"zenflow";
        var rows=jdbc.queryForList("SELECT client_id,client_secret,scope,authorized_grant_types,web_server_redirect_uri,access_token_validity,refresh_token_validity FROM sys_oauth_client_details WHERE tenant_id=? AND client_id=? AND del_flag='0'",product.getTenantId(),id);
        if(rows.size()!=1) return null;
        var row=rows.get(0);
        if(row.get("client_secret")!=null && !row.get("client_secret").toString().isBlank()
            || !Set.of("authorization_code","refresh_token").equals(Set.of(row.get("authorized_grant_types").toString().split(",")))
            || !callback.equals(row.get("web_server_redirect_uri")) || !scope.equals(row.get("scope")))
            throw new IllegalStateException("INVALID_PRODUCT_CLIENT");
        var builder=RegisteredClient.withId(id).clientId(id).clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri(callback).scope(scope)
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).reuseRefreshTokens(false)
                .accessTokenTimeToLive(Duration.ofSeconds(((Number)row.get("access_token_validity")).longValue()))
                .refreshTokenTimeToLive(Duration.ofSeconds(((Number)row.get("refresh_token_validity")).longValue())).build());
        if(!administrator) builder.redirectUri(product.getPublicBaseUrl().toString().replaceAll("/$","")+"/auth/callback");
        return builder.build();
    }
}
