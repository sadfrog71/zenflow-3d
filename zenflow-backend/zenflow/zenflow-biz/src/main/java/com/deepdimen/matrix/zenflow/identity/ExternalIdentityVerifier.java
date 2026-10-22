package com.deepdimen.matrix.zenflow.identity;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalIdentityVerifier {
    public record Identity(String provider,String issuer,String scope,String subject,String email,boolean emailVerified,String displayName) {public Identity(String provider,String issuer,String scope,String subject,String email,boolean emailVerified){this(provider,issuer,scope,subject,email,emailVerified,"");}}
    private static final Map<String,String> KEYS=Map.of(
        "google","https://www.googleapis.com/oauth2/v3/certs",
        "facebook","https://www.facebook.com/.well-known/oauth/openid/jwks/",
        "apple","https://appleid.apple.com/auth/keys");
    private final ZenflowExternalProperties properties;
    private final Map<String,NimbusJwtDecoder> decoders=new ConcurrentHashMap<>();
    public ExternalIdentityVerifier(ZenflowExternalProperties properties) { this.properties=properties; }
    public Identity verify(String provider,String token,String nonce) {
        var p=properties.require(provider);
        if (token==null || token.length()>16384 || nonce==null) throw new IllegalArgumentException("INVALID_PROVIDER_TOKEN");
        if ("google".equals(provider)) {
            // Original Google iOS SDK has no nonce parameter. Only accept its single-use server code,
            // exchanged here; never accept a reusable client-supplied Google ID token.
            var body=new org.springframework.util.LinkedMultiValueMap<String,String>();
            body.add("code",token); body.add("client_id",p.getAudience());
            body.add("client_secret",p.getClientSecret()); body.add("grant_type","authorization_code");
            body.add("redirect_uri","");
            try {
                var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(5000); factory.setReadTimeout(5000);
                Map<?,?> result=org.springframework.web.client.RestClient.builder().requestFactory(factory).build().post()
                    .uri("https://oauth2.googleapis.com/token").contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body).retrieve().body(Map.class);
                token=result==null ? null : (String)result.get("id_token");
                if (token==null) throw new IllegalArgumentException();
            } catch (RuntimeException ex) { throw new IllegalArgumentException("INVALID_PROVIDER_TOKEN"); }
            nonce=null; // Fresh, audience-bound server exchange provides Google's replay protection.
        }
        Jwt jwt;
        try { jwt=decoders.computeIfAbsent(provider,n->NimbusJwtDecoder.withJwkSetUri(KEYS.get(n)).build()).decode(token); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("INVALID_PROVIDER_TOKEN"); }
        return claims(provider,p.getAudience(),jwt,nonce);
    }
    /** Browser credentials must carry the nonce from a single-use server transaction. */
    public Identity verifyGoogleWeb(String token,String nonce) {
        var p=properties.require("google");
        if(token==null || token.isBlank() || token.length()>16384 || nonce==null || nonce.isBlank())
            throw new IllegalArgumentException("INVALID_PROVIDER_TOKEN");
        Jwt jwt;
        try { jwt=decoders.computeIfAbsent("google",n->NimbusJwtDecoder.withJwkSetUri(KEYS.get(n)).build()).decode(token); }
        catch(RuntimeException ex) { throw new IllegalArgumentException("INVALID_PROVIDER_TOKEN"); }
        return claims("google",p.getAudience(),jwt,nonce);
    }
    private static String displayName(Jwt jwt){String value=jwt.getClaimAsString("name");if(value==null)return "";value=value.replaceAll("[\\p{Cntrl}]", "").strip();return value.length()>64?value.substring(0,64):value;}
    static Identity claims(String provider,String audience,Jwt jwt,String nonce) {
        String issuer=jwt.getClaimAsString("iss");
        boolean accepted=switch(provider) {
            case "google" -> "https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer);
            case "facebook" -> "https://www.facebook.com".equals(issuer);
            case "apple" -> "https://appleid.apple.com".equals(issuer);
            default -> false;
        };
        String subject=jwt.getSubject();
        if (!accepted || jwt.getAudience().size()!=1 || !jwt.getAudience().contains(audience)
            || jwt.getExpiresAt()==null || jwt.getIssuedAt()==null
            || jwt.getIssuedAt().isAfter(java.time.Instant.now().plusSeconds(60))
            || jwt.getExpiresAt().isBefore(java.time.Instant.now())
            || subject==null || subject.isBlank() || subject.length()>255
            || (nonce!=null && !java.util.Objects.equals(jwt.getClaimAsString("nonce"),nonce)))
            throw new IllegalArgumentException("INVALID_PROVIDER_TOKEN");
        String email=jwt.getClaimAsString("email");
        if (email!=null && email.length()>254) email=null;
        Object verified=jwt.getClaims().get("email_verified");
        return new Identity(provider,issuer,audience,subject,email,Boolean.TRUE.equals(verified)||"true".equals(verified),displayName(jwt));
    }
}
