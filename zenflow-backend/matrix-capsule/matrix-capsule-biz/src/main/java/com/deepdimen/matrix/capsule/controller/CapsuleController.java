package com.deepdimen.matrix.capsule.controller;

import com.deepdimen.matrix.capsule.api.dto.CapsuleCreateRequest;
import com.deepdimen.matrix.capsule.api.dto.CapsulePageQuery;
import com.deepdimen.matrix.capsule.api.dto.CapsuleStatusChangeRequest;
import com.deepdimen.matrix.capsule.api.dto.CapsuleUpdateRequest;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.security.CapsuleIdentityResolver;
import com.deepdimen.matrix.capsule.service.CapsuleService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/capsule")
@Tag(description = "capsule", name = "闪念胶囊")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class CapsuleController {

    private final CapsuleService capsuleService;
    private final CapsuleIdentityResolver identityResolver;

    @GetMapping("/page")
    @Operation(summary = "查询本人胶囊")
    public R<?> page(@ParameterObject CapsulePageQuery query) {
        return R.ok(capsuleService.page(identity(), query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询本人胶囊详情")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(capsuleService.detail(identity(), id));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "查询本人胶囊版本")
    public R<?> versions(@PathVariable Long id) {
        return R.ok(capsuleService.versions(identity(), id));
    }

    @PostMapping
    @SysLog("创建闪念胶囊")
    @Operation(summary = "创建本人胶囊")
    public R<?> create(@Valid @RequestBody CapsuleCreateRequest request) {
        return R.ok(capsuleService.create(identity(), request));
    }

    @PutMapping("/{id}")
    @SysLog("编辑闪念胶囊")
    @Operation(summary = "编辑本人胶囊并生成新版本")
    public R<?> update(
            @PathVariable Long id, @Valid @RequestBody CapsuleUpdateRequest request) {
        return R.ok(capsuleService.update(identity(), id, request));
    }

    @PostMapping("/{id}/archive")
    @SysLog("归档闪念胶囊")
    @Operation(summary = "归档本人胶囊")
    public R<?> archive(
            @PathVariable Long id, @Valid @RequestBody CapsuleStatusChangeRequest request) {
        return R.ok(capsuleService.changeStatus(
                identity(), id, request.getVersion(), CapsuleStatus.ARCHIVED));
    }

    @PostMapping("/{id}/restore")
    @SysLog("恢复闪念胶囊")
    @Operation(summary = "恢复本人胶囊")
    public R<?> restore(
            @PathVariable Long id, @Valid @RequestBody CapsuleStatusChangeRequest request) {
        return R.ok(capsuleService.changeStatus(
                identity(), id, request.getVersion(), CapsuleStatus.ACTIVE));
    }

    private CapsuleIdentity identity() {
        return identityResolver.current();
    }
}
