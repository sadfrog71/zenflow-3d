/*
 *
 *      Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the deepdimen.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: caoyuan (caoyuanchn@gmail.com)
 *
 */

package com.deepdimen.matrix.admin.api.feign;

import com.deepdimen.matrix.admin.api.dto.UserDTO;
import com.deepdimen.matrix.admin.api.dto.UserInfo;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author caoyuan
 * @date 2018/6/22
 */
@FeignClient(contextId = "remoteUserService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteUserService {

	/**
	 * 通过用户名查询用户、角色信息
	 * @param username 用户名
	 * @return R
	 */
	@NoToken
	@GetMapping("/user/info/{username}")
	R<UserInfo> info(@PathVariable("username") String username);

	/**
	 * 根据用户ID获取用户
	 * @param userIds ID
	 * @return SysUser
	 */
	@GetMapping("/user/list")
	R<List<SysUser>> getUserById(@RequestParam("userIds") List<Long> userIds);

	/**
	 * 通过社交账号或手机号查询用户、角色信息
	 * @param inStr appid@code
	 * @return
	 */
	@NoToken
	@GetMapping("/social/info/{inStr}")
	R<UserInfo> social(@PathVariable("inStr") String inStr);

	/**
	 * 查询上级部门的用户信息
	 * @param username 用户名
	 * @return R
	 */
	@GetMapping("/user/ancestor/{username}")
	R<List<SysUser>> ancestorUsers(@PathVariable("username") String username);

	/**
	 * 锁定用户
	 * @param username 用户名
	 * @return
	 */
	@NoToken
	@PutMapping("/user/lock/{username}")
	R<Boolean> lockUser(@PathVariable("username") String username);

	/**
	 * 根据角色ID查询用户列表
	 * @param roleIdList 角色ID列表
	 * @return 用户ID列表
	 */
	@GetMapping("/user/getUserIdListByRoleIdList")
	R<List<Long>> getUserIdListByRoleIdList(@RequestParam("roleIdList") List<Long> roleIdList);

	/**
	 * 根据部门ID列表获取用户ID列表接口
	 * @param deptIdList 部门ID列表
	 * @return 用户ID列表
	 */
	@GetMapping("/user/getUserIdListByDeptIdList")
	R<List<SysUser>> getUserIdListByDeptIdList(@RequestParam("deptIdList") List<Long> deptIdList);

	/**
	 * 通过用户名查询用户列表
	 * @param userName 用户名
	 * @return 用户列表
	 */
	@GetMapping("/user/getUserListByUserName")
	R<List<SysUser>> getUserListByUserName(@RequestParam("username") String userName);

	/**
	 * 注册用户
	 * @param userDTO 用户信息
	 * @return success/false
	 */
	@NoToken
	@PostMapping("/register/user")
	R<Boolean> registerUser(@RequestBody UserDTO userDTO);

	/**
	 * 通过企业微信 userId 查询用户信息
	 * @param wxCpUserId 企业微信 userId
	 * @return 用户信息
	 */
	@NoToken
	@GetMapping("/user/infoByWxCpUserId/{wxCpUserId}")
	R<UserInfo> infoByWxCpUserId(@PathVariable("wxCpUserId") String wxCpUserId);

}
