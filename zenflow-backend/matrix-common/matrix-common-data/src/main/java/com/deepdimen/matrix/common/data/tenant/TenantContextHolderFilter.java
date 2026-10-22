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

package com.deepdimen.matrix.common.data.tenant;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.admin.api.feign.RemoteTenantService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.constant.CommonConstants;
import com.deepdimen.matrix.common.core.util.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author caoyuan
 * @date 2018/9/13
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextHolderFilter extends GenericFilterBean {

    private final static String UNDEFINED_STR = "undefined";

	public static final String EXPLICIT_TENANT_ATTRIBUTE =
			TenantContextHolderFilter.class.getName() + ".explicitTenant";

    private final RemoteTenantService remoteTenantService;

    private final CacheManager cacheManager;

    private final org.springframework.beans.factory.ObjectProvider<ProductTenantLookup> productTenantLookup;

    @Override
    @SneakyThrows
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String headerTenantId = request.getHeader(CommonConstants.TENANT_ID);
        String paramTenantId = request.getParameter(CommonConstants.TENANT_ID);

        log.debug("获取header中的租户ID为:{}", headerTenantId);

		String explicitTenant = StrUtil.isNotBlank(headerTenantId) && !StrUtil.equals(UNDEFINED_STR, headerTenantId)
				? headerTenantId : StrUtil.isNotBlank(paramTenantId) && !StrUtil.equals(UNDEFINED_STR, paramTenantId)
						? paramTenantId : null;
		request.setAttribute(EXPLICIT_TENANT_ATTRIBUTE, explicitTenant);
		try {
			TenantContextHolder.setTenantId(explicitTenant == null
					? (productTenantLookup.getIfAvailable() == null ? CommonConstants.TENANT_ID_1 : productTenantLookup.getObject().defaultTenantId()) : parseTenantId(explicitTenant, response));
			if (TenantContextHolder.getTenantId() == null || !checkTenantStatus(request, response)) {
				return;
			}
			filterChain.doFilter(request, response);
		}
		finally {
			TenantContextHolder.clear();
		}
    }

	private Long parseTenantId(String value, HttpServletResponse response) throws java.io.IOException {
		try {
			return Long.valueOf(value);
		}
		catch (NumberFormatException ex) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().print(JSONUtil.toJsonStr(R.failed("非法租户上下文")));
			return null;
		}
	}

    /**
     * 检查租户状态
     *
     * @param request  请求
     * @param response 响应
     * @return boolean
     */
    @SneakyThrows
    private boolean checkTenantStatus(HttpServletRequest request, HttpServletResponse response) {
        ProductTenantLookup product = productTenantLookup.getIfAvailable();
        if (product != null) {
            boolean active;
            try { active = product.active(TenantContextHolder.getTenantId()); }
            catch (RuntimeException ex) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().print("{\"errorCode\":\"TENANT_UNAVAILABLE\"}");
                return false;
            }
            if (active) return true;
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().print("{\"errorCode\":\"INVALID_PRODUCT_TENANT\"}");
            return false;
        }
        // 如果是获取租户列表请求跳过
        if (StrUtil.containsAnyIgnoreCase(request.getRequestURI(), "/tenant/list")) {
            return true;
        }

        List<SysTenant> tenantList = null;

        // 判断租户状态
        Cache cache = cacheManager.getCache(CacheConstants.TENANT_DETAILS);

        if (Objects.nonNull(cache) && Objects.nonNull(cache.get(SimpleKey.EMPTY, List.class))) {
            tenantList = cache.get(SimpleKey.EMPTY, List.class);
        } else {
            R<List<SysTenant>> tenantListR = remoteTenantService.list();
            tenantList = tenantListR.getData();
        }

        if (CollUtil.isEmpty(tenantList)) {
            response.setStatus(503);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().print("{\"errorCode\":\"TENANT_UNAVAILABLE\"}");
            return false;
        }

        // 获取当前请求的租户ID
        Long currentTenantId = TenantContextHolder.getTenantId();
        // 检查租户是否存在且在有效期内
        boolean exist = tenantList.stream()
                .filter(tenant -> NumberUtil.equals(tenant.getId(), currentTenantId)) // 匹配租户ID
                .filter(tenant -> tenant.getStartTime().isBefore(LocalDateTime.now())) // 检查开始时间：租户已生效
                .anyMatch(tenant -> tenant.getEndTime().isAfter(LocalDateTime.now()));

        if (exist) {
            return true;
        }

        // 如果租户不存在或已失效，返回426状态码和错误信息
        response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(JSONUtil.toJsonStr(R.failed(StrUtil.format("租户ID为{}的租户不存在或已过期失效", currentTenantId))));
        return false;
    }

}
