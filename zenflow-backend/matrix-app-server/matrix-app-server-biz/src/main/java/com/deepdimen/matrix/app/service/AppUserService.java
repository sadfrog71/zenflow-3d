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

package com.deepdimen.matrix.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.app.api.dto.AppUserDTO;
import com.deepdimen.matrix.app.api.dto.AppUserInfo;
import com.deepdimen.matrix.app.api.entity.AppUser;
import com.deepdimen.matrix.app.api.vo.AppUserExcelVO;
import com.deepdimen.matrix.app.api.vo.AppUserVO;
import com.deepdimen.matrix.common.core.util.R;
import org.springframework.validation.BindingResult;

import java.util.List;

/**
 * app用户表
 *
 * @author aeizzz
 * @date 2022-12-07 09:52:03
 */
public interface AppUserService extends IService<AppUser> {

	/**
	 * 更新APP用户信息
	 *
	 * @param appUser 用户信息DTO对象
	 * @return 更新结果
	 */
	Boolean updateUser(AppUserDTO appUser);

	/**
	 * 保存APP用户信息
	 * @param appUser 用户信息DTO对象
	 * @return 保存结果
	 */
	Boolean saveUser(AppUserDTO appUser);

	/**
	 * 获取APP用户列表
	 * @param appUser 查询条件
	 * @return 用户列表（Excel导出格式）
	 */
	List<AppUserExcelVO> listUser(AppUserDTO appUser);

	/**
	 * 分页查询带角色的用户列表
	 * @param page 分页对象
	 * @param appUserDTO 查询条件
	 * @return 分页用户数据
	 */
	IPage getUsersWithRolePage(Page page, AppUserDTO appUserDTO);

	/**
	 * 获取用户详细信息
	 * @param user 用户基本信息
	 * @return 用户详情
	 */
	AppUserInfo findUserInfo(AppUser user);

	/**
	 * 更新用户详细信息
	 * @param userDto 用户信息DTO对象
	 * @return 更新结果
	 */
	R updateUserInfo(AppUserDTO userDto);

	/**
	 * 根据用户ID查询用户VO对象
	 * @param userId 用户ID
	 * @return 用户VO对象
	 */
	AppUserVO selectUserVoById(Long userId);

	/**
	 * 批量删除APP用户
	 * @param ids 用户ID数组
	 * @return 删除结果
	 */
	Boolean deleteAppUserByIds(Long[] ids);

	/**
	 * 导入用户数据
	 * @param excelVOList Excel导入的用户数据列表
	 * @param bindingResult 数据校验结果
	 * @return 导入结果
	 */
	R importUser(List<AppUserExcelVO> excelVOList, BindingResult bindingResult);

	/**
	 * 注册APP用户
	 * @param appUser 用户信息DTO对象
	 * @return 注册结果
	 */
	R registerAppUser(AppUserDTO appUser);

}
