package com.deepdimen.matrix.zenflow.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

@RestController
@RequestMapping("/zenflow/v1/auth")
public class ZenflowAuthController {
    public record SendRequest(String email, String purpose, String locale) {}
    public record VerifyRequest(String challengeId, String code) {}
    public record RegisterRequest(String verificationTicket, String password, String locale) {}
    public record ResetRequest(String verificationTicket, String newPassword) {}

    private final EmailChallengeService challenges;
    private final ZenflowAccountService accounts;
    private final ZenflowProperties properties;
    private final ZenflowExternalProperties external;
    private final OAuth2AuthorizationService authorizations;
    public ZenflowAuthController(EmailChallengeService challenges, ZenflowAccountService accounts,
                                 ZenflowProperties properties, OAuth2AuthorizationService authorizations, ZenflowExternalProperties external) {
        this.challenges = challenges; this.accounts = accounts; this.properties = properties;
        this.authorizations = authorizations; this.external = external;
    }

    @GetMapping("/policy")
    public Map<String, Object> policy() {
        return Map.of("registrationMethods", new String[]{"EMAIL"}, "loginMethods", java.util.stream.Stream.concat(java.util.stream.Stream.of("EMAIL_PASSWORD"), external.enabled().stream().map(String::toUpperCase)).toList(),
                "passwordMinChars", 10, "passwordMaxUtf8Bytes", 72, "codeLength", 8, "codeExpiresIn", 600,
                "resendAfter", 60);
    }

    @PostMapping("/email/challenges")
    public ResponseEntity<EmailChallengeService.Challenge> send(@RequestBody SendRequest body, HttpServletRequest request,
            @RequestHeader(value="X-Zenflow-CSRF", required=false) String csrf) {
        String session = requireBrowser(request, csrf);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(challenges.send(body.email(), body.purpose(), session, body.locale()));
    }

    @PostMapping("/email/verifications")
    public EmailChallengeService.Verification verify(@RequestBody VerifyRequest body, HttpServletRequest request,
            @RequestHeader(value="X-Zenflow-CSRF", required=false) String csrf) {
        return challenges.verify(body.challengeId(), body.code(), requireBrowser(request, csrf));
    }

    @PostMapping("/registrations")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest body, HttpServletRequest request,
            @RequestHeader(value="X-Zenflow-CSRF", required=false) String csrf) {
        accounts.register(body.verificationTicket(), body.password(), requireBrowser(request, csrf), body.locale());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("nextAction", "SIGN_IN"));
    }

    @PostMapping("/password-resets")
    public Map<String, String> reset(@RequestBody ResetRequest body, HttpServletRequest request,
            @RequestHeader(value="X-Zenflow-CSRF", required=false) String csrf) {
        accounts.reset(body.verificationTicket(), body.newPassword(), requireBrowser(request, csrf));
        return Map.of("nextAction", "SIGN_IN");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value="Authorization", required=false) String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ")) return ResponseEntity.status(401).build();
        var authorization = authorizations.findByToken(bearer.substring(7), OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || !properties.getOauthClientId().equals(authorization.getRegisteredClientId())) {
            return ResponseEntity.status(401).build();
        }
        authorizations.remove(authorization);
        return ResponseEntity.noContent().build();
    }

    private String requireBrowser(HttpServletRequest request, String csrf) {
        HttpSession session = request.getSession(false);
        String expectedOrigin = origin(properties.getPublicBaseUrl());
        if (session == null || csrf == null || !csrf.equals(session.getAttribute("ZENFLOW_CSRF"))
                || !expectedOrigin.equals(request.getHeader("Origin"))) {
            throw new IllegalArgumentException("AUTH_SESSION_REQUIRED");
        }
        return session.getId();
    }
    private static String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("errorCode", ex.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> unavailable(IllegalStateException ex) {
        String code = ex.getMessage();
        HttpStatus status = "SEND_RATE_LIMITED".equals(code) ? HttpStatus.TOO_MANY_REQUESTS
                : "MAIL_UNAVAILABLE".equals(code) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("errorCode", code));
    }
}
