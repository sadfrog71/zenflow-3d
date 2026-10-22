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

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.app.api.dto.AppUserInfo;
import com.deepdimen.matrix.app.api.entity.AppSocialDetails;

/**
 * 系统社交登录账号表
 *
 * @author caoyuan
 * @date 2018-08-16 21:30:41
 */
public interface AppSocialDetailsService extends IService<AppSocialDetails> {

	/**
	 * 绑定社交账号
	 * @param state 类型
	 * @param code code
	 * @return
	 */
	Boolean bindSocial(String state, String code);

	/**
	 * 根据入参查询用户信息
	 * @param inStr
	 * @return
	 */
	AppUserInfo getUserInfo(String inStr);

}
