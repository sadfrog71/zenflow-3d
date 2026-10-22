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

package com.deepdimen.matrix.common.data.datascope;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.admin.api.entity.SysDept;
import com.deepdimen.matrix.admin.api.entity.SysRole;
import com.deepdimen.matrix.admin.api.feign.RemoteDataScopeService;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.constant.enums.UserTypeEnum;
import com.deepdimen.matrix.common.core.util.RetOps;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author caoyuan
 * @date 2019-09-07
 * <p>
 * 默认data scope 判断处理器
 */

/**
 * @author caoyuan
 * @date 2019-09-07
 * <p>
 * 默认data scope 判断处理器
 */
@RequiredArgsConstructor
public class MatrixDefaultDataScopeHandle implements DataScopeHandle {

    private final RemoteDataScopeService dataScopeService;

    @Override
    public Boolean calcScope(DataScope dataScope) {
        // 对于TOC客户端，不进行数据权限校验
        MatrixUser user = SecurityUtils.getUser();
        if (UserTypeEnum.TOC.getStatus().equals(user.getUserType())) {
            return true;
        }

        // 业务代码里的规则，覆盖计算规则
        if (StrUtil.isNotBlank(dataScope.getUsername()) || CollUtil.isNotEmpty(dataScope.getDeptList())) {
            return false;
        }

        // 获取用户角色ID列表
        List<String> roleIdList = user.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(SecurityConstants.ROLE))
                .map(authority -> authority.split(StrUtil.UNDERLINE)[1])
                .toList();
        if (CollUtil.isEmpty(roleIdList)) {
            return false;
        }

        // 获取角色列表
        List<SysRole> roleList = RetOps.of(dataScopeService.getRoleList(roleIdList))
                .getData()
                .orElseGet(Collections::emptyList);
        if (CollUtil.isEmpty(roleList)) {
            return false;
        }

        // 处理数据权限
        return processDataScope(user, dataScope, roleList);
    }

    /**
     * 处理数据权限
     */
    private boolean processDataScope(MatrixUser user, DataScope dataScope, List<SysRole> roleList) {
        List<Long> deptList = dataScope.getDeptList();

        for (SysRole role : roleList) {
            Integer dsType = role.getDsType();

            // 处理不同数据权限类型
            switch (Objects.requireNonNull(DataScopeTypeEnum.getByType(dsType))) {
                case ALL:
                    return true;
                case CUSTOM:
                    handleCustomScope(role, deptList);
                    break;
                case OWN_CHILD_LEVEL:
                    handleOwnChildLevelScope(user, deptList);
                    break;
                case OWN_LEVEL:
                    handleOwnLevelScope(user, deptList);
                    break;
                case SELF_LEVEL:
                    handleSelfLevelScope(user, dataScope);
                    break;
                default:
                    break;
            }
        }

        return false;
    }

    /**
     * 处理自定义数据权限
     */
    private void handleCustomScope(SysRole role, List<Long> deptList) {
        if (StrUtil.isNotBlank(role.getDsScope())) {
            deptList.addAll(Arrays.stream(role.getDsScope().split(StrUtil.COMMA)).map(Long::parseLong).toList());
        }
    }

    /**
     * 处理本级及下级数据权限
     */
    private void handleOwnChildLevelScope(MatrixUser user, List<Long> deptList) {
        List<Long> descendantDeptIds = RetOps.of(dataScopeService.getDescendantList(user.getDeptId()))
                .getData()
                .orElseGet(Collections::emptyList)
                .stream()
                .map(SysDept::getDeptId)
                .toList();
        deptList.addAll(descendantDeptIds);
    }

    /**
     * 处理本级数据权限
     */
    private void handleOwnLevelScope(MatrixUser user, List<Long> deptList) {
        deptList.add(user.getDeptId());
    }

    /**
     * 处理个人数据权限
     */
    private void handleSelfLevelScope(MatrixUser user, DataScope dataScope) {
        dataScope.setUsername(user.getUsername());
    }

}
