package com.deepdimen.matrix.auth.support.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

/** Converts Basic base64(client-id:) into public-client authentication. */
public final class PublicClientAuthenticationConverter implements AuthenticationConverter {

    private final String productClientId;
    public PublicClientAuthenticationConverter() { this(null); }
    public PublicClientAuthenticationConverter(String productClientId) { this.productClientId=productClientId; }

    private static final String TOKEN_ENDPOINT_SUFFIX = "/oauth2/token";

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().endsWith(TOKEN_ENDPOINT_SUFFIX)) {
            return null;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (productClientId != null && (productClientId.equals(request.getParameter("client_id")) || "zenflow-admin".equals(request.getParameter("client_id")))
                && !StringUtils.hasText(authorization) && "refresh_token".equals(request.getParameter("grant_type"))) {
            return new OAuth2ClientAuthenticationToken(request.getParameter("client_id"), ClientAuthenticationMethod.NONE, null,
                    java.util.Map.of("grant_type", "refresh_token"));
        }
        if (!StringUtils.hasText(authorization) || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0 || separator != decoded.length() - 1) return null;
            if ((decoded.substring(0, separator).equals(productClientId) || "zenflow-admin".equals(decoded.substring(0, separator)))) return null;
            return new OAuth2ClientAuthenticationToken(decoded.substring(0, separator),
                    ClientAuthenticationMethod.NONE, null, Collections.emptyMap());
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
