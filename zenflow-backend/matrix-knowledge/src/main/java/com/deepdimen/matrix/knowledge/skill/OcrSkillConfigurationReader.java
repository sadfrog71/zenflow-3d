package com.deepdimen.matrix.knowledge.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.knowledge.entity.AiOcrConfEntity;
import com.deepdimen.matrix.knowledge.mapper.AiOcrConfMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins the original OCR rule and ordered fields; image input is a separate authorized
 * resource.
 */
@Component
public class OcrSkillConfigurationReader {

	private final AiOcrConfMapper configurations;

	private final ObjectProvider<AssistantIdentityResolver> identities;

	private final ObjectMapper json;

	public OcrSkillConfigurationReader(AiOcrConfMapper configurations,
			ObjectProvider<AssistantIdentityResolver> identities, ObjectMapper json) {
		this.configurations = configurations;
		this.identities = identities;
		this.json = json;
	}

	public record Field(String name, String description) {
	}

	public record Snapshot(String tenantId, String configurationId, String rule, List<Field> fields, String digest) {
		public Snapshot {
			fields = List.copyOf(fields);
		}

		@Override
		public String toString() {
			return "OcrConfiguration[" + configurationId + "," + digest + "]";
		}
	}

	public Snapshot capture(Long id) {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var resolver = identities.getIfAvailable();
		var tenant = TenantContextHolder.getTenantId();
		if (auth == null || !auth.isAuthenticated() || resolver == null || tenant == null || tenant <= 0 || id == null
				|| id <= 0 || !new PermissionService().hasPermission("knowledge_ocr_view"))
			throw denied();
		var identity = resolver.currentIdentity();
		if (identity == null || identity.userId() == null || !tenant.toString().equals(identity.tenantId()))
			throw denied();
		var row = configurations
			.selectOne(Wrappers.<AiOcrConfEntity>query().eq("tenant_id", tenant).eq("id", id).eq("del_flag", "0"));
		if (row == null || !id.equals(row.getId()) || !"0".equals(row.getDelFlag()) || row.getOcrPrompt() == null
				|| row.getOcrPrompt().length() > 16000 || row.getOcrMarked() == null
				|| row.getOcrMarked().length() > 256000)
			throw denied();
		try {
			var markers = json.readTree(row.getOcrMarked()).path("markerList");
			if (!markers.isArray() || markers.size() == 0 || markers.size() > 128)
				throw denied();
			var fields = new ArrayList<Field>();
			var names = new HashSet<String>();
			names.add("isContain");
			for (var marker : markers) {
				if (!marker.path("name").isTextual())
					throw denied();
				String name = marker.path("name").textValue();
				if (name.isBlank() || name.length() > 128 || !names.add(name))
					throw denied();
				var description = marker.get("description");
				if (description != null && !description.isNull() && !description.isTextual())
					throw denied();
				String text = description == null || description.isNull() ? null : description.textValue();
				if (text != null && text.length() > 4000)
					throw denied();
				fields.add(new Field(name, text));
			}
			String material = "ocr-configuration-v1\n" + json
				.writeValueAsString(new Snapshot(tenant.toString(), id.toString(), row.getOcrPrompt(), fields, ""));
			String digest = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
			return new Snapshot(tenant.toString(), id.toString(), row.getOcrPrompt(), fields, digest);
		}
		catch (IllegalArgumentException failure) {
			throw failure;
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("OCR_CONFIGURATION_INVALID", failure);
		}
	}

	public Snapshot requireSame(Snapshot expected) {
		if (expected == null || expected.configurationId() == null
				|| !expected.configurationId().matches("[1-9][0-9]{0,18}"))
			throw denied();
		var current = capture(Long.valueOf(expected.configurationId()));
		if (!current.equals(expected))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
		return current;
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("OCR_CONFIGURATION_NOT_AUTHORIZED");
	}

}
