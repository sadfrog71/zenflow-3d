package com.deepdimen.matrix.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 嵌入授权码申请参数
 *
 * @param username 用户名
 * @param agentId 智能体ID
 */
public record EmbedAuthorizeDTO(@NotBlank(message = "用户名不能为空") String username,
		@NotNull(message = "智能体ID不能为空") Long agentId) {
}
