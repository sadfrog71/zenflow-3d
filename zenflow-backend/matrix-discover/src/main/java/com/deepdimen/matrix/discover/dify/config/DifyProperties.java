package com.deepdimen.matrix.discover.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Dify API 配置属性
 * <p>
 * 从环境变量读取 Dify 服务地址和登录凭据
 *
 * @author matrix
 * @date 2026/03/14
 */
@Data
@ConfigurationProperties(prefix = "matrix.dify")
public class DifyProperties {

	/**
	 * 是否启用 Dify 集成
	 */
	private boolean enabled = true;

	/**
	 * Dify Console API 基础地址（如 http://localhost:5001）
	 */
	private String baseUrl;

	/**
	 * Dify Service API 基础地址（如 http://localhost:5001/v1） 默认为 baseUrl + /v1
	 */
	private String serviceApiBaseUrl;

	/**
	 * Dify 控制台登录邮箱
	 */
	private String email;

	/**
	 * Dify 控制台登录密码
	 */
	private String password;

	/**
	 * 连接超时时间（秒）
	 */
	private int connectTimeout = 30;

	/**
	 * 读取超时时间（秒）
	 */
	private int readTimeout = 60;

	/**
	 * SSE 流式读取超时时间（秒）
	 */
	private int streamReadTimeout = 300;

	/**
	 * 是否显示HTTP请求日志
	 */
	private boolean showLog = false;

	/**
	 * Matrix 对外 API 地址，用于 Dify 外部知识库回调
	 * <p>
	 * 如 http://matrix-boot:9999/admin/external-knowledge Docker 部署时通过环境变量
	 * MATRIX_DIFY_MATRIX_API_ENDPOINT 覆盖
	 */
	private String matrixApiEndpoint;

	/**
	 * Dify 侧 Matrix Chat2DB 自定义工具的 provider name，用于匹配工具 provider_id
	 */
	private String chat2dbToolName = "matrix-chat2db";

	/**
	 * 获取 Service API 基础地址，未配置时默认使用 baseUrl + /v1
	 */
	public String getServiceApiBaseUrl() {
		if (StringUtils.hasText(serviceApiBaseUrl)) {
			return serviceApiBaseUrl;
		}
		return StringUtils.hasText(baseUrl) ? baseUrl + "/v1" : null;
	}

}
