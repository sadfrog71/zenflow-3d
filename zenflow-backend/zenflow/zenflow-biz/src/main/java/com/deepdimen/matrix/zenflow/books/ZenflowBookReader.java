package com.deepdimen.matrix.zenflow.books;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Public published-book catalog plus explicitly opted-in private reading-state sync. */
@Service
public class ZenflowBookReader {
	private final JdbcTemplate jdbc;
	private final ZenflowProperties product;
	private final ZenflowConsumerAccess access;
	private final TransactionTemplate tx;
	private final ObjectMapper json;

	public ZenflowBookReader(JdbcTemplate jdbc, ZenflowProperties product, ZenflowConsumerAccess access,
			PlatformTransactionManager manager, ObjectMapper json) {
		this.jdbc = jdbc;
		this.product = product;
		this.access = access;
		this.tx = new TransactionTemplate(manager);
		this.json = json;
	}

	public Map<String, Object> catalog() {
		var rows = jdbc.queryForList("SELECT b.id AS book_id,b.title,b.author,b.publisher,b.source_language,b.source_version_label,b.category,b.summary,b.source_url,t.id AS translation_id,t.target_language,t.revision_no,t.is_excerpt,t.excerpt_label,t.published_at,s.id AS source_version_id,s.version_no AS source_version_no "
				+ "FROM zf_book b JOIN zf_book_translation t ON t.book_id=b.id AND t.tenant_id=b.tenant_id AND t.status='PUBLISHED' "
				+ "JOIN zf_book_source_version s ON s.id=t.source_version_id AND s.tenant_id=t.tenant_id AND s.source_reviewed=1 "
				+ "WHERE b.tenant_id=? AND b.state='PUBLISHED' AND b.authorization_verified=1 AND b.permission_display=1 AND (b.license_expires_on IS NULL OR b.license_expires_on>=CURRENT_DATE) "
				+ "ORDER BY b.category,b.title,t.target_language,t.published_at DESC,t.id DESC", tenant());
		var books = new LinkedHashMap<String, Map<String, Object>>();
		for (var row : rows) {
			String bookId = row.get("book_id").toString();
			var book = books.computeIfAbsent(bookId, ignored -> publicBook(row));
			@SuppressWarnings("unchecked") var releases = (List<Map<String, Object>>) book.get("releases");
			var release = new LinkedHashMap<String, Object>();
			release.put("id", row.get("translation_id").toString());
			release.put("sourceVersionId", row.get("source_version_id").toString());
			release.put("sourceVersionNo", row.get("source_version_no"));
			release.put("targetLanguage", row.get("target_language"));
			release.put("revisionNo", row.get("revision_no"));
			release.put("isExcerpt", flag(row.get("is_excerpt")));
			release.put("excerptLabel", row.get("excerpt_label"));
			release.put("availableLanguages", java.util.stream.Stream.of(row.get("source_language"), row.get("target_language")).distinct().toList());
			release.put("versionKey", row.get("source_version_id") + "-" + row.get("translation_id"));
			releases.add(release);
		}
		return Map.of("items", new ArrayList<>(books.values()));
	}

	public Map<String, Object> publishedVersion(String bookId, String translationId, String language) {
		return publishedVersion(bookId, translationId, language, null);
	}

	public Map<String, Object> publishedVersion(String bookId, String translationId, String language, Integer chapterNo) {
		if (chapterNo != null && chapterNo < 1) bad("BOOK_CHAPTER_INVALID");
		long book = id(bookId), translation = id(translationId);
		var rows = jdbc.queryForList("SELECT b.id AS book_id,b.title,b.author,b.publisher,b.source_language,b.source_version_label,b.category,b.summary,b.source_url,b.license_name,b.rights_holder AS license_holder,b.license_term,b.license_expires_on,t.id AS translation_id,t.target_language,t.revision_no,t.is_excerpt,t.excerpt_label,t.source_version_id,t.rights_snapshot,t.translation_origin,t.translation_model_note,t.translator_note,s.version_no AS source_version_no "
				+ "FROM zf_book b JOIN zf_book_translation t ON t.book_id=b.id AND t.tenant_id=b.tenant_id AND t.status='PUBLISHED' "
				+ "JOIN zf_book_source_version s ON s.id=t.source_version_id AND s.tenant_id=t.tenant_id AND s.source_reviewed=1 "
				+ "WHERE b.id=? AND t.id=? AND b.tenant_id=? AND b.state='PUBLISHED' AND b.authorization_verified=1 AND b.permission_display=1 AND (b.license_expires_on IS NULL OR b.license_expires_on>=CURRENT_DATE)",
				book, translation, tenant());
		if (rows.size() != 1) missing("BOOK_VERSION_NOT_AVAILABLE");
		var row = rows.get(0);
		String sourceLanguage = row.get("source_language").toString();
		String targetLanguage = row.get("target_language").toString();
		boolean vernacularEdition = sourceLanguage.equals(targetLanguage);
		boolean sourceView = sourceLanguage.equals(language) && !vernacularEdition;
		if (!sourceLanguage.equals(language) && !targetLanguage.equals(language)) bad("BOOK_LANGUAGE_NOT_AVAILABLE");
		var chapters = jdbc.queryForList("SELECT sc.id,sc.chapter_no,sc.title,sc.source_text,tc.translation_text,tc.commentary,tc.annotations "
				+ "FROM zf_book_translation_chapter tc JOIN zf_book_source_chapter sc ON sc.id=tc.source_chapter_id AND sc.tenant_id=tc.tenant_id "
				+ "WHERE tc.tenant_id=? AND tc.translation_id=? AND tc.included=1 AND tc.translation_state='COMPLETE' AND tc.review_state='APPROVED' ORDER BY tc.chapter_no",
				tenant(), translation);
		if (chapters.isEmpty()) missing("BOOK_VERSION_NOT_AVAILABLE");
		var result = new LinkedHashMap<String, Object>();
		result.put("id", row.get("book_id").toString());
		result.put("versionId", row.get("translation_id").toString());
		result.put("sourceVersionId", row.get("source_version_id").toString());
		result.put("versionKey", row.get("source_version_id") + "-" + row.get("translation_id"));
		result.put("versionLabel", row.get("source_version_label"));
		result.put("sourceVersionNo", row.get("source_version_no"));
		result.put("revisionNo", row.get("revision_no"));
		result.put("title", row.get("title"));
		result.put("author", row.get("author"));
		result.put("publisher", row.get("publisher"));
		result.put("category", row.get("category"));
		result.put("summary", row.get("summary"));
		result.put("sourceLanguage", sourceLanguage);
		result.put("targetLanguage", targetLanguage);
		result.put("language", language);
		result.put("isExcerpt", flag(row.get("is_excerpt")));
		result.put("excerptLabel", row.get("excerpt_label"));
		result.put("sourceUrl", row.get("source_url"));
		result.put("licenseName", row.get("license_name"));
		result.put("licenseHolder", row.get("license_holder"));
		result.put("licenseTerm", row.get("license_term"));
		result.put("licenseExpiresOn", row.get("license_expires_on") == null ? "" : row.get("license_expires_on").toString());
		result.put("translationOrigin", row.get("translation_origin"));
		result.put("modelNote", Objects.toString(row.get("translation_model_note"), ""));
		result.put("translatorNote", Objects.toString(row.get("translator_note"), ""));
		String modelNote = Objects.toString(row.get("translation_model_note"), "").trim();
		String storedNote = Objects.toString(row.get("translator_note"), "").trim();
		result.put("translationNote", sourceView ? "原文版本"
				: modelNote.isBlank() ? "AI 辅助译注，此间整理" : "AI 辅助译注（" + modelNote + "），此间整理");
		result.put("editorNote", storedNote.isBlank() ? "正文、译文、夹注与章节解读分别标注；读者可通过来源信息查看版本与许可。" : storedNote);
		var selected = chapters.stream().filter(chapter -> chapterNo == null || ((Number) chapter.get("chapter_no")).intValue() == chapterNo).toList();
		if (selected.isEmpty()) missing("BOOK_CHAPTER_NOT_AVAILABLE");
		if (chapterNo != null) result.put("chapterSummaries", chapters.stream().map(chapter -> Map.of(
				"id", chapter.get("id").toString(), "chapterNo", ((Number) chapter.get("chapter_no")).intValue(), "title", Objects.toString(chapter.get("title"), ""))).toList());
		result.put("chapters", selected.stream().map(chapter -> chapterProjection(chapter, sourceView, vernacularEdition)).toList());
		return result;
	}

	public Map<String, Object> state() {
		return tx.execute(ignored -> state(access.capsuleIdentity()));
	}

	public Map<String, Object> syncState(Map<String, Object> body) {
		if (body == null || !body.keySet().equals(Set.of("syncEnabled", "progress", "bookmarks"))
				|| !(body.get("syncEnabled") instanceof Boolean) || !(body.get("progress") instanceof List<?>)
				|| !(body.get("bookmarks") instanceof List<?>)) bad("BOOK_READER_STATE_INVALID");
		var identity = access.capsuleIdentity();
		boolean enabled = Boolean.TRUE.equals(body.get("syncEnabled"));
		List<?> progress = (List<?>) body.get("progress"), bookmarks = (List<?>) body.get("bookmarks");
		if (progress.size() > 100 || bookmarks.size() > 1000) bad("BOOK_READER_STATE_LIMIT_EXCEEDED");
		return tx.execute(ignored -> {
			ensurePreference(identity);
			if (!enabled) {
				clear(identity);
				jdbc.update("UPDATE zf_book_reader_preference SET sync_enabled=0,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=?", identity.tenantId(), identity.ownerUserId());
				return state(identity);
			}
			var normalizedProgress = progress.stream().map(this::stateAnchor).toList();
			var normalizedBookmarks = bookmarks.stream().map(this::stateAnchor).toList();
			ensureNoDuplicates(normalizedProgress, "BOOK_PROGRESS_DUPLICATE", false);
			ensureNoDuplicates(normalizedBookmarks, "BOOK_BOOKMARK_DUPLICATE", true);
			for (var anchor : normalizedProgress) requirePublishedAnchor(anchor);
			for (var anchor : normalizedBookmarks) requirePublishedAnchor(anchor);
			clear(identity);
			jdbc.update("UPDATE zf_book_reader_preference SET sync_enabled=1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=?", identity.tenantId(), identity.ownerUserId());
			for (var anchor : normalizedProgress) jdbc.update("INSERT INTO zf_book_reader_progress(tenant_id,owner_user_id,book_id,translation_id,language,chapter_no,paragraph_no) VALUES(?,?,?,?,?,?,?)",
					identity.tenantId(), identity.ownerUserId(), anchor.get("bookId"), anchor.get("versionId"), anchor.get("language"), anchor.get("chapterNo"), anchor.get("paragraphNo"));
			for (var anchor : normalizedBookmarks) jdbc.update("INSERT INTO zf_book_bookmark(id,tenant_id,owner_user_id,book_id,translation_id,language,chapter_no,paragraph_no) VALUES(?,?,?,?,?,?,?,?)",
					IdWorker.getId(), identity.tenantId(), identity.ownerUserId(), anchor.get("bookId"), anchor.get("versionId"), anchor.get("language"), anchor.get("chapterNo"), anchor.get("paragraphNo"));
			return state(identity);
		});
	}

	public Map<String, Object> clearState() {
		var identity = access.capsuleIdentity();
		return tx.execute(ignored -> {
			ensurePreference(identity);
			clear(identity);
			jdbc.update("UPDATE zf_book_reader_preference SET sync_enabled=0,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=?", identity.tenantId(), identity.ownerUserId());
			return state(identity);
		});
	}

	private Map<String, Object> state(CapsuleIdentity identity) {
		var preferences = jdbc.queryForList("SELECT sync_enabled FROM zf_book_reader_preference WHERE tenant_id=? AND owner_user_id=?",
				identity.tenantId(), identity.ownerUserId());
		boolean enabled = !preferences.isEmpty() && flag(preferences.get(0).get("sync_enabled"));
		var progress = new ArrayList<Map<String, Object>>();
		var bookmarks = new ArrayList<Map<String, Object>>();
		if (enabled) {
			progress.addAll(jdbc.queryForList("SELECT book_id,translation_id,language,chapter_no,paragraph_no FROM zf_book_reader_progress WHERE tenant_id=? AND owner_user_id=? ORDER BY updated_at DESC",
					identity.tenantId(), identity.ownerUserId()).stream().map(this::stateProjection).toList());
			bookmarks.addAll(jdbc.queryForList("SELECT book_id,translation_id,language,chapter_no,paragraph_no FROM zf_book_bookmark WHERE tenant_id=? AND owner_user_id=? ORDER BY created_at DESC,id DESC",
					identity.tenantId(), identity.ownerUserId()).stream().map(this::stateProjection).toList());
		}
		var result = new LinkedHashMap<String, Object>();
		result.put("syncEnabled", enabled);
		result.put("progress", progress);
		result.put("bookmarks", bookmarks);
		return result;
	}

	private Map<String, Object> stateAnchor(Object raw) {
		if (!(raw instanceof Map<?, ?> item) || !item.keySet().equals(Set.of("bookId", "versionId", "language", "chapterNo", "paragraphNo"))) bad("BOOK_READER_STATE_INVALID");
		var values = map((Map<?, ?>) raw);
		long book = id(Objects.toString(values.get("bookId"), null));
		long version = id(Objects.toString(values.get("versionId"), null));
		String language = text(values.get("language"), 16);
		if (!Set.of("en-US", "zh-CN").contains(language)) bad("BOOK_READER_STATE_INVALID");
		int chapter = positiveInteger(values.get("chapterNo"), true);
		int paragraph = positiveInteger(values.get("paragraphNo"), false);
		var result = new LinkedHashMap<String, Object>();
		result.put("bookId", book);
		result.put("versionId", version);
		result.put("language", language);
		result.put("chapterNo", chapter);
		result.put("paragraphNo", paragraph);
		return result;
	}

	private void requirePublishedAnchor(Map<String, Object> anchor) {
		var rows = jdbc.queryForList("SELECT s.id,t.target_language,b.source_language FROM zf_book_translation t JOIN zf_book b ON b.id=t.book_id AND b.tenant_id=t.tenant_id JOIN zf_book_source_version s ON s.id=t.source_version_id AND s.tenant_id=t.tenant_id JOIN zf_book_translation_chapter tc ON tc.translation_id=t.id AND tc.tenant_id=t.tenant_id AND tc.chapter_no=? AND tc.included=1 AND tc.translation_state='COMPLETE' AND tc.review_state='APPROVED' WHERE t.id=? AND t.book_id=? AND t.tenant_id=? AND t.status='PUBLISHED' AND b.state='PUBLISHED' AND b.authorization_verified=1 AND b.permission_display=1 AND (b.license_expires_on IS NULL OR b.license_expires_on>=CURRENT_DATE)",
				anchor.get("chapterNo"), anchor.get("versionId"), anchor.get("bookId"), tenant());
		if (rows.size() != 1) bad("BOOK_READER_STATE_VERSION_INVALID");
		var row = rows.get(0);
		String language = anchor.get("language").toString();
		if (!language.equals(row.get("source_language")) && !language.equals(row.get("target_language"))) bad("BOOK_READER_STATE_LANGUAGE_INVALID");
	}

	private void ensurePreference(CapsuleIdentity identity) {
		int count = jdbc.update("INSERT IGNORE INTO zf_book_reader_preference(tenant_id,owner_user_id,sync_enabled) VALUES(?,?,0)", identity.tenantId(), identity.ownerUserId());
		if (count < 0) throw new IllegalStateException("BOOK_READER_PREFERENCE_UNAVAILABLE");
	}

	private void clear(CapsuleIdentity identity) {
		jdbc.update("DELETE FROM zf_book_reader_progress WHERE tenant_id=? AND owner_user_id=?", identity.tenantId(), identity.ownerUserId());
		jdbc.update("DELETE FROM zf_book_bookmark WHERE tenant_id=? AND owner_user_id=?", identity.tenantId(), identity.ownerUserId());
	}

	private Map<String, Object> publicBook(Map<String, Object> row) {
		var result = new LinkedHashMap<String, Object>();
		result.put("id", row.get("book_id").toString());
		result.put("title", row.get("title"));
		result.put("author", row.get("author"));
		result.put("publisher", row.get("publisher"));
		result.put("sourceLanguage", row.get("source_language"));
		result.put("sourceVersion", row.get("source_version_label"));
		result.put("category", row.get("category"));
		result.put("summary", row.get("summary"));
		result.put("releases", new ArrayList<Map<String, Object>>());
		return result;
	}

	private Map<String, Object> chapterProjection(Map<String, Object> row, boolean sourceView, boolean vernacularEdition) {
		var item = new LinkedHashMap<String, Object>();
		item.put("id", row.get("id").toString());
		item.put("chapterNo", row.get("chapter_no"));
		item.put("title", row.get("title"));
		item.put("sourceText", row.get("source_text"));
		item.put("translationText", row.get("translation_text"));
		item.put("text", sourceView || vernacularEdition ? row.get("source_text") : row.get("translation_text"));
		item.put("annotationLayer", vernacularEdition ? "source" : "translation");
		item.put("commentary", sourceView ? "" : row.get("commentary"));
		item.put("annotations", sourceView ? List.of() : readJsonList(row.get("annotations")));
		return item;
	}

	private List<?> readJsonList(Object value) {
		if (value == null) return List.of();
		try { return json.readValue(value.toString(), List.class); }
		catch (Exception invalid) { return List.of(); }
	}

	private Map<String, Object> stateProjection(Map<String, Object> row) {
		var result = new LinkedHashMap<String, Object>();
		result.put("bookId", row.get("book_id").toString());
		result.put("versionId", row.get("translation_id").toString());
		result.put("language", row.get("language"));
		result.put("chapterNo", row.get("chapter_no"));
		result.put("paragraphNo", row.get("paragraph_no"));
		return result;
	}

	private int positiveInteger(Object value, boolean requirePositive) {
		if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) bad("BOOK_READER_STATE_INVALID");
		int result = ((Number) value).intValue();
		if (result < (requirePositive ? 1 : 0) || result > 1_000_000) bad("BOOK_READER_STATE_INVALID");
		return result;
	}

	private static boolean flag(Object value) {
		if (value instanceof Boolean bool) return bool;
		if (value instanceof Number number) return number.intValue() == 1;
		return "1".equals(value);
	}

	private String text(Object value, int max) {
		if (!(value instanceof String text) || text.isBlank() || text.length() > max) bad("BOOK_READER_STATE_INVALID");
		return ((String) value).trim();
	}

	private void ensureNoDuplicates(List<Map<String, Object>> items, String code, boolean bookmark) {
		var keys = new HashSet<String>();
		for (var item : items) {
			String key = item.get("bookId") + ":" + item.get("versionId") + ":" + item.get("language")
					+ (bookmark ? ":" + item.get("chapterNo") + ":" + item.get("paragraphNo") : "");
			if (!keys.add(key)) bad(code);
		}
	}

	private Map<String, Object> map(Map<?, ?> values) {
		var result = new LinkedHashMap<String, Object>();
		values.forEach((key, value) -> result.put(key.toString(), value));
		return result;
	}

	private long tenant() { return product.getTenantId(); }
	private long id(String value) { try { if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException(); return Long.parseLong(value); } catch (Exception invalid) { throw error(HttpStatus.BAD_REQUEST, "BOOK_ID_INVALID"); } }
	private static void bad(String code) { throw error(HttpStatus.BAD_REQUEST, code); }
	private static void missing(String code) { throw error(HttpStatus.NOT_FOUND, code); }
	private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
}
