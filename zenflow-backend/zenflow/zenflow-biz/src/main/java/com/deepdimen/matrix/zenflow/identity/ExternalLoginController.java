package com.deepdimen.matrix.zenflow.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/zenflow/v1")
public class ExternalLoginController {
    public record Start(String provider,String codeChallenge,String state,String intent,String channel) {}
    public record Complete(String transactionId,String proof,String locale) {}
    record Transaction(String provider,String nonce,String codeChallenge,String state,String intent,long userId,long authVersion,String channel) {}
    private final ZenflowProperties product;
    private final ZenflowExternalProperties providers;
    private final ExternalIdentityVerifier verifier;
    private final ExternalAccountService accounts;
    private final ZenflowUserDetailsService users;
    private final OAuth2AuthorizationService authorizations;
    private final RegisteredClientRepository clients;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public ExternalLoginController(ZenflowProperties product,ZenflowExternalProperties providers,ExternalIdentityVerifier verifier,
            ExternalAccountService accounts,ZenflowUserDetailsService users,OAuth2AuthorizationService authorizations,
            RegisteredClientRepository clients,StringRedisTemplate redis,ObjectMapper json) {
        this.product=product;this.providers=providers;this.verifier=verifier;this.accounts=accounts;this.users=users;
        this.authorizations=authorizations;this.clients=clients;this.redis=redis;this.json=json;
    }
    @PostMapping("/auth/external/challenge")
    public Map<String,Object> challenge(@RequestBody Start body,HttpServletRequest request) throws Exception {
        nativeRequest(request);providers.require(body.provider());
        if(body.codeChallenge()==null || !body.codeChallenge().matches("[A-Za-z0-9_-]{43}")
            || body.state()==null || !body.state().matches("[A-Za-z0-9_-]{32,128}")) throw new IllegalArgumentException("INVALID_AUTH_TRANSACTION");
        String channel=body.channel()==null?"NATIVE":body.channel();
        if(!Set.of("NATIVE","WEB").contains(channel) || ("WEB".equals(channel) && !"google".equals(body.provider())))
            throw new IllegalArgumentException("INVALID_AUTH_TRANSACTION");
        if("WEB".equals(channel)) webRequest(request);
        String intent=body.intent()==null?"LOGIN":body.intent();
        if(!Set.of("LOGIN","BIND").contains(intent)) throw new IllegalArgumentException("INVALID_AUTH_TRANSACTION");
        rate("ip:"+new SecretDigests(product.getAuthHmacKey()).hmac(request.getRemoteAddr()),30);
        MatrixUser current="BIND".equals(intent)?current(request,true):null;
        var secrets=new SecretDigests(product.getAuthHmacKey());
        String id=secrets.randomHex(24),nonce=secrets.randomHex(32);
        long version=current==null?0:((Number)current.getAttributes().get(ZenflowUserDetailsService.AUTH_VERSION)).longValue();
        var tx=new Transaction(body.provider(),nonce,body.codeChallenge(),body.state(),intent,current==null?0:current.getId(),version,channel);
        redis.opsForValue().set(key(id),json.writeValueAsString(tx),Duration.ofMinutes(5));
        return Map.of("transactionId",id,"nonce",nonce,"state",body.state(),"expiresIn",300);
    }
    @PostMapping("/auth/external/complete")
    public Map<String,String> complete(@RequestBody Complete body,HttpServletRequest request) throws Exception {
        nativeRequest(request);
        String key=key(body.transactionId()),stored=redis.opsForValue().get(key);
        if(stored==null) throw new IllegalArgumentException("AUTH_TRANSACTION_EXPIRED");
        rate("attempt:"+body.transactionId(),5);
        Transaction tx=json.readValue(stored,Transaction.class);
        MatrixUser current=null;
        if("BIND".equals(tx.intent())) {
            current=current(request,true);
            if(current.getId()!=tx.userId() || ((Number)current.getAttributes().get(ZenflowUserDetailsService.AUTH_VERSION)).longValue()!=tx.authVersion())
                throw new IllegalArgumentException("AUTH_TRANSACTION_OWNER_CHANGED");
        }
        boolean web="WEB".equals(tx.channel());
        if(web) webRequest(request);
        var identity=web?verifier.verifyGoogleWeb(body.proof(),tx.nonce()):verifier.verify(tx.provider(),body.proof(),tx.nonce());
        Long consumed=redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
            "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end",Long.class),java.util.List.of(key),stored);
        if(!Long.valueOf(1).equals(consumed)) throw new IllegalArgumentException("AUTH_TRANSACTION_EXPIRED");
        if(current!=null) {
            accounts.bind(current.getId(),identity,tx.authVersion());
            return Map.of("nextAction","BOUND","state",tx.state());
        }
        long userId=accounts.login(identity,body.locale());
        MatrixUser user=users.loadExternal(userId);
        var client=clients.findByClientId(product.getOauthClientId());
        if(client==null) throw new IllegalStateException("CLIENT_NOT_CONFIGURED");
        var principal=UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities());
        var authRequest=OAuth2AuthorizationRequest.authorizationCode().authorizationUri(product.getOauthIssuer()+"/oauth2/authorize")
            .clientId(client.getClientId()).redirectUri(web?product.getPublicBaseUrl().toString().replaceAll("/$","")+"/auth/callback":"zenflow://auth/callback").scopes(Set.of("zenflow")).state(tx.state())
            .additionalParameters(Map.of("code_challenge",tx.codeChallenge(),"code_challenge_method","S256")).build();
        Instant now=Instant.now();
        String code=new SecretDigests(product.getAuthHmacKey()).randomHex(32);
        var authorization=OAuth2Authorization.withRegisteredClient(client).principalName(user.getUsername())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("zenflow"))
            .attribute(Principal.class.getName(),principal).attribute(OAuth2AuthorizationRequest.class.getName(),authRequest)
            .token(new OAuth2AuthorizationCode(code,now,now.plusSeconds(90))).build();
        authorizations.save(authorization);
        return Map.of("code",code,"state",tx.state());
    }
    @GetMapping("/account/login-methods")
    public Map<String,Object> methods(HttpServletRequest request) {
        MatrixUser user=current(request,false);
        return Map.of("external",accounts.methods(user.getId()),"available",providers.enabled());
    }
    @DeleteMapping("/account/login-methods/{provider}")
    public Map<String,String> unbind(@PathVariable String provider,HttpServletRequest request) {
        MatrixUser user=current(request,true);
        accounts.unbind(user.getId(),provider,((Number)user.getAttributes().get(ZenflowUserDetailsService.AUTH_VERSION)).longValue());
        return Map.of("nextAction","SIGN_IN");
    }
    private MatrixUser current(HttpServletRequest request,boolean recent) {
        var context=SecurityContextHolder.getContext().getAuthentication();
        if(context==null || !(context.getPrincipal() instanceof MatrixUser user)) throw new IllegalArgumentException("AUTH_REQUIRED");
        users.loadUserByUser(user);
        if(recent) {
            String header=request.getHeader("Authorization");
            var auth=header!=null && header.startsWith("Bearer ")?authorizations.findByToken(header.substring(7),OAuth2TokenType.ACCESS_TOKEN):null;
            var code=auth==null?null:auth.getToken(OAuth2AuthorizationCode.class);
            if(code==null || code.getToken().getIssuedAt().isBefore(Instant.now().minusSeconds(300)))
                throw new IllegalStateException("RECENT_LOGIN_REQUIRED");
        }
        return user;
    }
    private void webRequest(HttpServletRequest request) {
        if(!product.getPublicBaseUrl().toString().replaceAll("/$","").equals(request.getHeader("Origin")))
            throw new IllegalArgumentException("INVALID_AUTH_ORIGIN");
    }
    private void nativeRequest(HttpServletRequest request) {
        if(!"Zenflow".equals(request.getHeader("X-Requested-With"))) throw new IllegalArgumentException("NATIVE_AUTH_REQUIRED");
    }
    private String key(String id) {
        if(id==null || !id.matches("[a-f0-9]{48}")) throw new IllegalArgumentException("INVALID_AUTH_TRANSACTION");
        return "zenflow:"+product.getTenantId()+":external:"+id;
    }
    private void rate(String suffix,int limit) {
        String key="zenflow:"+product.getTenantId()+":external-rate:"+suffix;
        Long count=redis.opsForValue().increment(key);
        if(Long.valueOf(1).equals(count)) redis.expire(key,Duration.ofMinutes(5));
        if(count==null || count>limit) throw new IllegalStateException("AUTH_RATE_LIMITED");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("errorCode",ex.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> conflict(IllegalStateException ex) {
        int status="PROVIDER_NOT_CONFIGURED".equals(ex.getMessage())?503:"AUTH_RATE_LIMITED".equals(ex.getMessage())?429:409;
        return ResponseEntity.status(status).body(Map.of("errorCode",ex.getMessage()));
    }
}
