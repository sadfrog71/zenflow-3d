package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ExternalIdentityVerifierTest {
    private Jwt token(String iss,String aud,String nonce,Instant expires) {
        return Jwt.withTokenValue("fixture").header("alg","RS256").issuer(iss).subject("provider-user")
            .audience(List.of(aud)).issuedAt(Instant.now().minusSeconds(10)).expiresAt(expires).claim("nonce",nonce).build();
    }
    @Test void checksAudienceIssuerNonceAndExpiry() {
        var valid=token("https://appleid.apple.com","product.app","nonce",Instant.now().plusSeconds(60));
        assertEquals("provider-user",ExternalIdentityVerifier.claims("apple","product.app",valid,"nonce").subject());
        assertThrows(IllegalArgumentException.class,()->ExternalIdentityVerifier.claims("apple","different.app",valid,"nonce"));
        assertThrows(IllegalArgumentException.class,()->ExternalIdentityVerifier.claims("apple","product.app",valid,"wrong-nonce"));
        assertThrows(IllegalArgumentException.class,()->ExternalIdentityVerifier.claims("facebook","product.app",valid,"nonce"));
        assertThrows(IllegalArgumentException.class,()->ExternalIdentityVerifier.claims("apple","product.app",token("https://appleid.apple.com","product.app","nonce",Instant.now().minusSeconds(1)),"nonce"));
    }
    @Test void disabledProviderDoesNotAttemptNetwork() {
        var verifier=new ExternalIdentityVerifier(new ZenflowExternalProperties());
        assertThrows(IllegalStateException.class,()->verifier.verify("google","anything","nonce"));
        assertThrows(IllegalArgumentException.class,()->verifier.verify("other","anything","nonce"));
    }
    @Test void cryptographicSignatureRejectsOtherSigningKey() throws Exception {
        var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
        var other = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
        var properties = new ZenflowExternalProperties();
        var provider = new ZenflowExternalProperties.Provider();
        provider.setEnabled(true); provider.setAudience("product.app");
        properties.getProviders().put("apple", provider);
        var verifier = new ExternalIdentityVerifier(properties);
        var field = ExternalIdentityVerifier.class.getDeclaredField("decoders"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var decoders = (java.util.Map<String,org.springframework.security.oauth2.jwt.NimbusJwtDecoder>)field.get(verifier);
        decoders.put("apple",org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://appleid.apple.com").audience("product.app")
            .subject("fixture-subject").issueTime(java.util.Date.from(Instant.now().minusSeconds(1)))
            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(60))).claim("nonce","nonce").build();
        var valid = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),claims);
        valid.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));
        assertEquals("fixture-subject",verifier.verify("apple",valid.serialize(),"nonce").subject());
        var forged = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),claims);
        forged.sign(new com.nimbusds.jose.crypto.RSASSASigner(other));
        assertThrows(IllegalArgumentException.class,()->verifier.verify("apple",forged.serialize(),"nonce"));
    }
    @Test void browserGoogleRequiresSignedAudienceAndTransactionNonce() throws Exception {
        var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
        var properties = new ZenflowExternalProperties();
        var provider = new ZenflowExternalProperties.Provider();
        provider.setEnabled(true); provider.setAudience("web-client"); provider.setClientSecret("test-only");
        properties.getProviders().put("google", provider);
        var verifier = new ExternalIdentityVerifier(properties);
        var field = ExternalIdentityVerifier.class.getDeclaredField("decoders"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var decoders = (java.util.Map<String,org.springframework.security.oauth2.jwt.NimbusJwtDecoder>)field.get(verifier);
        decoders.put("google",org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience("web-client")
            .subject("same-google-user").issueTime(java.util.Date.from(Instant.now().minusSeconds(1)))
            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(60))).claim("nonce","single-transaction").build();
        var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),claims);
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));
        assertEquals("same-google-user",verifier.verifyGoogleWeb(signed.serialize(),"single-transaction").subject());
        assertThrows(IllegalArgumentException.class,()->verifier.verifyGoogleWeb(signed.serialize(),"other-transaction"));
        assertThrows(IllegalArgumentException.class,()->verifier.verifyGoogleWeb(signed.serialize(),null));
        assertThrows(IllegalArgumentException.class,()->verifier.verifyGoogleWeb(signed.serialize(),""));
        provider.setAudience("different-client");
        assertThrows(IllegalArgumentException.class,()->verifier.verifyGoogleWeb(signed.serialize(),"single-transaction"));
    }

}
