/*
 *    Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the deepdimen.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: caoyuan (caoyuanchn@gmail.com)
 */

package com.deepdimen.matrix.common.security.service;

import com.deepdimen.matrix.admin.api.dto.UserInfo;
import com.deepdimen.matrix.admin.api.feign.RemoteUserService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.constant.enums.UserTypeEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.RetOps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Objects;
import java.util.Optional;

/**
 * 用户详细信息 default
 *
 * @author caoyuan
 */
@Slf4j
@Primary
@RequiredArgsConstructor
public class MatrixDefaultUserDetailsServiceImpl implements MatrixUserDetailsService {

    private final RemoteUserService remoteUserService;

    private final CacheManager cacheManager;

    /**
     * 用户密码登录
     *
     * @param username 用户名
     * @return
     * @throws UsernameNotFoundException
     */
    @Override
    @SneakyThrows
    public UserDetails loadUserByUsername(String username) {
        Cache cache = cacheManager.getCache(CacheConstants.USER_DETAILS);
		String cacheKey = cacheKey(null, username);
		if (Objects.nonNull(cache) && Objects.nonNull(cache.get(cacheKey))) {
			return getUserDetails(Optional.ofNullable(cache.get(cacheKey, UserInfo.class)));
        }

        R<UserInfo> result = remoteUserService.info(username);
        Optional<UserInfo> userInfoOptional = RetOps.of(result).getData();
		userInfoOptional.ifPresent(userInfo -> Objects.requireNonNull(cache).put(cacheKey, userInfo));
        return getUserDetails(userInfoOptional);
    }

    /**
     * 通过用户实体查询
     *
     * @param matrixUser user
     * @return
     */
    @Override
    public UserDetails loadUserByUser(MatrixUser matrixUser) {
        // 避免 C端用户通过接口调用B端接口的安全问题
        if (UserTypeEnum.TOB.getStatus().equals(matrixUser.getUserType())) {
			Cache cache = cacheManager.getCache(CacheConstants.USER_DETAILS);
			String cacheKey = cacheKey(matrixUser.getTenantId(), matrixUser.getUsername());
			if (Objects.nonNull(cache) && Objects.nonNull(cache.get(cacheKey))) {
				return getUserDetails(Optional.ofNullable(cache.get(cacheKey, UserInfo.class)));
			}
			R<UserInfo> result = remoteUserService.info(matrixUser.getUsername());
			Optional<UserInfo> userInfo = RetOps.of(result).getData();
			userInfo.ifPresent(value -> Objects.requireNonNull(cache).put(cacheKey, value));
			return getUserDetails(userInfo);
        }
        return null;
    }

	private String cacheKey(Long tenantId, String username) {
		Long resolvedTenant = tenantId;
		if (resolvedTenant == null) {
			RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
			Object explicit = attributes == null ? null : attributes.getAttribute(
					"com.deepdimen.matrix.common.data.tenant.TenantContextHolderFilter.explicitTenant",
					RequestAttributes.SCOPE_REQUEST);
			try {
				resolvedTenant = explicit == null ? 1L : Long.valueOf(explicit.toString());
			}
			catch (NumberFormatException ex) {
				throw new UsernameNotFoundException("非法租户上下文");
			}
		}
		return resolvedTenant + ":" + username;
	}

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

}
