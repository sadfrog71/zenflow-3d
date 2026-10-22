package com.deepdimen.matrix.work.file;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.entity.SysFile;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysFileMapper;
import com.deepdimen.matrix.admin.service.SysFileService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.file.core.FileTemplate;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.api.service.TaskFileAccess;
import com.deepdimen.matrix.work.entity.WorkTaskFileEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskFileMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TaskFileService implements TaskFileAccess {

	private final SysFileMapper files;

	private final SysFileService storage;

	private final FileTemplate objects;

	private final WorkTaskFileMapper links;

	private final TenantUserDirectory directory;

	@Override
	public void requireAttachable(Long tenantId, Long actorId, List<Long> fileIds) {
		if (fileIds == null || fileIds.isEmpty())
			return;
		if (fileIds.size() > 20 || !directory.isAssignableUser(tenantId, actorId))
			denied();
		String username = directory.listTenantUsers(tenantId)
			.stream()
			.filter(u -> Objects.equals(u.userId(), actorId))
			.map(TenantUserDirectory.AssignableUser::username)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("上传人员不可用"));
		for (Long id : fileIds) {
			SysFile file = requireFile(tenantId, id);
			if (!Objects.equals(username, file.getCreateBy()))
				denied();
		}
	}

	public Map<String, Object> upload(MultipartFile file) {
		Long tenant = TenantContextHolder.getTenantId();
		if (SecurityUtils.getUser() == null || !directory.isAssignableUser(tenant, SecurityUtils.getUser().getId()))
			denied();
		if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024)
			throw new IllegalArgumentException("任务附件须为 1 字节至 20 MB");
		String name = UUID.randomUUID() + ".bin";
		R<?> uploaded = storage.uploadFile(file, name, "work-task", null, "WORK_TASK");
		if (uploaded.getCode() != 0)
			throw new IllegalStateException("任务附件上传失败");
		SysFile saved = files
			.selectOne(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getTenantId, tenant).eq(SysFile::getFileName, name));
		if (saved == null)
			throw new IllegalStateException("任务附件记录缺失");
		return Map.of("fileId", String.valueOf(saved.getId()), "name", saved.getOriginal(), "size",
				saved.getFileSize());
	}

	/**
	 * Called only after the controller has authorized the task and historical-file scope.
	 */
	public List<Map<String, Object>> list(Long tenant, Long taskId) {
		return links
			.selectList(Wrappers.<WorkTaskFileEntity>lambdaQuery()
				.eq(WorkTaskFileEntity::getTenantId, tenant)
				.eq(WorkTaskFileEntity::getTaskId, taskId))
			.stream()
			.map(link -> {
				SysFile file = requireFile(tenant, link.getFileId());
				return Map.<String, Object>of("fileId", String.valueOf(file.getId()), "name", file.getOriginal(),
						"size", file.getFileSize(), "purpose", link.getPurpose(), "submissionId",
						link.getSubmissionId() == null ? "" : link.getSubmissionId().toString());
			})
			.toList();
	}

	public void download(Long tenant, Long taskId, Long fileId, HttpServletResponse response)
			throws java.io.IOException {
		if (links.selectCount(Wrappers.<WorkTaskFileEntity>lambdaQuery()
			.eq(WorkTaskFileEntity::getTenantId, tenant)
			.eq(WorkTaskFileEntity::getTaskId, taskId)
			.eq(WorkTaskFileEntity::getFileId, fileId)) == 0)
			denied();
		SysFile file = requireFile(tenant, fileId);
		response.setContentType("application/octet-stream");
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Content-Disposition",
				ContentDisposition.attachment()
					.filename(file.getOriginal(), StandardCharsets.UTF_8)
					.build()
					.toString());
		try (var object = objects.getObject(file.getBucketName(), file.getDir(), file.getFileName())) {
			object.getObjectContent().transferTo(response.getOutputStream());
		}
	}

	private SysFile requireFile(Long tenant, Long id) {
		if (tenant == null || id == null) {
			denied();
		}
		SysFile file = files
			.selectOne(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getTenantId, tenant).eq(SysFile::getId, id));
		if (file == null || !"WORK_TASK".equals(file.getType()))
			denied();
		return file;
	}

	private void denied() {
		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_FILE_NOT_FOUND");
	}

}
