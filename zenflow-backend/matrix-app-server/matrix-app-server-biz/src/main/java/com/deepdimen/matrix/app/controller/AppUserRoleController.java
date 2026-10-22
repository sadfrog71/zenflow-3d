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

package com.deepdimen.matrix.app.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.app.api.dto.AppUserDTO;
import com.deepdimen.matrix.app.api.entity.AppUserRole;
import com.deepdimen.matrix.app.service.AppUserRoleService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户角色表
 *
 * @author aeizzz
 * @date 2022-12-07 09:52:03
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/appuserrole")
@Tag(description = "appuserrole", name = "用户角色表管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AppUserRoleController {

	private final AppUserRoleService appUserRoleService;

	/**
	 * 分页查询用户角色信息。
	 *
	 * @param page    分页参数对象，用于指定查询的页码和每页显示的记录数
	 * @param userDTO 用户数据传输对象，包含查询条件
	 * @return 返回分页结果，包含查询到的用户角色列表及相关分页信息
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	public R getAppUserRolePage(Page page, AppUserDTO userDTO) {
		return R.ok(appUserRoleService.pageUserRole(page, userDTO));
	}

	/**
	 * 通过id查询用户角色表
	 * @param userId id
	 * @return R
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{userId}")
	public R getById(@PathVariable("userId") Long userId) {
		return R.ok(appUserRoleService.getById(userId));
	}

	/**
	 * 新增用户角色表
	 * @param appUserRole 用户角色表
	 * @return R
	 */
	@Operation(summary = "新增用户角色表", description = "新增用户角色表")
	@SysLog("新增用户角色表")
	@PostMapping
	@HasPermission("app_approle_add")
	public R save(@RequestBody AppUserRole appUserRole) {
		return R.ok(appUserRoleService.save(appUserRole));
	}


	/**
	 * 批量保存用户角色信息
	 *
	 * @param appUserRoleList 用户角色信息列表
	 * @return 保存结果
	 */
	@Operation(summary = "新增用户角色表", description = "新增用户角色表")
	@SysLog("新增用户角色表")
	@PostMapping("/batch")
	@HasPermission("app_approle_add")
	public R batchSave(@RequestBody List<AppUserRole> appUserRoleList) {
		return R.ok(appUserRoleService.batchSave(appUserRoleList));
	}

	/**
	 * 修改用户角色表
	 * @param appUserRole 用户角色表
	 * @return R
	 */
	@Operation(summary = "修改用户角色表", description = "修改用户角色表")
	@SysLog("修改用户角色表")
	@PutMapping
	@HasPermission("app_approle_edit")
	public R updateById(@RequestBody AppUserRole appUserRole) {
		return R.ok(appUserRoleService.updateById(appUserRole));
	}

	/**
	 * 通过id删除用户角色表
	 * @param userId id
	 * @return R
	 */
	@Operation(summary = "通过id删除用户角色表", description = "通过id删除用户角色表")
	@SysLog("通过id删除用户角色表")
	@DeleteMapping("/{userId}")
	@HasPermission("app_approle_del")
	public R removeById(@PathVariable Long userId) {
		return R.ok(appUserRoleService.removeById(userId));
	}

	/**
	 * 导出excel 表格
	 * @param appUserRole 查询条件
	 * @return excel 文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("app_approle_export")
	public List<AppUserRole> export(AppUserRole appUserRole) {
		return appUserRoleService.list(Wrappers.query(appUserRole));
	}

}
