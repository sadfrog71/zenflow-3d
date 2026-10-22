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

import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @author caoyuan
 * @date 2019/6/19
 * <p>
 * 租户接口
 */
@FeignClient(contextId = "remoteTenantService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteTenantService {

	/**
	 * 查询全部有效租户
	 * @return
	 */
	@NoToken
	@GetMapping("/tenant/list")
	R<List<SysTenant>> list();

	/**
	 * 校验用户是否可切换到目标租户。
	 * @param userId 用户 ID
	 * @param tenantId 目标租户 ID
	 * @return 租户有效且用户属于该租户时返回 true
	 */
	@NoToken
	@GetMapping("/tenant/member")
	R<Boolean> canSwitchTenant(@RequestParam("userId") Long userId, @RequestParam("tenantId") Long tenantId);

}
