package com.deepdimen.matrix.zenflow.identity;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="zenflow.external")
public class ZenflowExternalProperties {
    public static class Provider {
        private boolean enabled;
        private String audience;
        private String clientSecret;
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String value) { clientSecret=value; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled=enabled; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience=audience; }
    }
    private Map<String,Provider> providers = new LinkedHashMap<>();
    public Map<String,Provider> getProviders() { return providers; }
    public void setProviders(Map<String,Provider> providers) { this.providers=providers; }
    public Provider require(String name) {
        if (!List.of("google","facebook","apple").contains(name)) throw new IllegalArgumentException("INVALID_PROVIDER");
        Provider p=providers.get(name);
        if (p==null || !p.isEnabled() || p.getAudience()==null || p.getAudience().isBlank() || ("google".equals(name) && (p.getClientSecret()==null || p.getClientSecret().isBlank())))
            throw new IllegalStateException("PROVIDER_NOT_CONFIGURED");
        return p;
    }
    public List<String> enabled() {
        return List.of("google","facebook","apple").stream().filter(n -> {
            try { require(n); return true; } catch (RuntimeException ex) { return false; }
        }).toList();
    }
}
