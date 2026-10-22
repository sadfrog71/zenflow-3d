package com.deepdimen.matrix.common.security.service;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.admin.api.entity.SysOauthClientDetails;
import com.deepdimen.matrix.admin.api.feign.RemoteClientDetailsService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.MsgUtils;
import com.deepdimen.matrix.common.core.util.RetOps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 查询客户端相关信息实现
 *
 * @author caoyuan
 * @date 2022/5/29
 */
@RequiredArgsConstructor
public class MatrixRemoteRegisteredClientRepository implements RegisteredClientRepository {

    @org.springframework.beans.factory.annotation.Value("${zenflow.product.oauth-client-id:}")
    private String zenflowClientId;

    @org.springframework.beans.factory.annotation.Value("${zenflow.product.public-base-url}/manage/callback")
    private String zenflowAdminCallback;

    private final static String NOTFOUND_CLIENT_ERROR_CODE = "RegisteredClient.notFound";

    /**
     * 刷新令牌有效期默认 30 天
     */
    private final static int refreshTokenValiditySeconds = 60 * 60 * 24 * 30;

    /**
     * 请求令牌有效期默认 12 小时
     */
    private final static int accessTokenValiditySeconds = 60 * 60 * 12;

    private final RemoteClientDetailsService clientDetailsService;

    private final CacheManager cacheManager;

    /**
     * Saves the registered client.
     *
     * <p>
     * IMPORTANT: Sensitive information should be encoded externally from the
     * implementation, e.g. {@link RegisteredClient#getClientSecret()}
     *
     * @param registeredClient the {@link RegisteredClient}
     */
    @Override
    public void save(RegisteredClient registeredClient) {
    }

    /**
     * Returns the registered client identified by the provided {@code id}, or
     * {@code null} if not found.
     *
     * @param id the registration identifier
     * @return the {@link RegisteredClient} if found, otherwise {@code null}
     */
    @Override
    public RegisteredClient findById(String id) {
        return this.findByClientId(id);
    }

    /**
     * Returns the registered client identified by the provided {@code clientId}, or
     * {@code null} if not found.
     * @param clientId the client identifier
     * @return the {@link RegisteredClient} if found, otherwise {@code null}
     */

    /**
     * 按客户端 ID 查找
     *
     * @param clientId 客户端 ID
     * @return {@link RegisteredClient }
     */
    @Override
    @SneakyThrows
    public RegisteredClient findByClientId(String clientId) {

        if (!zenflowClientId.equals(clientId) && !"zenflow-admin".equals(clientId)) {
            return null;
        }

        Cache cache = cacheManager.getCache(CacheConstants.CLIENT_DETAILS_KEY);
        SysOauthClientDetails clientDetails = null;
        if (Objects.nonNull(cache) && Objects.nonNull(cache.get(clientId))) {
            clientDetails = cache.get(clientId, SysOauthClientDetails.class);
        }

        if (Objects.isNull(clientDetails)) {
            SysOauthClientDetails fetchedClientDetails = RetOps
                    .of(clientDetailsService.getClientDetailsById(clientId))
                    .getData()
                    .orElseThrow(() -> new OAuth2AuthorizationCodeRequestAuthenticationException(
                            new OAuth2Error(MsgUtils.getSecurityMessage(NOTFOUND_CLIENT_ERROR_CODE)), null));

            clientDetails = fetchedClientDetails;
            Optional.ofNullable(cache).ifPresent(c -> c.put(clientId, fetchedClientDetails));
        }

        final SysOauthClientDetails finalClientDetails = clientDetails;
        boolean zenflowClient = clientId.equals(zenflowClientId) || "zenflow-admin".equals(clientId);
        String expectedCallback = "zenflow-admin".equals(clientId) ? zenflowAdminCallback : "zenflow://auth/callback";
        if (zenflowClient && (!StrUtil.isBlank(finalClientDetails.getClientSecret())
                || !java.util.Set.of(finalClientDetails.getAuthorizedGrantTypes())
                    .equals(java.util.Set.of("authorization_code", "refresh_token"))
                || !expectedCallback.equals(finalClientDetails.getWebServerRedirectUri()))) {
            throw new IllegalStateException("Zenflow OAuth client must be public, PKCE-only and use its fixed callback");
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(finalClientDetails.getClientId())
                .clientId(finalClientDetails.getClientId());

        if (StrUtil.isBlank(finalClientDetails.getClientSecret())) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        else {
            builder.clientSecret(SecurityConstants.NOOP + finalClientDetails.getClientSecret())
                    .clientAuthenticationMethods(clientAuthenticationMethods -> {
                        clientAuthenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                        clientAuthenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_POST);
                    });
        }

        // 授权模式
        Arrays.stream(finalClientDetails.getAuthorizedGrantTypes())
                .forEach(grant -> builder.authorizationGrantType(new AuthorizationGrantType(grant)));

        // 回调地址
        Optional.ofNullable(finalClientDetails.getWebServerRedirectUri())
                .ifPresent(redirectUri -> Arrays.stream(redirectUri.split(StrUtil.COMMA))
                        .filter(StrUtil::isNotBlank)
                        .forEach(builder::redirectUri));

        // scope
        Optional.ofNullable(finalClientDetails.getScope())
                .ifPresent(scope -> Arrays.stream(scope.split(StrUtil.COMMA))
                        .filter(StrUtil::isNotBlank)
                        .forEach(builder::scope));

        // 注入扩展配置
        Optional.ofNullable(finalClientDetails.getAdditionalInformation()).ifPresent(ext -> {
            Map<String, Object> map = JSONUtil.parseObj(ext).toBean(Map.class);
            builder.clientSettings(ClientSettings.withSettings(map)
                    .requireProofKey(zenflowClient)
                    .requireAuthorizationConsent(!BooleanUtil.toBoolean(finalClientDetails.getAutoapprove()))
                    .build());
        });

        if (zenflowClient && StrUtil.isBlank(finalClientDetails.getAdditionalInformation())) {
            builder.clientSettings(ClientSettings.builder().requireProofKey(true)
                    .requireAuthorizationConsent(false).build());
        }

        return builder
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .accessTokenTimeToLive(Duration.ofSeconds(
                                Optional.ofNullable(finalClientDetails.getAccessTokenValidity()).orElse(accessTokenValiditySeconds)))
                        .refreshTokenTimeToLive(Duration.ofSeconds(Optional.ofNullable(finalClientDetails.getRefreshTokenValidity())
                                .orElse(refreshTokenValiditySeconds)))
                        .build())
                .build();
    }

}
