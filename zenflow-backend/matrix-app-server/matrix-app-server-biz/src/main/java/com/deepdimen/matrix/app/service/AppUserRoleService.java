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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.app.api.dto.AppUserDTO;
import com.deepdimen.matrix.app.api.entity.AppUserRole;

import java.util.List;

/**
 * 用户角色表
 *
 * @author aeizzz
 * @date 2022-12-07 09:52:03
 */
public interface AppUserRoleService extends IService<AppUserRole> {

    /**
     * 根据分页信息和用户角色查询数据。
     *
     * @param page    分页信息，包括页码和每页显示条数
     * @param userDTO 用户角色信息，用于数据过滤
     * @return 查询结果分页数据
     */
    Page pageUserRole(Page page, AppUserDTO userDTO);

    /**
     * 批量保存应用用户角色信息的方法
     *
     * @param appUserRoleList 应用用户角色信息列表
     * @return 保存操作是否成功
     */
    boolean batchSave(List<AppUserRole> appUserRoleList);
}
