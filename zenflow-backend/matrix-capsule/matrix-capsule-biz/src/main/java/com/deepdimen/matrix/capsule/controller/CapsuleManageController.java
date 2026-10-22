package com.deepdimen.matrix.capsule.controller;

import com.deepdimen.matrix.capsule.security.CapsuleIdentityResolver;
import com.deepdimen.matrix.capsule.service.CapsuleService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/capsule/manage")
@Tag(description = "capsuleManage", name = "闪念胶囊管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class CapsuleManageController {

    private final CapsuleService capsuleService;
    private final CapsuleIdentityResolver identityResolver;

    @GetMapping("/overview")
    @HasPermission("capsule_manage_view")
    @Operation(summary = "查看当前租户胶囊聚合概览，不返回个人内容")
    public R<?> overview() {
        Long tenantId = identityResolver.current().tenantId();
        return R.ok(capsuleService.tenantOverview(tenantId));
    }
}
