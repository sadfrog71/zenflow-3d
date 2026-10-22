package com.deepdimen.matrix.zenflow.identity;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zenflow.product")
public class ZenflowProperties {

    private long tenantId;
    private String oauthClientId;
    private String emailFrom;
    private String authHmacKey;
    private URI publicBaseUrl;
    private URI oauthIssuer;

    public void validate() {
        if (tenantId <= 0 || blank(oauthClientId) || blank(authHmacKey)
                || authHmacKey.getBytes(StandardCharsets.UTF_8).length < 32 || publicBaseUrl == null
                || !("https".equals(publicBaseUrl.getScheme())
                     || ("http".equals(publicBaseUrl.getScheme())
                         && "127.0.0.1".equals(publicBaseUrl.getHost())))) {
            throw new IllegalStateException("Zenflow product identity and HTTPS configuration are required");
        }
        if (oauthIssuer == null || !publicBaseUrl.getScheme().equals(oauthIssuer.getScheme())
                || !publicBaseUrl.getAuthority().equals(oauthIssuer.getAuthority())
                || oauthIssuer.getPath() == null || oauthIssuer.getPath().isBlank()) {
            throw new IllegalStateException("Zenflow OAuth issuer must use the product origin and a fixed path");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public long getTenantId() { return tenantId; }
    public void setTenantId(long value) { tenantId = value; }
    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String value) { oauthClientId = value; }
    public String getEmailFrom() { return emailFrom; }
    public void setEmailFrom(String value) { emailFrom = value; }
    public String getAuthHmacKey() { return authHmacKey; }
    public void setAuthHmacKey(String value) { authHmacKey = value; }
    public URI getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(URI value) { publicBaseUrl = value; }
    public URI getOauthIssuer() { return oauthIssuer; }
    public void setOauthIssuer(URI value) { oauthIssuer = value; }
}
