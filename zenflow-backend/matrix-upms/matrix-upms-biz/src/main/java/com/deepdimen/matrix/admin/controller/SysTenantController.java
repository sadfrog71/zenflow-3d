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

package com.deepdimen.matrix.admin.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.admin.api.dto.SysTenantUserDTO;
import com.deepdimen.matrix.admin.api.dto.UserDTO;
import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.admin.service.SysTenantService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.resolver.ParamResolver;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户管理
 *
 * @author caoyuan
 * @date 2019-05-15 15:55:41
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/tenant")
@Tag(description = "tenant", name = "租户管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysTenantController {

	private final SysTenantService sysTenantService;

	private final SysMenuService sysMenuService;

	private final OAuth2AuthorizationService authorizationService;

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param sysTenant 租户
	 * @return
	 */
	@GetMapping("/page")
	@HasPermission("sys_systenant_view")
	public R getSysTenantPage(@ParameterObject Page page, @ParameterObject SysTenant sysTenant) {
		return R.ok(sysTenantService.page(page, Wrappers.<SysTenant>lambdaQuery()
			.like(StrUtil.isNotBlank(sysTenant.getName()), SysTenant::getName, sysTenant.getName())));
	}

	/**
	 * 通过ID 查询租户信息
	 * @param id ID
	 * @return R
	 */
	@GetMapping("/details/{id}")
	@HasPermission("sys_systenant_view")
	public R getById(@PathVariable Long id) {
		return R.ok(sysTenantService.getById(id));
	}

	/**
	 * 查询租户信息
	 * @param query 查询条件
	 * @return 租户信息
	 */
	@GetMapping("/details")
	@HasPermission("sys_systenant_view")
	public R getDetails(@ParameterObject SysTenant query) {
		return R.ok(sysTenantService.getOne(Wrappers.query(query), false));
	}

	/**
	 * 新增租户
	 * @param sysTenant 租户
	 * @return R
	 */
	@SysLog("新增租户")
	@PostMapping
	@HasPermission("sys_systenant_add")
	@CacheEvict(value = CacheConstants.TENANT_DETAILS, allEntries = true)
	public R save(@RequestBody SysTenant sysTenant) {
		return R.ok(sysTenantService.saveTenant(sysTenant));
	}

	/**
	 * 修改租户
	 * @param sysTenant 租户
	 * @return R
	 */
	@SysLog("修改租户")
	@PutMapping
	@HasPermission("sys_systenant_edit")
	public R updateById(@RequestBody SysTenant sysTenant) {
		return R.ok(sysTenantService.updateTenant(sysTenant));
	}

	/**
	 * 通过id删除租户
	 * <p>
	 * 为了保证安全,这里只删除租户表的数据，不删除基础表中的租户初始化数据。
	 * @param ids id 列表
	 * @return R
	 */
	@SysLog("删除租户")
	@DeleteMapping
	@HasPermission("sys_systenant_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(sysTenantService.removeTenant(ids));
	}

	/**
	 * 查询全部有效的租户列表
	 * @return 包含有效租户列表的响应结果
	 */
	@Inner(value = false)
	@GetMapping("/list")
	public R list() {
		List<SysTenant> tenants = sysTenantService.getNormalTenant()
			.stream()
			.filter(tenant -> tenant.getStartTime().isBefore(LocalDateTime.now()))
			.filter(tenant -> tenant.getEndTime().isAfter(LocalDateTime.now()))
			.toList();
		return R.ok(tenants);
	}

	/**
	 * 供认证服务校验租户切换权限。
	 * @param userId 当前用户 ID
	 * @param tenantId 目标租户 ID
	 * @return 是否可切换
	 */
	@Inner
	@GetMapping("/member")
	public R<Boolean> canSwitchTenant(@RequestParam Long userId, @RequestParam Long tenantId) {
		return R.ok(sysTenantService.canUserAccessTenant(userId, tenantId));
	}

	/**
	 * 导出租户信息到Excel
	 * @param sysTenant 租户查询条件
	 * @param ids 租户ID数组
	 * @return 符合条件的租户列表
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("sys_systenant_export")
	public List<SysTenant> export(SysTenant sysTenant, Long[] ids) {
		return sysTenantService.list(
				Wrappers.lambdaQuery(sysTenant).in(ArrayUtil.isNotEmpty(ids), SysTenant::getId, CollUtil.toList(ids)));
	}

	/**
	 * 获取租户菜单
	 * @param menuScope 菜单归属（0=后台菜单，1=前台菜单），为空时返回全部
	 * @return 包含菜单树结构的响应结果
	 */
	@GetMapping(value = "/tree/menu")
	public R getTree(@RequestParam(required = false) String menuScope) {
		Long defaultId = ParamResolver.getLong("TENANT_DEFAULT_ID", 1L);
		List<Tree<Long>> trees = new ArrayList<>();
		TenantBroker.runAs(defaultId, (id) -> {
			trees.addAll(sysMenuService.treeMenu(null, null, null, menuScope));
		});

		return R.ok(trees);
	}

	/**
	 * 获取用户租户信息
	 * @return 包含用户租户信息的响应结果
	 */
	@GetMapping("/user-tenant")
	public R getUserTenant() {
		return R.ok(sysTenantService.getUserTenant());
	}

	/**
	 * 新增用户租户关联关系
	 * @param tenantUserDTO 用户租户关联信息DTO
	 * @return 操作结果
	 */
	@PostMapping("/user-tenant")
	@HasPermission("sys_systenant_del")
	public R addUserTenant(@RequestBody SysTenantUserDTO tenantUserDTO) {
		return R.ok(sysTenantService.saveTenantUser(tenantUserDTO));
	}

	/**
	 * 获取用户租户分页信息
	 * @param page 分页参数
	 * @param userDTO 用户查询参数
	 * @return 租户分页结果
	 */
	@GetMapping("/user-tenant/page")
	@HasPermission("sys_systenant_del")
	public R getUserTenantPage(@ParameterObject Page page, @ParameterObject UserDTO userDTO) {
		return R.ok(sysTenantService.getUserTenantPage(page, userDTO));
	}

	/**
	 * 根据租户用户信息删除用户租户关系
	 * @param tenantUserDTO 租户用户信息DTO
	 * @return 操作结果
	 */
	@SysLog("删除租户>用户关系")
	@DeleteMapping("/remove-users")
	@HasPermission("sys_systenant_del")
	public R removeUserTenantById(@RequestBody SysTenantUserDTO tenantUserDTO) {
		return R.ok(sysTenantService.removeTenantUser(tenantUserDTO));
	}

	/**
	 * 根据条件查询租户用户列表
	 * @param userDTO 用户查询条件
	 * @return 包含查询结果的响应对象
	 */
	@GetMapping("/list-users")
	@HasPermission("sys_systenant_del")
	public R listTenantUser(@ParameterObject UserDTO userDTO) {
		return R.ok(sysTenantService.listTenantUser(userDTO));
	}

	/**
	 * 获取租户角色列表
	 * @param userDTO 用户信息参数对象
	 * @return 包含租户角色列表的响应结果
	 */
	@GetMapping("/list-org")
	@HasPermission("sys_systenant_del")
	public R listTenantOrg(@ParameterObject UserDTO userDTO) {
		return R.ok(sysTenantService.listTenantOrg(userDTO));
	}

	/**
	 * 更新用户租户信息
	 * @param userDto 用户数据传输对象，包含需要更新的用户信息
	 * @return 操作结果
	 */
	@PutMapping("/personal/update")
	public R updateUserTenant(@RequestBody UserDTO userDto,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
		String username = SecurityUtils.getUser().getUsername();
		userDto.setUsername(username);
		userDto.setUserId(SecurityUtils.getUser().getId());
		R result = sysTenantService.updateUserTenant(userDto);
		if (result.isOk() && StrUtil.isNotBlank(authHeader)) {
			String tokenValue = authHeader.replace(OAuth2AccessToken.TokenType.BEARER.getValue(), StrUtil.EMPTY).trim();
			OAuth2Authorization authorization = authorizationService.findByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN);
			if (authorization != null) {
				authorizationService.remove(authorization);
			}
		}
		return result;
	}

}
