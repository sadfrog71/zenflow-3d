package com.deepdimen.matrix.work.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.api.enums.WorkSourceType;
import com.deepdimen.matrix.work.api.vo.WorkSourceSyncVO;
import com.deepdimen.matrix.work.entity.WorkEvidenceEntity;
import com.deepdimen.matrix.work.entity.WorkSourceEntity;
import com.deepdimen.matrix.work.mapper.WorkEvidenceMapper;
import com.deepdimen.matrix.work.mapper.WorkSourceMapper;
import com.deepdimen.matrix.work.security.CurrentOrganizationResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;

/**
 * Read-only IMAP/POP3 collector. Folders are always opened READ_ONLY and the
 * adapter has no SMTP dependency or send operation.
 */
@Service
@RequiredArgsConstructor
public class WorkMailSyncService {

	private static final int MAX_EXCERPT = 2000;

	private final WorkSourceMapper sourceMapper;

	private final WorkEvidenceMapper evidenceMapper;

	private final CurrentOrganizationResolver organizationResolver;

	private final ObjectMapper objectMapper;

	public WorkSourceSyncVO sync(Long sourceId) {
		Long tenantId = TenantContextHolder.getTenantId();
		Long userId = SecurityUtils.getUser() == null ? null : SecurityUtils.getUser().getId();
		Long orgId = organizationResolver.resolve();
		if (tenantId == null || userId == null) {
			throw new IllegalStateException("缺少租户或用户上下文");
		}
		WorkSourceEntity source = sourceMapper.selectOne(Wrappers.<WorkSourceEntity>lambdaQuery()
				.eq(WorkSourceEntity::getId, sourceId)
				.eq(WorkSourceEntity::getTenantId, tenantId)
				.last("LIMIT 1"));
		if (source == null || source.getSourceType() != WorkSourceType.ENTERPRISE_MAIL) {
			throw new IllegalArgumentException("未找到当前租户的企业邮箱数据源");
		}
		if (!Boolean.TRUE.equals(source.getEnabled())) {
			throw new IllegalStateException("企业邮箱数据源未启用");
		}
		if (!orgId.equals(source.getOrgId()) || !userId.equals(source.getOwnerUserId())) {
			throw new IllegalStateException("无权同步其他组织或其他用户的邮箱");
		}

		LocalDateTime syncedAt = LocalDateTime.now();
		try {
			JsonNode config = objectMapper.readTree(source.getConfigJson());
			SyncCount count = collect(source, config, tenantId, userId, orgId);
			source.setHealthStatus("HEALTHY");
			source.setLastError(null);
			source.setLastSyncAt(syncedAt);
			source.setSyncCursor(syncedAt.toString());
			sourceMapper.updateById(source);
			return new WorkSourceSyncVO(sourceId, count.scanned(), count.inserted(), count.duplicates(), syncedAt);
		}
		catch (Exception ex) {
			source.setHealthStatus("ERROR");
			source.setLastError(limit(safeError(ex), 1000));
			sourceMapper.updateById(source);
			throw new IllegalStateException("邮箱只读同步失败：" + limit(safeError(ex), 240), ex);
		}
	}

	private SyncCount collect(WorkSourceEntity source, JsonNode config, Long tenantId, Long userId, Long orgId)
			throws Exception {
		String protocol = source.getProtocolType().toUpperCase(Locale.ROOT);
		if (!"IMAP".equals(protocol) && !"POP3".equals(protocol)) {
			throw new IllegalArgumentException("当前只支持 IMAP/POP3 只读同步");
		}
		if (config.has("tls") && !config.path("tls").asBoolean(true)) {
			throw new IllegalArgumentException("生产邮箱连接必须启用 TLS");
		}
		String server = required(config, "server");
		String mailbox = required(config, "mailbox");
		String folderName = config.path("folder").asText("INBOX");
		int maxMessages = Math.max(1, Math.min(config.path("maxMessages").asInt(50), 200));
		String storeProtocol = "IMAP".equals(protocol) ? "imaps" : "pop3s";
		int port = config.path("port").asInt("IMAP".equals(protocol) ? 993 : 995);
		String password = resolveSecret(source.getSecretRef());

		Properties properties = new Properties();
		String prefix = "mail." + storeProtocol + ".";
		properties.setProperty(prefix + "ssl.enable", "true");
		properties.setProperty(prefix + "ssl.checkserveridentity", "true");
		properties.setProperty(prefix + "connectiontimeout", "10000");
		properties.setProperty(prefix + "timeout", "30000");
		try (Store store = Session.getInstance(properties).getStore(storeProtocol)) {
			store.connect(server, port, mailbox, password);
			try (Folder folder = store.getFolder(folderName)) {
				folder.open(Folder.READ_ONLY);
				int total = folder.getMessageCount();
				if (total <= 0) {
					return new SyncCount(0, 0, 0);
				}
				Message[] messages = folder.getMessages(Math.max(1, total - maxMessages + 1), total);
				int scanned = 0;
				int inserted = 0;
				int duplicates = 0;
				for (int index = messages.length - 1; index >= 0; index--) {
					Message message = messages[index];
					if (message.isSet(Flags.Flag.SEEN)) {
						continue;
					}
					LocalDateTime occurredAt = occurredAt(message);
					if (source.getLastSyncAt() != null && occurredAt.isBefore(source.getLastSyncAt().minusMinutes(5))) {
						continue;
					}
					scanned++;
					String externalHash = sha256(externalId(message, mailbox));
					boolean exists = evidenceMapper.selectCount(Wrappers.<WorkEvidenceEntity>lambdaQuery()
							.eq(WorkEvidenceEntity::getTenantId, tenantId)
							.eq(WorkEvidenceEntity::getSourceId, source.getId())
							.eq(WorkEvidenceEntity::getExternalIdHash, externalHash)) > 0;
					if (exists) {
						duplicates++;
						continue;
					}
					Sender sender = sender(message.getFrom());
					String excerpt = limit(extractText(message).replaceAll("\\s+", " ").trim(), MAX_EXCERPT);
					WorkEvidenceEntity evidence = new WorkEvidenceEntity();
					evidence.setSourceId(source.getId());
					evidence.setOrgId(orgId);
					evidence.setOwnerUserId(userId);
					evidence.setEvidenceType("EMAIL");
					evidence.setExternalIdHash(externalHash);
					evidence.setTitle(limit(decode(message.getSubject()), 500));
					evidence.setExcerpt(excerpt);
					evidence.setSenderDisplay(sender.display());
					evidence.setSenderHash(sender.hash());
					evidence.setOccurredAt(occurredAt);
					evidence.setContentHash(sha256(evidence.getTitle() + "\n" + excerpt));
					evidence.setMetadataJson(objectMapper.writeValueAsString(
							java.util.Map.of("protocol", protocol, "folder", folderName)));
					evidence.setCapturedAt(LocalDateTime.now());
					evidence.setCreateTime(LocalDateTime.now());
					evidence.setTenantId(tenantId);
					evidenceMapper.insert(evidence);
					inserted++;
				}
				return new SyncCount(scanned, inserted, duplicates);
			}
		}
	}

	private String extractText(Part part) throws Exception {
		String disposition = part.getDisposition();
		if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
			return "";
		}
		if (part.isMimeType("text/plain")) {
			return String.valueOf(part.getContent());
		}
		if (part.isMimeType("text/html")) {
			return Jsoup.parse(String.valueOf(part.getContent())).text();
		}
		if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			StringBuilder text = new StringBuilder();
			for (int index = 0; index < multipart.getCount() && text.length() < MAX_EXCERPT; index++) {
				BodyPart bodyPart = multipart.getBodyPart(index);
				if (!Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
					text.append(extractText(bodyPart)).append(' ');
				}
			}
			return text.toString();
		}
		return "";
	}

	private Sender sender(Address[] addresses) throws Exception {
		if (addresses == null || addresses.length == 0) {
			return new Sender("未知发件人", null);
		}
		Address address = addresses[0];
		if (address instanceof InternetAddress internetAddress) {
			String email = internetAddress.getAddress();
			String personal = internetAddress.getPersonal();
			String display = personal == null || personal.isBlank() ? maskEmail(email) : decode(personal);
			return new Sender(limit(display, 255), email == null ? null : sha256(email.toLowerCase(Locale.ROOT)));
		}
		String value = address.toString();
		return new Sender(limit(value, 255), sha256(value));
	}

	private String externalId(Message message, String mailbox) throws Exception {
		String[] header = message.getHeader("Message-ID");
		if (header != null && header.length > 0 && header[0] != null && !header[0].isBlank()) {
			return header[0];
		}
		return mailbox + ":" + occurredAt(message) + ":" + decode(message.getSubject());
	}

	private LocalDateTime occurredAt(Message message) throws Exception {
		Date value = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
		return value == null ? LocalDateTime.now() : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
	}

	private String resolveSecret(String secretRef) {
		if (secretRef == null || !secretRef.startsWith("env://")) {
			throw new IllegalArgumentException("邮箱 Secret 必须使用 env:// 引用");
		}
		String name = secretRef.substring("env://".length());
		if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("邮箱 Secret 环境变量名不合法");
		}
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("邮箱 Secret 环境变量未配置");
		}
		return value;
	}

	private String required(JsonNode config, String field) {
		String value = config.path(field).asText();
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("邮箱配置缺少 " + field);
		}
		return value;
	}

	private String decode(String value) throws Exception {
		return value == null || value.isBlank() ? "(无主题)" : MimeUtility.decodeText(value);
	}

	private String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return "未知发件人";
		}
		String[] parts = email.split("@", 2);
		String local = parts[0];
		return (local.length() <= 2 ? "**" : local.substring(0, 2) + "***") + "@" + parts[1];
	}

	private String safeError(Exception ex) {
		return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String limit(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private record Sender(String display, String hash) {
	}

	private record SyncCount(int scanned, int inserted, int duplicates) {
	}

}
