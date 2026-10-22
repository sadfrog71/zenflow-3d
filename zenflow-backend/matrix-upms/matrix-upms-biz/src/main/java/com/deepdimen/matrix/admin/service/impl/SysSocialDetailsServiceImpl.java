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

package com.deepdimen.matrix.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.dto.UserInfo;
import com.deepdimen.matrix.admin.api.entity.SysSocialDetails;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.vo.SysSocialDetailsVO;
import com.deepdimen.matrix.admin.handler.LoginHandler;
import com.deepdimen.matrix.admin.mapper.SysSocialDetailsMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.service.SysSocialDetailsService;
import com.deepdimen.matrix.common.core.constant.CacheConstants;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author caoyuan
 * @date 2018年08月16日
 */
@Slf4j
@AllArgsConstructor
@Service("sysSocialDetailsService")
public class SysSocialDetailsServiceImpl extends ServiceImpl<SysSocialDetailsMapper, SysSocialDetails>
		implements SysSocialDetailsService {

	private final Map<String, LoginHandler> loginHandlerMap;

	private final CacheManager cacheManager;

	private final SysUserMapper sysUserMapper;

	/**
	 * 绑定社交账号
	 * @param type type
	 * @param code code
	 * @return
	 */
	@Override
	public Boolean bindSocial(String type, String code) {
		LoginHandler loginHandler = loginHandlerMap.get(type);
		// 绑定逻辑
		String identify = loginHandler.identify(code);
		SysUser sysUser = sysUserMapper.selectById(SecurityUtils.getUser().getId());
		loginHandler.bind(sysUser, identify);

		// 更新緩存
		cacheManager.getCache(CacheConstants.USER_DETAILS).clear();
		return Boolean.TRUE;
	}

	/**
	 * 根据入参查询用户信息
	 * @param inStr TYPE@code
	 * @return
	 */
	@Override
	public UserInfo getUserInfo(String inStr) {
		String[] inStrs = inStr.split(StringPool.AT);
		String type = inStrs[0];
		String loginStr = inStr.substring(type.length() + 1);
		return loginHandlerMap.get(type).handle(loginStr);
	}

	/**
	 * 查询当前租户下的三方应用信息 ，脱敏 appSecret
	 * @return List<SysSocialDetails>
	 */
	@Override
	public List<SysSocialDetailsVO> selectList() {
		List<SysSocialDetails> sysSocialDetailsList = baseMapper.selectList(Wrappers.query());
		return sysSocialDetailsList.stream().map(sysSocialDetails -> {
			SysSocialDetailsVO newSysSocialDetails = new SysSocialDetailsVO();
			BeanUtils.copyProperties(sysSocialDetails, newSysSocialDetails);
			return newSysSocialDetails;
		}).toList();
	}

}
