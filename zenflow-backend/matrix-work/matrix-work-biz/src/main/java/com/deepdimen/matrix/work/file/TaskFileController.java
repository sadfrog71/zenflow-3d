package com.deepdimen.matrix.work.file;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/work/task", headers = "X-Task-Contract=2")
public class TaskFileController {

	private final TaskFileService files;

	private final WorkTaskV2Service tasks;

	@PostMapping("/files")
	@PreAuthorize("@pms.hasPermission('work_task_create') or @pms.hasPermission('work_task_execute')")
	public R<?> upload(@RequestPart("file") MultipartFile file) {
		return R.ok(files.upload(file));
	}

	@GetMapping("/{id}/files")
	public R<?> list(@PathVariable Long id) {
		tasks.requireHistoryVisible(id);
		return R.ok(files.list(TenantContextHolder.getTenantId(), id));
	}

	@GetMapping("/{id}/files/{fileId}")
	public void download(@PathVariable Long id, @PathVariable Long fileId, HttpServletResponse response)
			throws java.io.IOException {
		tasks.requireHistoryVisible(id);
		files.download(TenantContextHolder.getTenantId(), id, fileId, response);
	}

}
