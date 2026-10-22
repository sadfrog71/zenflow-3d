package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/work/task", headers = "X-Task-Contract=2")
public class TaskAiController {

	private final TaskPolicyService policies;

	private final TaskModelRunner models;

	private final WorkTaskV2Service tasks;

	private final ObjectMapper json;

	private final ManagedTaskParseRunner managedParse;

	@GetMapping("/capabilities")
	public R<?> capabilities() {
		return R.ok(policies.capabilities());
	}

	@GetMapping("/policy")
	public R<?> policy() {
		return R.ok(policies.forManagement());
	}

	@PutMapping("/policy")
	public R<?> policy(@RequestBody WorkTaskPolicyEntity input) {
		return R.ok(policies.save(input));
	}

	public record ParseInput(@NotBlank @Size(max = 30000) String text, Long parentId) {
	}

	@PostMapping("/parse-preview")
	@PreAuthorize("@pms.hasPermission('work_task_create')")
	public R<?> parse(@Valid @RequestBody ParseInput input) throws java.io.IOException {
		Long tenant = TenantContextHolder.getTenantId();
		WorkTaskPolicyEntity policy = policies.current(tenant);
		if (!Boolean.TRUE.equals(policy.getEnabled()))
			throw new IllegalArgumentException("请先配置任务 AI 用途");
		if (input.parentId() != null && !tasks.detail(input.parentId()).getAllowedActions().contains("children"))
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "TASK_CHILDREN_FORBIDDEN");
		if ("MANAGED".equals(TaskSkillManagementModes.requireMode(policy.getParseSkillManagementMode())))
			return R.ok(managedParse.execute(policy.getId(), input.parentId(), input.text()));
		String schema = "返回 {\"tasks\":[{\"title\":\"标题\",\"content\":\"Markdown 正文\",\"priority\":\"NORMAL\",\"dueAt\":null}],\"warnings\":[]}。"
				+ "不确定的截止时间必须为 null，不猜测人员或标识。截止时间若有必须包含 +08:00 时区。"
				+ (input.parentId() == null ? "根任务只生成一项，多个事项归入该任务正文。" : "仅拆成该任务的直接子任务，最多 30 项，不生成孙任务。");
		var result = models.run(tenant, SecurityUtils.getUser().getId(), UUID.randomUUID().toString(), "TASK_PARSE",
				policy.getChatModelId(), models.resolveSkill(tenant, policy.getParseSkillPackageId()), schema,
				json.writeValueAsString(Map.of("text", input.text(), "currentTime",
						OffsetDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString())));
		var drafts = result.value().path("tasks");
		if (!drafts.isArray() || drafts.isEmpty() || drafts.size() > (input.parentId() == null ? 1 : 30))
			throw new IllegalArgumentException("模型拆解结果数量无效，请修改文本后重试");
		for (var draft : drafts) {
			if (!draft.isObject() || draft.path("title").asText().isBlank()
					|| draft.path("title").asText().length() > 200 || draft.path("content").asText().isBlank()
					|| draft.path("content").asText().length() > 30000)
				throw new IllegalArgumentException("模型拆解结果缺少有效标题或正文");
		}
		return R.ok(Map.of("drafts", drafts, "warnings", result.value().path("warnings"), "skillPackageId",
				String.valueOf(result.packageId()), "skillHash", result.packageSha256(), "confirmationRequired", true));
	}

}
