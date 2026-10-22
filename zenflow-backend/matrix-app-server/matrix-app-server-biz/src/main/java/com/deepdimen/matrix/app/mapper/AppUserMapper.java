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

package com.deepdimen.matrix.app.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.app.api.dto.AppUserDTO;
import com.deepdimen.matrix.app.api.entity.AppUser;
import com.deepdimen.matrix.app.api.vo.AppUserVO;
import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * app用户表
 *
 * @author aeizzz
 * @date 2022-12-07 09:52:03
 */
@Mapper
public interface AppUserMapper extends MatrixBaseMapper<AppUser> {

	IPage<AppUserVO> getUserVosPage(Page page, @Param("query") AppUserDTO appUserDTO);

	AppUserVO getUserVoById(Long userId);

}
