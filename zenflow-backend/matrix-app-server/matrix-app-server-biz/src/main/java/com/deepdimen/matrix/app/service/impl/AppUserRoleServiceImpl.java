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
package com.deepdimen.matrix.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.feign.RemoteUserService;
import com.deepdimen.matrix.app.api.dto.AppUserDTO;
import com.deepdimen.matrix.app.api.entity.AppUserRole;
import com.deepdimen.matrix.app.mapper.AppUserRoleMapper;
import com.deepdimen.matrix.app.service.AppUserRoleService;
import com.deepdimen.matrix.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 应用用户角色服务实现类：提供用户角色相关的服务实现
 *
 * @author caoyuan
 * @date 2025/05/29
 */
@Service
@RequiredArgsConstructor
public class AppUserRoleServiceImpl extends ServiceImpl<AppUserRoleMapper, AppUserRole> implements AppUserRoleService {

    private final RemoteUserService remoteUserService;


    /**
     * 根据分页信息和用户角色查询数据。
     *
     * @param page    分页信息，包含当前页码和每页显示条数
     * @param userDTO 用户数据传输对象，包含用户角色信息用于数据过滤
     * @return 返回分页后的系统用户数据
     */
    @Override
    public Page pageUserRole(Page page, AppUserDTO userDTO) {
        // 根据用户名查询用户列表
        if (StrUtil.isNotBlank(userDTO.getUsername())) {
            R<List<SysUser>> userByIdR = remoteUserService.getUserListByUserName(userDTO.getUsername());
            Page<SysUser> userPage = new Page<>();
            userPage.setCurrent(page.getCurrent());
            userPage.setSize(page.getSize());
            userPage.setTotal(userByIdR.getData().size());
            userPage.setRecords(userByIdR.getData());
            return userPage;
        }

        Page<AppUserRole> selectPage = baseMapper.selectPage(page, Wrappers.<AppUserRole>lambdaQuery());
        if (selectPage.getTotal() == 0) {
            return selectPage;
        }
        // 根据用户ID 查询用户列表
        List<Long> userIdList = selectPage.getRecords().stream().map(AppUserRole::getUserId).toList();
        R<List<SysUser>> userByIdR = remoteUserService.getUserById(userIdList);
        Page<SysUser> userPage = new Page<>();
        userPage.setCurrent(selectPage.getCurrent());
        userPage.setSize(selectPage.getSize());
        userPage.setTotal(selectPage.getTotal());
        userPage.setRecords(userByIdR.getData());
        return userPage;
    }

    /**
     * 批量保存应用用户角色信息的方法
     *
     * @param appUserRoleList 应用用户角色信息列表
     * @return 保存操作是否成功
     */
    @Override
    public boolean batchSave(List<AppUserRole> appUserRoleList) {
        for (AppUserRole appUserRole : appUserRoleList) {
            if (baseMapper.exists(Wrappers.lambdaQuery(appUserRole))) {
                continue;
            }
            baseMapper.insert(appUserRole);
        }
        return false;
    }
}
