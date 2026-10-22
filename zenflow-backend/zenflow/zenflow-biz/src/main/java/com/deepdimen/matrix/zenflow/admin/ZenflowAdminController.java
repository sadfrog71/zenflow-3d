package com.deepdimen.matrix.zenflow.admin;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/zenflow-admin/v1")
public class ZenflowAdminController {
    private final ZenflowAdminAccess access;
    private final ZenflowModelService models;
    private final OAuth2AuthorizationService authorizations;
    private final ZenflowModelGateway gateway;
    private final ZenflowOperationsService operations;
    public ZenflowAdminController(ZenflowAdminAccess access,ZenflowModelService models,OAuth2AuthorizationService authorizations,ZenflowModelGateway gateway,ZenflowOperationsService operations) {this.access=access;this.models=models;this.authorizations=authorizations;this.gateway=gateway;this.operations=operations;}
    @GetMapping("/me") public Map<String,Object> me(){var user=access.require();return Map.of("username",user.getUsername(),"subjectType","ADMIN_USER","tenantId",user.getTenantId().toString(),"permissions",List.of("models","companion","operations","users"));}
    @GetMapping("/models") public List<Map<String,Object>> list(){access.require();return models.list();}
    @GetMapping("/operations") public Map<String,Object> operations(){access.require();return operations.overview();}
    @PostMapping("/models") public Map<String,Object> create(@RequestBody Map<String,Object> body){access.require();return models.save(null,body);}
    @PutMapping("/models/{id}") public Map<String,Object> update(@PathVariable String id,@RequestBody Map<String,Object> body){access.require();return models.save(id,body);}
    @PostMapping("/models/{id}/activate") public Map<String,Object> activate(@PathVariable String id,@RequestBody Map<String,Integer> body){access.require();return models.activate(id,body.getOrDefault("expectedVersion",-1));}
    @PostMapping("/models/{id}/test") public Map<String,Object> test(@PathVariable String id,@RequestBody Map<String,Integer> body){access.require();gateway.probe(id,body.getOrDefault("expectedVersion",-1));return Map.of("tested",true,"modelId",id);}
    @PostMapping("/logout") public ResponseEntity<Void> logout(@RequestHeader("Authorization") String bearer) {
        access.require();
        if (!bearer.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"ADMIN_AUTH_REQUIRED");
        var authorization=authorizations.findByToken(bearer.substring(7),OAuth2TokenType.ACCESS_TOKEN);
        if (authorization==null || !"zenflow-admin".equals(authorization.getRegisteredClientId()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"ADMIN_AUTH_REQUIRED");
        authorizations.remove(authorization);
        return ResponseEntity.noContent().build();
    }
}
