package com.deepdimen.matrix.common.security.component;

/**
 * API Token 验证接口，解耦 Filter 与 upms 实现
 * 单体模式下由 matrix-upms 提供实现，微服务模式下可由网关层实现
 *
 * @author matrix
 * @date 2025-03-12
 */
public interface ApiTokenValidator {

	/**
	 * 根据 Token 值查询有效的 Token 信息
	 * 验证条件：token_value 存在 + status=active + expire_time 未过期
	 *
	 * @param tokenValue Token值
	 * @return Token信息，验证失败返回 null
	 */
	ApiTokenInfo findValidToken(String tokenValue);

	/**
	 * 更新 Token 最后使用时间
	 *
	 * @param tokenId Token ID
	 */
	void updateLastUsedTime(Long tokenId);

	/**
	 * 设置租户上下文（由实现类负责调用 TenantContextHolder）
	 *
	 * @param tenantId 租户ID字符串
	 */
	void setTenantContext(String tenantId);

	/**
	 * 清理租户上下文（Filter 请求处理完毕后调用，防止线程复用时租户上下文泄漏）
	 */
	void clearTenantContext();

	/**
	 * 查询租户下第一个有效的 API Token 值
	 * <p>
	 * 用于 Dify 外部知识库同步等场景，需要一个有效的 Token 作为回调认证凭据
	 *
	 * @param tenantId 租户ID
	 * @return Token 值（UUID 字符串），无有效 Token 时返回 null
	 */
	default String findFirstActiveTokenValue(String tenantId) {
		return null;
	}

	/**
	 * 获取或创建租户的 API Token
	 * <p>
	 * 先查询租户下是否有活跃 Token，有则返回；没有则自动创建一个系统级 Token 并返回。
	 * 用于 Dify 外部知识库同步等场景，确保首次使用时自动创建。
	 *
	 * @param tenantId 租户ID
	 * @param createBy 创建人（当前操作用户ID）
	 * @return Token 值（UUID 字符串）
	 */
	default String getOrCreateTokenValue(String tenantId, String createBy) {
		return findFirstActiveTokenValue(tenantId);
	}

	/**
	 * API Token 信息载体
	 */
	class ApiTokenInfo {

		private Long id;

		private String tenantId;

		private String createBy;

		public ApiTokenInfo(Long id, String tenantId, String createBy) {
			this.id = id;
			this.tenantId = tenantId;
			this.createBy = createBy;
		}

		public Long getId() {
			return id;
		}

		public String getTenantId() {
			return tenantId;
		}

		public String getCreateBy() {
			return createBy;
		}

	}

}
