package com.deepdimen.matrix.knowledge.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.knowledge.entity.AiDocumentTemplateEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentTemplateMapper;

/** Authorized read of the existing template; no parallel template store. */
@Component
public class WritingSkillTemplateReader {

	private final AiDocumentTemplateMapper templates;

	private final ObjectProvider<AssistantIdentityResolver> identities;

	public WritingSkillTemplateReader(AiDocumentTemplateMapper templates,
			ObjectProvider<AssistantIdentityResolver> identities) {
		this.templates = templates;
		this.identities = identities;
	}

	public record Snapshot(String tenantId, String templateId, String content, String digest) {
		@Override
		public String toString() {
			return "WritingTemplate[" + templateId + "," + digest + "]";
		}
	}

	public Snapshot capture(Long templateId) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		var resolver = identities.getIfAvailable();
		var tenant = TenantContextHolder.getTenantId();
		if (authentication == null || !authentication.isAuthenticated() || resolver == null || tenant == null
				|| tenant <= 0 || templateId == null || templateId <= 0
				|| !new PermissionService().hasPermission("aiDocumentTemplate_view"))
			throw denied();
		var identity = resolver.currentIdentity();
		if (identity == null || identity.userId() == null || !tenant.toString().equals(identity.tenantId()))
			throw denied();
		var row = templates.selectOne(Wrappers.<AiDocumentTemplateEntity>query()
			.eq("tenant_id", tenant)
			.eq("id", templateId)
			.eq("del_flag", "0"));
		if (row == null || !templateId.equals(row.getId()) || !"0".equals(row.getDelFlag()) || row.getContent() == null
				|| row.getContent().isBlank() || row.getContent().length() > 128000)
			throw denied();
		try {
			String material = "writing-template-v1\n" + tenant + "\n" + templateId + "\n" + row.getContent();
			String digest = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
			return new Snapshot(tenant.toString(), templateId.toString(), row.getContent(), digest);
		}
		catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException(failure);
		}
	}

	public Snapshot requireSame(Snapshot expected) {
		if (expected == null || expected.templateId() == null || !expected.templateId().matches("[1-9][0-9]{0,18}"))
			throw denied();
		var current = capture(Long.valueOf(expected.templateId()));
		if (!current.equals(expected))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
		return current;
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("WRITING_TEMPLATE_NOT_AUTHORIZED");
	}

}
