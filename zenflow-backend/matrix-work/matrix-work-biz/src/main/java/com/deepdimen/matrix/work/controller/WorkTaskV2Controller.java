package com.deepdimen.matrix.work.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.work.api.dto.*;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/work/task", headers = "X-Task-Contract=2")
public class WorkTaskV2Controller {

	private final WorkTaskV2Service service;

	@GetMapping("/page")
	public R<?> page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
			@RequestParam(required = false) String view, @RequestParam(required = false) WorkTaskStatus status,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String risk,
			@RequestParam(required = false) Long personId, @RequestParam(required = false) Long deptId,
			@RequestParam(required = false) String taskLevel) {
		return R.ok(service.page(current, size,
				new WorkTaskV2Service.Filter(view, status, keyword, risk, personId, deptId, taskLevel)));
	}

	@GetMapping("/statistics")
	public R<?> statistics(@RequestParam(required = false) String view,
			@RequestParam(required = false) WorkTaskStatus status, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String risk, @RequestParam(required = false) Long personId,
			@RequestParam(required = false) Long deptId, @RequestParam(required = false) String taskLevel) {
		return R.ok(service
			.statistics(new WorkTaskV2Service.Filter(view, status, keyword, risk, personId, deptId, taskLevel)));
	}

	@GetMapping("/workloads")
	public R<?> workloads(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String view,
			@RequestParam(required = false) WorkTaskStatus status, @RequestParam(required = false) String risk,
			@RequestParam(required = false) Long personId, @RequestParam(required = false) Long deptId,
			@RequestParam(required = false) String taskLevel) {
		return R.ok(service.workloads(current, size, keyword,
				new WorkTaskV2Service.Filter(view, status, null, risk, personId, deptId, taskLevel)));
	}

	@GetMapping("/assignable-users")
	public R<?> candidates(@RequestParam(defaultValue = "EXECUTOR") String purpose,
			@RequestParam(required = false) Long taskId, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long deptId, @RequestParam(defaultValue = "1") long current,
			@RequestParam(defaultValue = "20") long size) {
		return R.ok(service.candidates(purpose, taskId, keyword, deptId, current, size));
	}

	@GetMapping("/{id}")
	public R<?> detail(@PathVariable Long id) {
		return R.ok(service.detail(id));
	}

	@GetMapping("/{id}/events")
	public R<?> events(@PathVariable Long id, @RequestParam(required = false) String category,
			@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size) {
		return R.ok(service.eventPage(id, category, current, size));
	}

	@PostMapping
	public R<?> create(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkTaskV2CreateDTO request) {
		return R.ok(service.create(key, request));
	}

	@PatchMapping("/{id}")
	public R<?> update(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "update", null, key, request);
	}

	@PostMapping("/{id}/actions/{action}")
	public R<?> action(@PathVariable Long id, @PathVariable String action, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		if (!java.util.Set.of("start", "progress", "block", "resume").contains(action))
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND);
		return run(id, action, null, key, request);
	}

	@PostMapping("/{id}/submissions")
	public R<?> submit(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "submit", null, key, request);
	}

	@PostMapping("/{id}/acceptance")
	public R<?> accept(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "accept", null, key, request);
	}

	@PostMapping("/{id}/acceptor")
	public R<?> acceptor(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "acceptor", null, key, request);
	}

	@PostMapping("/{id}/transfers")
	public R<?> transfer(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "transfer", null, key, request);
	}

	@PostMapping("/{id}/transfers/{transferId}/decision")
	public R<?> transferDecision(@PathVariable Long id, @PathVariable Long transferId,
			@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "transfer-decision", transferId, key, request);
	}

	@PostMapping("/{id}/children")
	public R<?> children(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskChildrenDTO request) {
		return R.ok(service.children(id, key, request));
	}

	@PostMapping("/{id}/dispatch")
	public R<?> dispatch(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "dispatch", null, key, request);
	}

	@PostMapping("/{id}/nudges")
	public R<?> nudge(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody WorkTaskCommandDTO request) {
		return run(id, "nudge", null, key, request);
	}

	private R<?> run(Long id, String action, Long transferId, String key, WorkTaskCommandDTO request) {
		return R.ok(service.command(id, action, transferId, key, request));
	}

}
