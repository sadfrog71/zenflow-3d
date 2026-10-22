package com.deepdimen.matrix.zenflow.identity;

import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EmailChallengeService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EmailChallengeService.class);
    public record Challenge(String challengeId, int expiresIn, int resendAfter) {}
    public record Verification(String verificationTicket, int expiresIn, String purpose) {}
    public record Ticket(String email, String purpose, String session, String accountId, String authVersion) {}

    private static final String VERIFY_LUA = """
        if redis.call('GET', KEYS[2]) ~= ARGV[1] then return -1 end
        if redis.call('HGET', KEYS[1], 'session') ~= ARGV[2] then return -2 end
        local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
        if attempts <= 0 then return -3 end
        if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[3] then
          redis.call('HINCRBY', KEYS[1], 'attempts', -1)
          return 0
        end
        local fields = redis.call('HGETALL', KEYS[1])
        for i = 1, #fields, 2 do
          if fields[i] ~= 'digest' and fields[i] ~= 'attempts' then
            redis.call('HSET', KEYS[3], fields[i], fields[i+1])
          end
        end
        redis.call('EXPIRE', KEYS[3], 600)
        redis.call('DEL', KEYS[1], KEYS[2])
        return 1
        """;

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> sender;
    private final ZenflowProperties properties;
    private final SecretDigests digests;

    public EmailChallengeService(StringRedisTemplate redis, JdbcTemplate jdbc, ObjectProvider<JavaMailSender> sender,
                                 ZenflowProperties properties) {
        this.redis = redis; this.jdbc = jdbc; this.sender = sender; this.properties = properties;
        this.digests = new SecretDigests(properties.getAuthHmacKey());
    }

    public Challenge send(String rawEmail, String purpose, String session) { return send(rawEmail,purpose,session,null); }

    public Challenge send(String rawEmail, String purpose, String session, String locale) {
        if (sender.getIfAvailable() == null || properties.getEmailFrom() == null
                || properties.getEmailFrom().isBlank()) {
            LOG.warn("Product mail not configured: senderPresent={}, fromPresent={}", sender.getIfAvailable()!=null, properties.getEmailFrom()!=null && !properties.getEmailFrom().isBlank());
            throw new IllegalStateException("MAIL_UNAVAILABLE");
        }
        String email = EmailIdentity.normalize(rawEmail);
        if (!"REGISTER".equals(purpose) && !"RESET".equals(purpose)) throw new IllegalArgumentException("INVALID_PURPOSE");
        if (session == null || session.isBlank()) throw new IllegalArgumentException("AUTH_SESSION_REQUIRED");
        String accountKey = digests.hmac(email + ":" + purpose + ":" + properties.getTenantId());
        String rateKey = key("rate:" + accountKey);
        Boolean accepted = redis.opsForValue().setIfAbsent(rateKey, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(accepted)) throw new IllegalStateException("SEND_RATE_LIMITED");

        String id = digests.randomHex(20), code = digests.code();
        String currentKey = key("current:" + accountKey + ":" + digests.hmac(session));
        String challengeKey = key("challenge:" + id);
        String accountId = "", authVersion = "";
        if ("RESET".equals(purpose)) {
            var rows = jdbc.queryForList("SELECT user_id, auth_version FROM app_user WHERE tenant_id=? AND email_normalized=? AND del_flag='0' AND lock_flag='0' AND email_verified_at IS NOT NULL",
                    properties.getTenantId(), email);
            if (!rows.isEmpty()) {
                accountId = rows.get(0).get("user_id").toString();
                authVersion = rows.get(0).get("auth_version").toString();
            }
        }
        redis.opsForHash().putAll(challengeKey, Map.of(
                "email", email, "purpose", purpose, "session", session,
                "accountId", accountId, "authVersion", authVersion,
                "digest", digests.hmac(id + ":" + code), "attempts", "5"));
        redis.expire(challengeKey, Duration.ofMinutes(10));
        redis.opsForValue().set(currentKey, id, Duration.ofMinutes(10));
        try {
            if (!"RESET".equals(purpose) || !accountId.isEmpty()) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.getEmailFrom());
                message.setTo(email);
                boolean chinese=locale!=null && locale.startsWith("zh");
                message.setSubject(chinese ? ("REGISTER".equals(purpose) ? "此间邮箱验证" : "此间密码重置")
                        : ("REGISTER".equals(purpose) ? "此间 — Email verification" : "此间 — Password reset"));
                message.setText(chinese ? "你的此间验证码是 " + code + "。10 分钟内有效。若非本人操作，请忽略此邮件。"
                        : "Your verification code is " + code + ". It expires in 10 minutes. If you did not request this, ignore this email.");
                JavaMailSender transport = sender.getIfAvailable();
                if (transport == null || properties.getEmailFrom() == null || properties.getEmailFrom().isBlank()) {
                    throw new IllegalStateException("MAIL_UNAVAILABLE");
                }
                transport.send(message);
            }
        } catch (RuntimeException ex) {
            Throwable cause=ex; while(cause.getCause()!=null && cause.getCause()!=cause) cause=cause.getCause();
            LOG.warn("Product mail delivery failed: {}, cause={}", ex.getClass().getSimpleName(), cause.getClass().getSimpleName());
            redis.delete(challengeKey);
            redis.delete(currentKey);
            redis.delete(rateKey);
            throw new IllegalStateException("MAIL_UNAVAILABLE");
        }
        return new Challenge(id, 600, 60);
    }

    public Verification verify(String challengeId, String code, String session) {
        if (challengeId == null || !challengeId.matches("[0-9a-f]{40}") || code == null || !code.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("INVALID_CODE");
        }
        String challengeKey = key("challenge:" + challengeId);
        String email = (String) redis.opsForHash().get(challengeKey, "email");
        String purpose = (String) redis.opsForHash().get(challengeKey, "purpose");
        if (email == null || purpose == null) throw new IllegalArgumentException("CODE_EXPIRED");
        String accountKey = digests.hmac(email + ":" + purpose + ":" + properties.getTenantId());
        String currentKey = key("current:" + accountKey + ":" + digests.hmac(session));
        String ticket = digests.randomHex(24);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(VERIFY_LUA, Long.class);
        Long result = redis.execute(script, java.util.List.of(challengeKey, currentKey, key("ticket:" + ticket)),
                challengeId, session, digests.hmac(challengeId + ":" + code));
        if (result == null || result != 1) throw new IllegalArgumentException(result != null && result == 0 ? "INVALID_CODE" : "CODE_EXPIRED");
        return new Verification(ticket, 600, purpose);
    }

    public Ticket ticket(String token, String session, String purpose) {
        if (token == null || !token.matches("[0-9a-f]{48}")) throw new IllegalArgumentException("INVALID_TICKET");
        Map<Object, Object> fields = redis.opsForHash().entries(key("ticket:" + token));
        if (fields.isEmpty() || !session.equals(fields.get("session")) || !purpose.equals(fields.get("purpose"))) {
            throw new IllegalArgumentException("INVALID_TICKET");
        }
        return new Ticket((String) fields.get("email"), purpose, session,
                (String) fields.get("accountId"), (String) fields.get("authVersion"));
    }

    public String receiptKey(String ticket) { return digests.hmac("receipt:" + ticket); }
    public String requestDigest(String operation, String email, String password) {
        return digests.hmac(operation + ":" + email + ":" + password);
    }
    public void discard(String ticket) { redis.delete(key("ticket:" + ticket)); }
    private String key(String suffix) { return "zenflow:" + properties.getTenantId() + ":auth:" + suffix; }
}
