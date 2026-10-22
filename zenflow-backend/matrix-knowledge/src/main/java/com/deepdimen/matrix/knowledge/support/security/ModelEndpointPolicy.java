package com.deepdimen.matrix.knowledge.support.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import lombok.RequiredArgsConstructor;

/**
 * 校验由租户管理员填写的模型服务地址，防止 SSRF 和密钥被发送到平台内网地址。
 */
@Component
@RequiredArgsConstructor
public class ModelEndpointPolicy {

	private final AiKnowledgeProperties properties;

	public URI validate(String rawBaseUrl) {
		if (StrUtil.isBlank(rawBaseUrl)) {
			throw new IllegalArgumentException("baseUrl不能为空");
		}
		final URI uri;
		try {
			uri = URI.create(rawBaseUrl.trim()).normalize();
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("baseUrl格式无效", ex);
		}
		String scheme = lower(uri.getScheme());
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IllegalArgumentException("baseUrl仅支持 http 或 https");
		}
		if (StrUtil.isBlank(uri.getHost())) {
			throw new IllegalArgumentException("baseUrl必须包含有效主机名");
		}
		if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
			throw new IllegalArgumentException("baseUrl不能包含用户信息、查询参数或片段");
		}

		String host = normalizeHost(uri.getHost());
		if (!allowedPrivateHosts().contains(host)) {
			for (InetAddress address : resolve(host)) {
				if (!isPublicAddress(address)) {
					throw new IllegalArgumentException("不允许访问内网、本机或保留地址：" + host);
				}
			}
		}
		return uri;
	}

	public boolean sameEndpoint(String left, String right) {
		URI leftUri = validate(left);
		URI rightUri = validate(right);
		return lower(leftUri.getScheme()).equals(lower(rightUri.getScheme()))
				&& normalizeHost(leftUri.getHost()).equals(normalizeHost(rightUri.getHost()))
				&& effectivePort(leftUri) == effectivePort(rightUri)
				&& normalizePath(leftUri.getPath()).equals(normalizePath(rightUri.getPath()));
	}

	private Set<String> allowedPrivateHosts() {
		return properties.getModelEndpointPolicy().getAllowedPrivateHosts().stream()
				.filter(StrUtil::isNotBlank)
				.map(ModelEndpointPolicy::normalizeHost)
				.collect(Collectors.toUnmodifiableSet());
	}

	private InetAddress[] resolve(String host) {
		try {
			InetAddress[] addresses = InetAddress.getAllByName(host);
			if (addresses.length == 0) {
				throw new IllegalArgumentException("baseUrl主机名无法解析");
			}
			return addresses;
		}
		catch (UnknownHostException ex) {
			throw new IllegalArgumentException("baseUrl主机名无法解析", ex);
		}
	}

	private boolean isPublicAddress(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			int first = Byte.toUnsignedInt(bytes[0]);
			int second = Byte.toUnsignedInt(bytes[1]);
			int third = Byte.toUnsignedInt(bytes[2]);
			return !(first == 0 || first == 10 || first == 127 || first >= 224
					|| (first == 100 && second >= 64 && second <= 127)
					|| (first == 169 && second == 254)
					|| (first == 172 && second >= 16 && second <= 31)
					|| (first == 192 && second == 0 && (third == 0 || third == 2))
					|| (first == 192 && second == 168)
					|| (first == 198 && (second == 18 || second == 19))
					|| (first == 198 && second == 51 && third == 100)
					|| (first == 203 && second == 0 && third == 113));
		}
		if (address instanceof Inet6Address) {
			int first = Byte.toUnsignedInt(bytes[0]);
			int second = Byte.toUnsignedInt(bytes[1]);
			boolean uniqueLocal = (first & 0xfe) == 0xfc;
			boolean documentation = first == 0x20 && second == 0x01
					&& Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8;
			return !uniqueLocal && !documentation;
		}
		return false;
	}

	private static int effectivePort(URI uri) {
		if (uri.getPort() >= 0) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static String normalizePath(String path) {
		String normalized = StrUtil.blankToDefault(path, "");
		return normalized.length() > 1 ? normalized.replaceAll("/+$", "") : normalized;
	}

	private static String normalizeHost(String host) {
		return lower(Objects.requireNonNull(host)).replaceAll("\\.$", "");
	}

	private static String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

}
