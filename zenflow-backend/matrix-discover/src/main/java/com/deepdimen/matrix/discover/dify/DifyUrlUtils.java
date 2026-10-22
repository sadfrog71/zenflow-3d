package com.deepdimen.matrix.discover.dify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dify URL 工具类
 *
 * @author matrix
 * @date 2026/03/14
 */
public final class DifyUrlUtils {

	private DifyUrlUtils() {
	}

	/**
	 * 构建带查询参数的完整 URI
	 * @param baseUrl 基础地址
	 * @param path API 路径
	 * @param queryParams 查询参数（可为 null）
	 * @return 完整 URI
	 */
	public static String buildUri(String baseUrl, String path, Map<String, String> queryParams) {
		StringBuilder sb = new StringBuilder(baseUrl).append(path);
		if (queryParams != null && !queryParams.isEmpty()) {
			sb.append('?');
			queryParams.forEach((k, v) -> sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
				.append('=')
				.append(v != null ? URLEncoder.encode(v, StandardCharsets.UTF_8) : "")
				.append('&'));
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

}
