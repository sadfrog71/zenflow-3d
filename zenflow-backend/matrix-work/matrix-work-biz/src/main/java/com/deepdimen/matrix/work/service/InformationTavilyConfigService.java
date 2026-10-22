package com.deepdimen.matrix.work.service;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SM4;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.work.api.dto.InformationTavilyConfigRequest;
import com.deepdimen.matrix.work.api.entity.InformationTavilyConfig;
import com.deepdimen.matrix.work.api.vo.InformationTavilyConfigVO;
import com.deepdimen.matrix.work.config.InformationTavilyProperties;
import com.deepdimen.matrix.work.mapper.InformationTavilyConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InformationTavilyConfigService {

	private static final String MASK = "******";
	private static final String DEFAULT_BASE_URL = "https://api.tavily.com";
	private static final String DEFAULT_SEARCH_DEPTH = "basic";

	private final InformationTavilyConfigMapper configMapper;
	private final InformationTavilyProperties properties;

	@Value("${matrix.auth-config.sm4-key:deepdimen1234567}")
	private String sm4Key;

	private volatile SM4 sm4Instance;

	public InformationTavilyConfigVO getConfig() {
		InformationTavilyConfig config = currentEntity();
		return toView(config);
	}

	@Transactional(rollbackFor = Exception.class)
	public InformationTavilyConfigVO saveConfig(InformationTavilyConfigRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Tavily配置不能为空");
		}
		InformationTavilyConfig config = currentEntity();
		if (config == null) {
			config = new InformationTavilyConfig();
			config.setCreateTime(LocalDateTime.now());
			config.setDelFlag("0");
		}
		config.setEnabled(request.getEnabled() == null ? 0 : (request.getEnabled() ? 1 : 0));
		config.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
		config.setSearchDepth(normalizeSearchDepth(request.getSearchDepth()));
		config.setMaxResults(normalizeInt(request.getMaxResults(), 20, 1, 20));
		config.setTimeoutSeconds(normalizeInt(request.getTimeoutSeconds(), 30, 5, 120));
		if (Boolean.TRUE.equals(request.getClearApiKey())) {
			config.setApiKeyCipher(null);
		}
		else if (StringUtils.hasText(request.getApiKey()) && !MASK.equals(request.getApiKey().trim())) {
			String apiKey = normalizeApiKey(request.getApiKey());
			config.setApiKeyCipher(encrypt(apiKey));
		}
		config.setUpdateTime(LocalDateTime.now());
		if (config.getId() == null) {
			configMapper.insert(config);
		}
		else {
			configMapper.updateById(config);
		}
		return toView(config);
	}

	public RuntimeConfig current() {
		InformationTavilyConfig config = currentEntity();
		if (config == null) {
			return new RuntimeConfig(properties.isEnabled(), properties.getApiKey(),
					valueOrDefault(properties.getBaseUrl(), DEFAULT_BASE_URL),
					valueOrDefault(properties.getSearchDepth(), DEFAULT_SEARCH_DEPTH),
					clamp(properties.getMaxResults(), 1, 20), clamp(properties.getTimeoutSeconds(), 5, 120));
		}
		String databaseKey = decrypt(config.getApiKeyCipher());
		String apiKey = StringUtils.hasText(databaseKey) ? databaseKey : properties.getApiKey();
		return new RuntimeConfig(Integer.valueOf(1).equals(config.getEnabled()), apiKey,
				valueOrDefault(config.getBaseUrl(), valueOrDefault(properties.getBaseUrl(), DEFAULT_BASE_URL)),
				valueOrDefault(config.getSearchDepth(), valueOrDefault(properties.getSearchDepth(), DEFAULT_SEARCH_DEPTH)),
				clamp(config.getMaxResults() == null ? properties.getMaxResults() : config.getMaxResults(), 1, 20),
				clamp(config.getTimeoutSeconds() == null ? properties.getTimeoutSeconds() : config.getTimeoutSeconds(), 5, 120));
	}

	private InformationTavilyConfig currentEntity() {
		return configMapper.selectOne(Wrappers.<InformationTavilyConfig>lambdaQuery()
				.orderByDesc(InformationTavilyConfig::getUpdateTime)
				.last("LIMIT 1"));
	}

	private InformationTavilyConfigVO toView(InformationTavilyConfig config) {
		InformationTavilyConfigVO view = new InformationTavilyConfigVO();
		if (config == null) {
			view.setEnabled(properties.isEnabled() ? 1 : 0);
			view.setConfigured(StringUtils.hasText(properties.getApiKey()));
			view.setApiKeyMasked(mask(properties.getApiKey()));
			view.setBaseUrl(valueOrDefault(properties.getBaseUrl(), DEFAULT_BASE_URL));
			view.setSearchDepth(valueOrDefault(properties.getSearchDepth(), DEFAULT_SEARCH_DEPTH));
			view.setMaxResults(clamp(properties.getMaxResults(), 1, 20));
			view.setTimeoutSeconds(clamp(properties.getTimeoutSeconds(), 5, 120));
			view.setSource("环境变量兜底");
			return view;
		}
		String key = decrypt(config.getApiKeyCipher());
		if (!StringUtils.hasText(key)) {
			key = properties.getApiKey();
		}
		view.setId(config.getId());
		view.setEnabled(config.getEnabled());
		view.setConfigured(StringUtils.hasText(key));
		view.setApiKeyMasked(mask(key));
		view.setBaseUrl(valueOrDefault(config.getBaseUrl(), DEFAULT_BASE_URL));
		view.setSearchDepth(valueOrDefault(config.getSearchDepth(), DEFAULT_SEARCH_DEPTH));
		view.setMaxResults(clamp(config.getMaxResults() == null ? properties.getMaxResults() : config.getMaxResults(), 1, 20));
		view.setTimeoutSeconds(clamp(config.getTimeoutSeconds() == null ? properties.getTimeoutSeconds() : config.getTimeoutSeconds(), 5, 120));
		view.setSource("租户后台配置");
		return view;
	}

	private String normalizeBaseUrl(String value) {
		String baseUrl = valueOrDefault(value, DEFAULT_BASE_URL);
		if (!baseUrl.startsWith("https://")) {
			throw new IllegalArgumentException("Tavily Base URL 必须使用 HTTPS");
		}
		try {
			URI uri = URI.create(baseUrl);
			if (!"api.tavily.com".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null
					|| uri.getPort() != -1 || StringUtils.hasText(uri.getQuery()) || StringUtils.hasText(uri.getFragment())) {
				throw new IllegalArgumentException("租户配置仅允许 Tavily 官方 API 地址");
			}
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Tavily Base URL 无效", ex);
		}
		return baseUrl.replaceAll("/+$", "");
	}

	private String normalizeApiKey(String value) {
		String apiKey = value == null ? "" : value.trim();
		if (!apiKey.startsWith("tvly-") || apiKey.length() < 12 || apiKey.chars().anyMatch(Character::isWhitespace)) {
			throw new IllegalArgumentException("Tavily Key 格式无效，应以 tvly- 开头");
		}
		return apiKey;
	}

	private String normalizeSearchDepth(String value) {
		return "advanced".equalsIgnoreCase(value) ? "advanced" : DEFAULT_SEARCH_DEPTH;
	}

	private int normalizeInt(Integer value, int fallback, int min, int max) {
		return clamp(value == null ? fallback : value, min, max);
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private String valueOrDefault(String value, String fallback) {
		return StringUtils.hasText(value) ? value.trim() : fallback;
	}

	private SM4 getSm4() {
		if (sm4Instance == null) {
			synchronized (this) {
				if (sm4Instance == null) {
					if (sm4Key == null || sm4Key.getBytes(StandardCharsets.UTF_8).length != 16) {
						throw new IllegalStateException("Tavily配置加密密钥必须是16字节，请配置 matrix.auth-config.sm4-key");
					}
					sm4Instance = SmUtil.sm4(sm4Key.getBytes(StandardCharsets.UTF_8));
				}
			}
		}
		return sm4Instance;
	}

	private String encrypt(String value) {
		return getSm4().encryptHex(value);
	}

	private String decrypt(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		try {
			return getSm4().decryptStr(value, StandardCharsets.UTF_8);
		}
		catch (RuntimeException ex) {
			log.error("Tavily配置解密失败，请检查 matrix.auth-config.sm4-key");
			throw new IllegalStateException("Tavily配置解密失败，请检查服务端加密密钥");
		}
	}

	private String mask(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.length() <= 8) {
			return MASK;
		}
		return trimmed.substring(0, 4) + MASK + trimmed.substring(trimmed.length() - 4);
	}

	public record RuntimeConfig(boolean enabled, String apiKey, String baseUrl, String searchDepth,
			int maxResults, int timeoutSeconds) {
	}
}
