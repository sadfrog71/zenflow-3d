package com.deepdimen.matrix.common.security.service;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/** Product-specific synchronous gate for access, refresh, code and browser authorization lookups. */
public interface MatrixAuthorizationValidator {
    /** Optional product login metadata observer; never receives raw credential strings. */
    default void issued(OAuth2Authorization authorization) {}
    boolean supports(String registeredClientId);
    boolean accepts(OAuth2Authorization authorization, OAuth2TokenType tokenType);
}
