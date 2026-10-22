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

package com.deepdimen.matrix.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.deepdimen.matrix.admin.api.entity.SysRole;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author caoyuan
 * @since 2017-10-29
 */
@Mapper
public interface SysRoleMapper extends MatrixBaseMapper<SysRole> {

	/**
	 * 通过用户ID查询角色信息
	 * @param userId 用户ID
	 * @param tenantId 租户ID
	 * @return 角色信息列表
	 */
	@InterceptorIgnore(tenantLine = "true")
	List<SysRole> listRolesByUserId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
