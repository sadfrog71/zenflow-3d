package com.deepdimen.matrix.zenflow.identity;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailIdentity {
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}");
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private EmailIdentity() {}

    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("INVALID_EMAIL");
        String email = input.trim();
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1 || email.indexOf('@') != at) {
            throw new IllegalArgumentException("INVALID_EMAIL");
        }
        String local = email.substring(0, at);
        String domain;
        try { domain = IDN.toASCII(email.substring(at + 1), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("INVALID_EMAIL"); }
        String normalized = local.toLowerCase(Locale.ROOT) + "@" + domain;
        if (!LOCAL.matcher(local).matches() || !DOMAIN.matcher(domain).matches()
                || normalized.getBytes(StandardCharsets.UTF_8).length > 254) {
            throw new IllegalArgumentException("INVALID_EMAIL");
        }
        return normalized;
    }

    public static void checkPassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 128
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("INVALID_PASSWORD");
        }
    }
}
