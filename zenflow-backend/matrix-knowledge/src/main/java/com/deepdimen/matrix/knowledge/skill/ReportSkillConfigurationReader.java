package com.deepdimen.matrix.knowledge.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.knowledge.entity.AiReportConfEntity;
import com.deepdimen.matrix.knowledge.mapper.AiReportConfMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Fixed report configuration plus bytes from the existing tenant-scoped file service. */
@Component
public class ReportSkillConfigurationReader {

	private static final int MAX_BYTES = 20 * 1024 * 1024;

	private final AiReportConfMapper configurations;

	private final RemoteFileService files;

	private final ObjectProvider<AssistantIdentityResolver> identities;

	private final ObjectMapper json;

	public ReportSkillConfigurationReader(AiReportConfMapper configurations, RemoteFileService files,
			ObjectProvider<AssistantIdentityResolver> identities, ObjectMapper json) {
		this.configurations = configurations;
		this.files = files;
		this.identities = identities;
		this.json = json;
	}

	public record Snapshot(String tenantId, String configurationId, String title, String rule, String marked,
			String fileName, String fileDigest, String digest) {
		@Override
		public String toString() {
			return "ReportConfiguration[" + configurationId + "," + digest + "]";
		}
	}

	public record Capture(Snapshot snapshot, byte[] templateBytes) {
		public Capture {
			templateBytes = templateBytes.clone();
		}

		@Override
		public byte[] templateBytes() {
			return templateBytes.clone();
		}

		@Override
		public String toString() {
			return snapshot.toString();
		}
	}

	public Capture capture(Long id) {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var resolver = identities.getIfAvailable();
		var tenant = TenantContextHolder.getTenantId();
		if (auth == null || !auth.isAuthenticated() || resolver == null || tenant == null || tenant <= 0 || id == null
				|| id <= 0 || !new PermissionService().hasPermission("knowledge_aiReportConf_view"))
			throw denied();
		var identity = resolver.currentIdentity();
		if (identity == null || identity.userId() == null || !tenant.toString().equals(identity.tenantId()))
			throw denied();
		var row = configurations
			.selectOne(Wrappers.<AiReportConfEntity>query().eq("tenant_id", tenant).eq("id", id).eq("del_flag", "0"));
		if (row == null || !id.equals(row.getId()) || !"0".equals(row.getDelFlag()))
			throw denied();
		String file = row.getReportResource();
		if (file == null || file.isBlank() || file.length() > 2048 || file.contains(":") || file.contains("..")
				|| file.contains("\\") || file.contains("?") || file.contains("#") || file.contains("%")
				|| file.startsWith("//"))
			throw denied();
		try (var response = files.getFile(file)) {
			if (response == null || response.status() != 200 || response.body() == null)
				throw denied();
			byte[] bytes;
			try (var input = response.body().asInputStream()) {
				bytes = input.readNBytes(MAX_BYTES + 1);
			}
			if (bytes.length == 0 || bytes.length > MAX_BYTES || !tenant.equals(TenantContextHolder.getTenantId())
					|| auth != SecurityContextHolder.getContext().getAuthentication()
					|| !sameAuthorization(identity, resolver.currentIdentity()))
				throw denied();
			var fixed = new Snapshot(tenant.toString(), id.toString(), row.getReportTitle(), row.getReportPrompt(),
					row.getReportMarked(), file, sha(bytes), "");
			String digest = sha(
					("report-configuration-v1\n" + json.writeValueAsString(fixed)).getBytes(StandardCharsets.UTF_8));
			return new Capture(new Snapshot(fixed.tenantId(), fixed.configurationId(), fixed.title(), fixed.rule(),
					fixed.marked(), file, fixed.fileDigest(), digest), bytes);
		}
		catch (IllegalArgumentException failure) {
			throw failure;
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("REPORT_RESOURCE_UNAVAILABLE", failure);
		}
	}

	public Capture requireSame(Snapshot expected) {
		if (expected == null || expected.configurationId() == null
				|| !expected.configurationId().matches("[1-9][0-9]{0,18}"))
			throw denied();
		var current = capture(Long.valueOf(expected.configurationId()));
		if (!current.snapshot().equals(expected))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
		return current;
	}

	private static boolean sameAuthorization(IdentityEnvelope before, IdentityEnvelope after) {
		return after != null && Objects.equals(before.tenantId(), after.tenantId())
				&& Objects.equals(before.userId(), after.userId())
				&& Objects.equals(before.currentOrgId(), after.currentOrgId())
				&& Objects.equals(before.accessibleOrgIds(), after.accessibleOrgIds())
				&& Objects.equals(before.roleIds(), after.roleIds())
				&& Objects.equals(before.deptIds(), after.deptIds());
	}

	private static String sha(byte[] bytes) throws java.security.NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("REPORT_RESOURCE_NOT_AUTHORIZED");
	}

}
