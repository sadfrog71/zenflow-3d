package com.deepdimen.matrix.admin.api.vo;

/**
 * 嵌入授权码申请结果
 *
 * @param code 一次性授权码
 * @param expiresIn 有效秒数
 * @param embedUrl 嵌入地址
 */
public record EmbedAuthorizeVO(String code, long expiresIn, String embedUrl) {
}
