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

package com.deepdimen.matrix.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.deepdimen.matrix.admin.api.vo.UserVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security 用户信息实体类，继承自UserVO并实现Serializable接口
 *
 * @author caoyuan
 * @date 2025/06/30
 */
@Data
@Schema(description = "用户信息")
@EqualsAndHashCode(callSuper = true)
public class UserInfo extends UserVO implements Serializable {

	/**
	 * Allow the current user to view their complete phone number.
	 */
	@Override
	@JsonSerialize(using = StringSerializer.class)
	public String getPhone() {
		return super.getPhone();
	}

	/**
	 * 密码
	 */
	@JsonIgnore(value = false)
	private String password;

	/**
	 * 随机盐
	 */
	@JsonIgnore(value = false)
	private String salt;

	/**
	 * 权限标识集合
	 */
	@Schema(description = "权限标识集合")
	private List<String> permissions = new ArrayList<>();

}
