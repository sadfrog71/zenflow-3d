package com.deepdimen.matrix.knowledge.support.provider;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

/**
 * Read-only native catalog, retaining the configured credential origin and bounded
 * pagination.
 */
public final class AliyunModelCatalog {

	private static final int PAGE_SIZE = 100;

	private static final int MAX_MODELS = 10000;

	private static final long BUDGET_NANOS = 8_000_000_000L;

	private static final Set<String> HOSTS = Set.of("dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com",
			"cn-hongkong.dashscope.aliyuncs.com", "dashscope-us.aliyuncs.com");

	private AliyunModelCatalog() {
	}

	public record Response(int status, JSONObject body) {
		@Override
		public String toString() {
			return "CatalogResponse[REDACTED]";
		}
	}

	@FunctionalInterface
	public interface Fetcher {

		Response get(String url, int timeoutMillis);

	}

	public static final class Unsupported extends RuntimeException {

		public Unsupported() {
			super("该地址未提供百炼原生模型目录");
		}

	}

	public static String endpoint(String baseUrl) {
		URI uri = URI.create(baseUrl);
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getRawQuery() != null
				|| uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
			return null;
		boolean workspace = host
			.matches("[a-z0-9][a-z0-9-]*\\.(cn-beijing|ap-northeast-1|eu-central-1|us-east-1)\\.maas\\.aliyuncs\\.com");
		String path = uri.getPath().replaceAll("/+$", "");
		if ((!HOSTS.contains(host) && !workspace) || !("/compatible-mode/v1".equals(path) || "/api/v1".equals(path)))
			return null;
		return "https://" + uri.getRawAuthority() + "/api/v1/models";
	}

	public static List<String> readAll(String endpoint, Fetcher fetcher) {
		return readAll(endpoint, fetcher, System::nanoTime);
	}

	static List<String> readAll(String endpoint, Fetcher fetcher, LongSupplier clock) {
		long start = clock.getAsLong();
		Long expectedTotal = null;
		int read = 0;
		LinkedHashSet<String> names = new LinkedHashSet<>();
		for (int page = 1; page <= 100; page++) {
			long remaining = BUDGET_NANOS - (clock.getAsLong() - start);
			if (remaining <= 0)
				throw new IllegalStateException("查询百炼模型目录超时，请重试");
			Response response = fetcher.get(
					endpoint + "?page_no=" + page + "&page_size=" + PAGE_SIZE + "&language=zh-CN",
					(int) Math.max(1, remaining / 1_000_000L));
			if (clock.getAsLong() - start >= BUDGET_NANOS)
				throw new IllegalStateException("查询百炼模型目录超时，请重试");
			if (page == 1 && (response.status() == 404 || response.status() == 405))
				throw new Unsupported();
			if (response.status() != 200)
				throw new IllegalStateException("查询百炼模型目录失败(HTTP " + response.status() + ")");
			JSONObject body = response.body();
			JSONObject output = body == null ? null : body.getJSONObject("output");
			if (output == null || Boolean.FALSE.equals(body.getBool("success")))
				throw new IllegalStateException("百炼模型目录响应无效，未返回不完整列表");
			Long total = output.getLong("total");
			Integer actualPage = output.getInt("page_no");
			JSONArray models = output.getJSONArray("models");
			if (total == null || total < 0 || total > MAX_MODELS || actualPage == null || actualPage != page
					|| models == null || models.size() > PAGE_SIZE)
				throw new IllegalStateException("百炼模型目录分页信息无效或超过资源上限");
			if (expectedTotal != null && !expectedTotal.equals(total))
				throw new IllegalStateException("百炼模型目录在读取期间变化，请重新刷新");
			expectedTotal = total;
			for (Object value : models) {
				if (!(value instanceof JSONObject model) || !(model.get("model") instanceof String name)
						|| name.isBlank())
					throw new IllegalStateException("百炼模型目录缺少有效模型ID");
				names.add(name);
			}
			read += models.size();
			if (read > total)
				throw new IllegalStateException("百炼模型目录分页计数不一致");
			if (read == total)
				return new ArrayList<>(names);
			if (models.isEmpty())
				throw new IllegalStateException("百炼模型目录提前结束，未返回不完整列表");
		}
		throw new IllegalStateException("百炼模型目录超过分页资源上限");
	}

}
