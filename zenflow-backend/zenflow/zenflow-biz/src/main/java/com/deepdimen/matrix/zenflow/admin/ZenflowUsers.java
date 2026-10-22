package com.deepdimen.matrix.zenflow.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Administrative identity projection only; no chat, capsule, memory or OAuth subject
 * bodies.
 */
@Service
public class ZenflowUsers {

	private final JdbcTemplate jdbc;

	private final ZenflowProperties product;

	private final ZenflowAdminAccess access;

	private final TransactionTemplate tx;

	public ZenflowUsers(JdbcTemplate jdbc, ZenflowProperties product, ZenflowAdminAccess access,
			PlatformTransactionManager manager) {
		this.jdbc = jdbc;
		this.product = product;
		this.access = access;
		this.tx = new TransactionTemplate(manager);
	}

	public Map<String, Object> list(String query, String state, String before, int limit) {
		access.require();
		String keyword = query == null ? "" : query.trim();
		if (keyword.length() > 64)
			bad("INVALID_USER_QUERY");
		String status = state == null ? "ALL" : state;
		if (!Set.of("ALL", "ACTIVE", "DISABLED").contains(status))
			bad("INVALID_USER_STATE");
		int size = Math.max(1, Math.min(limit, 50));
		long cursor = before == null ? Long.MAX_VALUE : id(before);
		String filter = "SELECT u.user_id AS id,p.public_id AS publicId,u.nickname,u.name,u.email,(SELECT e.provider_email FROM zf_external_identity e WHERE e.tenant_id=u.tenant_id AND e.user_id=u.user_id AND e.provider_email_verified=1 ORDER BY e.last_login_at DESC,e.id LIMIT 1) AS identityEmail,CASE WHEN u.password IS NULL OR u.password='' THEN 0 ELSE 1 END AS emailLogin,u.lock_flag AS locked,u.auth_version AS version,DATE_FORMAT(u.create_time,'%Y-%m-%dT%H:%i:%sZ') AS createdAt FROM app_user u JOIN zf_profile p ON p.tenant_id=u.tenant_id AND p.owner_user_id=u.user_id WHERE u.tenant_id=? AND u.del_flag='0' AND u.user_id<?";
		var args = new ArrayList<Object>(List.of(product.getTenantId(), cursor));
		if (!status.equals("ALL")) {
			filter += " AND u.lock_flag=?";
			args.add(status.equals("ACTIVE") ? "0" : "1");
		}
		if (!keyword.isEmpty()) {
			String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
			filter += " AND (u.nickname LIKE ? ESCAPE '!' OR u.name LIKE ? ESCAPE '!' OR u.email LIKE ? ESCAPE '!' OR p.public_id LIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM zf_external_identity se WHERE se.tenant_id=u.tenant_id AND se.user_id=u.user_id AND se.provider_email_verified=1 AND se.provider_email LIKE ? ESCAPE '!'))";
			args.addAll(List.of(pattern, pattern, pattern, pattern, pattern));
		}
		filter += " ORDER BY u.user_id DESC LIMIT ?";
		args.add(size + 1);
		var rows = jdbc.queryForList(filter, args.toArray());
		boolean more = rows.size() > size;
		var items = new ArrayList<Map<String, Object>>();
		var visible = rows.subList(0, Math.min(size, rows.size()));
		var methods = new HashMap<String, List<String>>();
		if (!visible.isEmpty()) {
			var providerArgs = new ArrayList<Object>();
			providerArgs.add(product.getTenantId());
			visible.forEach(row -> providerArgs.add(row.get("id")));
			String placeholders = String.join(",", Collections.nCopies(visible.size(), "?"));
			var providers = jdbc
				.queryForList("SELECT user_id,provider FROM zf_external_identity WHERE tenant_id=? AND user_id IN ("
						+ placeholders + ") ORDER BY provider", providerArgs.toArray());
			providers.forEach(row -> methods.computeIfAbsent(row.get("user_id").toString(), key -> new ArrayList<>())
				.add(row.get("provider").toString()));
		}
		var stats=statistics(visible.stream().map(row->row.get("id").toString()).toList());
		for (var row : visible) {
			var item = new LinkedHashMap<String, Object>();
			item.put("id", row.get("id").toString());
			item.put("publicId", row.get("publicId"));
			String email=first(row.get("email"),row.get("identityEmail"));
			String display=first(row.get("nickname"),row.get("name"));
			if(display.isEmpty()&&!email.isEmpty())display=email.split("@",2)[0];
			item.put("displayName",display.isEmpty()?"未设置昵称":display);
			item.put("email",email.isEmpty()?null:email);
			item.put("state", "1".equals(row.get("locked")) ? "DISABLED" : "ACTIVE");
			item.put("version", row.get("version"));
			item.put("createdAt", row.get("createdAt"));
			item.put("membershipState", "NOT_CONFIGURED");
            item.putAll(stats.getOrDefault(row.get("id").toString(),Map.of()));
			var providers = new ArrayList<String>();
			if (row.get("email") != null && ((Number) row.get("emailLogin")).intValue() == 1)
				providers.add("EMAIL");
			providers.addAll(methods.getOrDefault(row.get("id").toString(), List.of()));
			item.put("loginMethods", providers.stream().distinct().toList());
			items.add(item);
		}
		var result = new LinkedHashMap<String, Object>();
		result.put("items", items);
		result.put("hasMore", more);
		result.put("nextCursor", more ? items.get(items.size() - 1).get("id") : null);
		return result;
	}

	public Map<String, Object> state(String userId, Map<String, Object> body) {
		var actor = access.require();
		if (!body.keySet().equals(Set.of("state", "expectedVersion")))
			bad("INVALID_USER_COMMAND");
		String state = String.valueOf(body.get("state"));
		if (!Set.of("ACTIVE", "DISABLED").contains(state))
			bad("INVALID_USER_STATE");
		if (!(body.get("expectedVersion") instanceof Number n) || n.doubleValue() != n.longValue() || n.longValue() < 1)
			bad("INVALID_USER_VERSION");
		long expected = ((Number) body.get("expectedVersion")).longValue(), user = id(userId);
		return tx.execute(ignored -> {
			var profile = jdbc.queryForList(
					"SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",
					product.getTenantId(), user);
			var rows = jdbc.queryForList(
					"SELECT auth_version,lock_flag FROM app_user WHERE tenant_id=? AND user_id=? AND del_flag='0' FOR UPDATE",
					product.getTenantId(), user);
			if (profile.size() != 1 || rows.size() != 1)
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
			long version = ((Number) rows.get(0).get("auth_version")).longValue();
			if (version != expected)
				throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_VERSION_CONFLICT");
			String flag = state.equals("ACTIVE") ? "0" : "1";
			if (flag.equals(rows.get(0).get("lock_flag")))
				return Map.of("id", userId, "state", state, "version", version);
			jdbc.update(
					"UPDATE app_user SET lock_flag=?,auth_version=auth_version+1,update_time=UTC_TIMESTAMP(),update_by=? WHERE tenant_id=? AND user_id=? AND auth_version=?",
					flag, actor.getId().toString(), product.getTenantId(), user, version);
			String event = Long.toString(IdWorker.getId());
			jdbc.update(
					"INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,state,next_attempt_at) VALUES(?,?,'AUTH_REVOKED','app_user',?,?,JSON_OBJECT('authVersion',?,'administratorId',?,'accountState',?),'PENDING',UTC_TIMESTAMP(3))",
					Long.parseLong(event), product.getTenantId(), userId, event, version + 1, actor.getId(), state);
			return Map.of("id", userId, "state", state, "version", version + 1);
		});
	}

    static String utc(Object value){if(value==null)return null;if(value instanceof java.time.LocalDateTime t)return t.toString()+"Z";if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime().toString()+"Z";return value.toString();}
    private static String first(Object... values){for(Object v:values)if(v instanceof String text&&!text.isBlank())return text.strip();return "";}
    private Map<String,Map<String,Object>> statistics(List<String> owners){
        var result=new HashMap<String,Map<String,Object>>();if(owners.isEmpty())return result;
        String in=String.join(",",Collections.nCopies(owners.size(),"?"));var args=new ArrayList<Object>();args.add(product.getTenantId());args.addAll(owners);
        for(String owner:owners){var values=new LinkedHashMap<String,Object>();values.put("lastLoginAt",null);values.put("loginCount",0);values.put("activeSeconds",0);values.put("lastActiveAt",null);for(String key:List.of("inputTokens","outputTokens","totalTokens","modelRuns","measuredRuns","partialRuns"))values.put(key,0);result.put(owner,values);}
        for(var row:jdbc.queryForList("SELECT owner_user_id,MAX(logged_in_at) AS lastLoginAt,COUNT(*) AS loginCount FROM zf_user_login_event WHERE tenant_id=? AND owner_user_id IN ("+in+") GROUP BY owner_user_id",args.toArray())){
            var target=result.get(row.get("owner_user_id").toString());target.put("lastLoginAt",utc(row.get("lastLoginAt")));target.put("loginCount",row.get("loginCount"));}
        for(var row:jdbc.queryForList("SELECT owner_user_id,active_millis,last_seen_at FROM zf_user_activity WHERE tenant_id=? AND owner_user_id IN ("+in+")",args.toArray())){
            var target=result.get(row.get("owner_user_id").toString());target.put("activeSeconds",((Number)row.get("active_millis")).longValue()/1000);target.put("lastActiveAt",utc(row.get("last_seen_at")));}
        for(var row:jdbc.queryForList("SELECT s.owner_user_id,COALESCE(SUM(prompt_tokens),0) AS inputTokens,COALESCE(SUM(completion_tokens),0) AS outputTokens,COALESCE(SUM(total_tokens),0) AS totalTokens,COUNT(*) AS modelRuns,COALESCE(SUM(CASE WHEN usage_source IS NOT NULL THEN 1 ELSE 0 END),0) AS measuredRuns,COALESCE(SUM(CASE WHEN usage_source='PROVIDER_PARTIAL' THEN 1 ELSE 0 END),0) AS partialRuns FROM ai_assistant_execution_run r JOIN ai_assistant_session s ON s.id=r.session_id AND s.tenant_id=r.tenant_id WHERE r.tenant_id=? AND s.owner_user_id IN ("+in+") AND s.owner_subject_type='APP_USER' GROUP BY s.owner_user_id",args.toArray())){
            var target=result.get(row.get("owner_user_id").toString());for(String key:List.of("inputTokens","outputTokens","totalTokens","modelRuns","measuredRuns","partialRuns"))target.put(key,row.get(key));}
        return result;
    }
	private static long id(String value) {
		try {
			if (!value.matches("[1-9][0-9]{0,18}"))
				throw new NumberFormatException();
			return Long.parseLong(value);
		}
		catch (NumberFormatException failure) {
			bad("INVALID_USER_ID");
			return 0;
		}
	}

	private static void bad(String code) {
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
	}

}
