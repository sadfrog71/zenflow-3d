package com.deepdimen.matrix.work.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.work.api.dto.WorkBriefRunRequest;
import com.deepdimen.matrix.work.brief.WorkBriefIdentityResolver;
import com.deepdimen.matrix.work.service.WorkBriefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work/brief")
@Tag(description = "workBrief", name = "工作简报")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class WorkBriefController {

	private final WorkBriefService briefService;

	private final WorkBriefIdentityResolver identityResolver;

	@GetMapping("/latest")
	@Operation(summary = "查询当前用户最新工作简报")
	public R<?> latest() {
		return R.ok(briefService.latest(identityResolver.current()));
	}

	@GetMapping("/{id}")
	@Operation(summary = "查询当前用户工作简报详情")
	public R<?> detail(@PathVariable("id") Long id) {
		return R.ok(briefService.detail(identityResolver.current(), id));
	}

	@PostMapping("/run")
	@SysLog("手动生成工作简报")
	@Operation(summary = "手动生成当前用户工作简报")
	public R<?> run(@RequestBody(required = false) WorkBriefRunRequest request) {
		boolean force = request != null && Boolean.TRUE.equals(request.getForce());
		return R.ok(briefService.run(identityResolver.current(), force));
	}
}
