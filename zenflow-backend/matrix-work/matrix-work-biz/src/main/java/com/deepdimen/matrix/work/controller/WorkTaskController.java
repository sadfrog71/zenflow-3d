package com.deepdimen.matrix.work.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.work.api.dto.WorkTaskCreateDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskTransitionDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskUpdateDTO;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.service.WorkTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/work/task")
@Tag(description = "workTask", name = "Pig AI 内部任务")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class WorkTaskController {

	private final WorkTaskService workTaskService;

	@GetMapping("/page")
	@Operation(summary = "查询内部任务")
	public R page(@ParameterObject Page<WorkTaskEntity> page, @RequestParam(required = false) String view,
			@RequestParam(required = false) WorkTaskStatus status, @RequestParam(required = false) String keyword) {
		return R.ok(workTaskService.pageForCurrentUser(page, view, status, keyword));
	}

	@GetMapping("/assignable-users")
	@Operation(summary = "查询当前业务组织可选任务人员")
	public R assignableUsers() {
		return R.ok(workTaskService.listAssignableUsers());
	}

	@GetMapping("/{id}")
	@Operation(summary = "查询内部任务详情")
	public R detail(@PathVariable Long id) {
		return R.ok(workTaskService.requireVisibleTask(id));
	}

	@PostMapping
	@SysLog("创建 Pig AI 内部任务")
	@Operation(summary = "创建内部任务")
	public R create(@Valid @RequestBody WorkTaskCreateDTO request) {
		return R.ok(workTaskService.create(request));
	}

	@PutMapping("/{id}")
	@SysLog("修改 Pig AI 内部任务")
	@Operation(summary = "修改内部任务")
	public R update(@PathVariable Long id, @Valid @RequestBody WorkTaskUpdateDTO request) {
		return R.ok(workTaskService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@SysLog("删除 Pig AI 内部任务")
	@Operation(summary = "删除内部任务")
	public R delete(@PathVariable Long id) {
		workTaskService.deleteTask(id);
		return R.ok();
	}

	@PostMapping("/{id}/transition")
	@SysLog("变更 Pig AI 内部任务状态")
	@Operation(summary = "变更内部任务状态")
	public R transition(@PathVariable Long id, @Valid @RequestBody WorkTaskTransitionDTO request) {
		workTaskService.requireVisibleTask(id);
		return R.ok(workTaskService.transition(id, request));
	}

}
