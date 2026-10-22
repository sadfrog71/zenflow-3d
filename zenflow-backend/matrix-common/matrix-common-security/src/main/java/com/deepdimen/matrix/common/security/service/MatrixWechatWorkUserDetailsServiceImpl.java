package com.deepdimen.matrix.common.security.service;

import com.deepdimen.matrix.admin.api.dto.UserInfo;
import com.deepdimen.matrix.admin.api.feign.RemoteUserService;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.RetOps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 企业微信登录 UserDetailsService 实现
 * 通过企业微信 userId 查找本地用户
 *
 * @author matrix
 * @date 2026-03-12
 */
@Slf4j
@RequiredArgsConstructor
public class MatrixWechatWorkUserDetailsServiceImpl implements MatrixUserDetailsService {

	private final RemoteUserService remoteUserService;

	@Override
	@SneakyThrows
	public UserDetails loadUserByUsername(String wxCpUserId) {
		R<UserInfo> result = remoteUserService.infoByWxCpUserId(wxCpUserId);
		return getUserDetails(RetOps.of(result).getData());
	}

	@Override
	public boolean support(String clientId, String grantType) {
		return SecurityConstants.WECHAT_WORK.equals(grantType);
	}

	@Override
	public int getOrder() {
		return Integer.MIN_VALUE + 2;
	}

}
