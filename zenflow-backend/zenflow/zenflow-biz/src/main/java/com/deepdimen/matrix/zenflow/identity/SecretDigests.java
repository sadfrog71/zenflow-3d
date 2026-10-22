package com.deepdimen.matrix.zenflow.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class SecretDigests {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;
    SecretDigests(String key) { this.key = key.getBytes(StandardCharsets.UTF_8); }
    String randomHex(int bytes) { byte[] value = new byte[bytes]; RANDOM.nextBytes(value); return HexFormat.of().formatHex(value); }
    String code() { int value = RANDOM.nextInt(100_000_000); return String.format("%08d", value); }
    String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("HMAC unavailable", ex); }
    }
    static boolean equalsDigest(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
