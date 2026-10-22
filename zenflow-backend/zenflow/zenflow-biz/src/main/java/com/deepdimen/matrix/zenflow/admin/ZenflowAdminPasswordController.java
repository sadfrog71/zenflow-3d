package com.deepdimen.matrix.zenflow.admin;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/zenflow-admin/v1/account")
public class ZenflowAdminPasswordController {
    public record Change(String currentPassword,String newPassword){}
    private final ZenflowAdminPassword passwords;
    public ZenflowAdminPasswordController(ZenflowAdminPassword passwords){this.passwords=passwords;}
    @PostMapping("/password") public ResponseEntity<Void> change(@RequestBody Change body){passwords.change(body.currentPassword(),body.newPassword());return ResponseEntity.noContent().build();}
}
