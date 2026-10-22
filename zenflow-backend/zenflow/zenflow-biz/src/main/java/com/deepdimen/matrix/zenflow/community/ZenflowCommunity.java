package com.deepdimen.matrix.zenflow.community;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ZenflowCommunity {

	private final JdbcTemplate jdbc;

	private final ZenflowConsumerAccess consumer;

	private final ZenflowAdminAccess admin;

	private final TransactionTemplate tx;

	public ZenflowCommunity(JdbcTemplate jdbc, ZenflowConsumerAccess consumer, ZenflowAdminAccess admin,
			PlatformTransactionManager manager) {
		this.jdbc = jdbc;
		this.consumer = consumer;
		this.admin = admin;
		this.tx = new TransactionTemplate(manager);
	}

	public Map<String, Object> upload(Map<String, Object> body) {
		var actor = consumer.require();
		var image = ZenflowCommunityImages.decode(text(body, "base64", 7_000_000, true));
		cleanup();
		long id = IdWorker.getId();
		jdbc.update(
				"INSERT INTO zf_community_attachment(id,tenant_id,owner_user_id,mime,data,thumbnail,width,height) VALUES(?,?,?,?,?,?,?,?)",
				id, actor.getTenantId(), actor.getId(), image.mime(), image.bytes(), image.thumbnail(), image.width(),
				image.height());
		return Map.of("id", Long.toString(id), "thumbnail", image.thumbnail());
	}

	public void discard(String key) {
		var actor = consumer.require();
		jdbc.update(
				"DELETE FROM zf_community_attachment WHERE id=? AND tenant_id=? AND owner_user_id=? AND post_id IS NULL",
				id(key), actor.getTenantId(), actor.getId());
	}

	@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000)
	public void cleanup() {
		jdbc.update("DELETE FROM zf_community_attachment WHERE post_id IS NULL AND created_at<?",
				java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400)));
	}

	public Map<String, Object> submit(Map<String, Object> body) {
		var actor = consumer.require();
		String title = text(body, "title", 120, true), content = text(body, "text", 50000, true),
				category = text(body, "category", 16, true);
		if (!Set.of("ESSAY", "SHORT", "POEM").contains(category))
			bad("COMMUNITY_CATEGORY_INVALID");
		Object raw = body.get("attachmentIds");
		if (!(raw instanceof List<?> attachments) || attachments.size() > 6)
			throw fail(HttpStatus.BAD_REQUEST, "COMMUNITY_IMAGES_LIMIT");
		var ids = attachments.stream().map(value -> id(value.toString())).toList();
		if (new HashSet<>(ids).size() != ids.size())
			bad("COMMUNITY_IMAGES_INVALID");
		return tx.execute(ignored -> {
			long key = IdWorker.getId();
			for (long attachment : ids) {
				var rows = jdbc.queryForList(
						"SELECT id FROM zf_community_attachment WHERE id=? AND tenant_id=? AND owner_user_id=? AND post_id IS NULL FOR UPDATE",
						attachment, actor.getTenantId(), actor.getId());
				if (rows.size() != 1)
					bad("COMMUNITY_IMAGE_NOT_OWNED");
			}
			String name = actor.getNickname();
			if (name == null || name.isBlank())
				name = "此间的朋友";
			jdbc.update(
					"INSERT INTO zf_community_post(id,tenant_id,owner_user_id,author_name,title,content,category) VALUES(?,?,?,?,?,?,?)",
					key, actor.getTenantId(), actor.getId(), name, title, content, category);
			for (long attachment : ids)
				jdbc.update("UPDATE zf_community_attachment SET post_id=? WHERE id=?", key, attachment);
			return project(row(actor.getTenantId(), key), true);
		});
	}

	public Map<String, Object> list(boolean mine, String category, String before, int limit) {
		var actor = consumer.require();
		return listing(actor, mine ? "owner_user_id=?" : "state='APPROVED'", mine ? actor.getId() : null, category,
				before, limit, false);
	}

	public Map<String, Object> adminList(String state, String before, int limit) {
		var actor = admin.require();
		if (state == null)
			state = "PENDING";
		if (!Set.of("ALL", "PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "REMOVED").contains(state))
			bad("COMMUNITY_STATE_INVALID");
		return listing(actor, state.equals("ALL") ? "1=1" : "state=?", state.equals("ALL") ? null : state, null, before,
				limit, true);
	}

	private Map<String, Object> listing(MatrixUser actor, String condition, Object extra, String category,
			String before, int limit, boolean administrator) {
		int size = Math.max(1, Math.min(30, limit));
		var args = new ArrayList<Object>();
		args.add(actor.getTenantId());
		if (extra != null)
			args.add(extra);
		String sql = "SELECT * FROM zf_community_post WHERE tenant_id=? AND " + condition;
		if (category != null && !category.equals("ALL")) {
			if (!Set.of("ESSAY", "SHORT", "POEM").contains(category))
				bad("COMMUNITY_CATEGORY_INVALID");
			sql += " AND category=?";
			args.add(category);
		}
		if (before != null && !before.isBlank()) {
			sql += " AND id<?";
			args.add(id(before));
		}
		sql += " ORDER BY id DESC LIMIT ?";
		args.add(size + 1);
		var rows = jdbc.queryForList(sql, args.toArray());
		boolean more = rows.size() > size;
		var page = rows.stream().limit(size).toList();
		var attachments = pageImages(actor.getTenantId(), page);
		var items = page.stream().map(r -> {
			var item = project(r, actor.getId().equals(((Number) r.get("owner_user_id")).longValue()),
					attachments.getOrDefault(r.get("id").toString(), List.of()));
			if (administrator)
				item.put("reviewNote", r.get("review_note"));
			return item;
		}).toList();
		return Map.of("items", items, "hasMore", more, "nextCursor",
				more ? rows.get(size - 1).get("id").toString() : "");
	}

	public Map<String, Object> detail(String key) {
		var actor = consumer.require();
		var row = row(actor.getTenantId(), id(key));
		boolean owner = actor.getId().equals(((Number) row.get("owner_user_id")).longValue());
		if (!owner && !"APPROVED".equals(row.get("state")))
			throw fail(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND");
		return project(row, owner);
	}

	public Map<String, Object> withdraw(String key, int version) {
		var actor = consumer.require();
		return tx.execute(ignored -> {
			var row = lock(actor.getTenantId(), id(key));
			if (!actor.getId().equals(((Number) row.get("owner_user_id")).longValue()))
				throw fail(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND");
			checkVersion(row, version);
			if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(row.get("state")))
				throw fail(HttpStatus.CONFLICT, "COMMUNITY_STATE_CONFLICT");
			jdbc.update(
					"UPDATE zf_community_post SET state='WITHDRAWN',featured=FALSE,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
					id(key), actor.getTenantId());
			return project(row(actor.getTenantId(), id(key)), true);
		});
	}

	public Map<String, Object> moderate(String key, Map<String, Object> body) {
		var actor = admin.require();
		String action = text(body, "action", 16, true);
		int version = number(body.get("expectedVersion"));
		String note = text(body, "reviewNote", 500, false);
		boolean featured = Boolean.TRUE.equals(body.get("featured"));
		return tx.execute(ignored -> {
			var row = lock(actor.getTenantId(), id(key));
			checkVersion(row, version);
			String current = row.get("state").toString();
			String next = switch (action) {
				case "APPROVE" -> {
					if (!current.equals("PENDING"))
						throw fail(HttpStatus.CONFLICT, "COMMUNITY_STATE_CONFLICT");
					yield "APPROVED";
				}
				case "REJECT" -> {
					if (!current.equals("PENDING"))
						throw fail(HttpStatus.CONFLICT, "COMMUNITY_STATE_CONFLICT");
					yield "REJECTED";
				}
				case "TAKEDOWN" -> {
					if (!current.equals("APPROVED"))
						throw fail(HttpStatus.CONFLICT, "COMMUNITY_STATE_CONFLICT");
					yield "REMOVED";
				}
				case "FEATURE" -> {
					if (!current.equals("APPROVED"))
						throw fail(HttpStatus.CONFLICT, "COMMUNITY_STATE_CONFLICT");
					yield "APPROVED";
				}
				default -> throw fail(HttpStatus.BAD_REQUEST, "COMMUNITY_ACTION_INVALID");
			};
			jdbc.update(
					"UPDATE zf_community_post SET state=?,featured=?,review_note=?,reviewed_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
					next, next.equals("APPROVED") && featured, note, actor.getId(), id(key), actor.getTenantId());
			return project(row(actor.getTenantId(), id(key)), true);
		});
	}

	public Map<String, Object> image(String key, boolean administrator) {
		var actor = administrator ? admin.require() : consumer.require();
		var images = jdbc.query(
				"SELECT a.owner_user_id,a.mime,a.data,p.state FROM zf_community_attachment a LEFT JOIN zf_community_post p ON p.id=a.post_id AND p.tenant_id=a.tenant_id WHERE a.id=? AND a.tenant_id=? AND (?=TRUE OR a.owner_user_id=? OR p.state='APPROVED')",
				(rs, n) -> {
					var image = new HashMap<String, Object>();
					image.put("owner_user_id", rs.getLong("owner_user_id"));
					image.put("mime", rs.getString("mime"));
					image.put("data", rs.getBytes("data"));
					image.put("state", rs.getString("state"));
					return image;
				}, id(key), actor.getTenantId(), administrator, actor.getId());
		if (images.size() != 1)
			throw fail(HttpStatus.NOT_FOUND, "COMMUNITY_IMAGE_NOT_FOUND");
		var row = images.get(0);
		boolean owner = actor.getId().equals(((Number) row.get("owner_user_id")).longValue());
		if (!administrator && !owner && !"APPROVED".equals(row.get("state")))
			throw fail(HttpStatus.NOT_FOUND, "COMMUNITY_IMAGE_NOT_FOUND");
		return Map.of("id", key, "uri",
				"data:" + row.get("mime") + ";base64," + Base64.getEncoder().encodeToString((byte[]) row.get("data")));
	}

	private Map<String, Object> row(long tenant, long key) {
		var rows = jdbc.queryForList("SELECT * FROM zf_community_post WHERE tenant_id=? AND id=?", tenant, key);
		if (rows.size() != 1)
			throw fail(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND");
		return rows.get(0);
	}

	private Map<String, Object> lock(long tenant, long key) {
		var rows = jdbc.queryForList("SELECT * FROM zf_community_post WHERE tenant_id=? AND id=? FOR UPDATE", tenant,
				key);
		if (rows.size() != 1)
			throw fail(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND");
		return rows.get(0);
	}

	private Map<String, Object> project(Map<String, Object> row, boolean owner) {
		return project(row, owner, jdbc.query(
				"SELECT id,thumbnail,width,height FROM zf_community_attachment WHERE tenant_id=? AND post_id=? ORDER BY id",
				(rs, n) -> Map.of("id", rs.getString("id"), "thumbnail", rs.getString("thumbnail"), "width",
						rs.getInt("width"), "height", rs.getInt("height")),
				row.get("tenant_id"), row.get("id")));
	}

	private Map<String, Object> project(Map<String, Object> row, boolean owner, List<Map<String, Object>> images) {
		var result = new LinkedHashMap<String, Object>();
		result.put("id", row.get("id").toString());
		result.put("title", row.get("title"));
		result.put("text", row.get("content"));
		result.put("author", row.get("author_name"));
		result.put("category", row.get("category"));
		result.put("state", row.get("state"));
		result.put("featured", row.get("featured"));
		result.put("version", row.get("version"));
		result.put("createdAt", row.get("created_at").toString());
		result.put("isOwner", owner);
		if (owner)
			result.put("reviewNote", row.get("review_note"));
		result.put("images", images);
		return result;
	}

	private Map<String, List<Map<String, Object>>> pageImages(long tenant, List<Map<String, Object>> posts) {
		var result = new HashMap<String, List<Map<String, Object>>>();
		if (posts.isEmpty())
			return result;
		var args = new ArrayList<Object>();
		args.add(tenant);
		for (var post : posts)
			args.add(post.get("id"));
		String placeholders = String.join(",", Collections.nCopies(posts.size(), "?"));
		jdbc.query(
				"SELECT post_id,id,thumbnail,width,height FROM zf_community_attachment WHERE tenant_id=? AND post_id IN ("
						+ placeholders + ") ORDER BY id",
				(org.springframework.jdbc.core.RowCallbackHandler) rs -> {
					result.computeIfAbsent(rs.getString("post_id"), ignored -> new ArrayList<>())
						.add(Map.of("id", rs.getString("id"), "thumbnail", rs.getString("thumbnail"), "width",
								rs.getInt("width"), "height", rs.getInt("height")));
				}, args.toArray());
		return result;
	}

	private static void checkVersion(Map<String, Object> row, int version) {
		if (((Number) row.get("version")).intValue() != version)
			throw fail(HttpStatus.CONFLICT, "COMMUNITY_VERSION_CONFLICT");
	}

	private static int number(Object raw) {
		if (!(raw instanceof Number value) || value.doubleValue() != value.intValue() || value.intValue() < 1)
			throw fail(HttpStatus.BAD_REQUEST, "COMMUNITY_VERSION_REQUIRED");
		return value.intValue();
	}

	private static long id(String raw) {
		try {
			return Long.parseLong(raw);
		}
		catch (RuntimeException invalid) {
			throw fail(HttpStatus.BAD_REQUEST, "COMMUNITY_ID_INVALID");
		}
	}

	private static String text(Map<String, Object> body, String key, int limit, boolean required) {
		Object value = body.get(key);
		if (value == null && !required)
			return "";
		if (!(value instanceof String text) || text.length() > limit || (required && text.isBlank()))
			throw fail(HttpStatus.BAD_REQUEST, "COMMUNITY_INPUT_INVALID");
		return text.trim();
	}

	private static void bad(String code) {
		throw fail(HttpStatus.BAD_REQUEST, code);
	}

	private static ResponseStatusException fail(HttpStatus status, String code) {
		return new ResponseStatusException(status, code);
	}

}
