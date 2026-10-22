package com.deepdimen.matrix.admin.service;

import com.deepdimen.matrix.admin.api.dto.EmbedAuthorizeDTO;
import com.deepdimen.matrix.admin.api.vo.EmbedAuthorizeVO;

/**
 * 嵌入授权服务
 */
public interface EmbedAuthorizationService {

	/**
	 * 申请一次性嵌入授权码
	 * @param apiToken API Token
	 * @param dto 申请参数
	 * @param clientIp 客户端IP
	 * @param userAgent 客户端标识
	 * @return 授权结果
	 */
	EmbedAuthorizeVO authorize(String apiToken, EmbedAuthorizeDTO dto, String clientIp, String userAgent);

}
