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

import com.deepdimen.matrix.admin.api.dto.SysLogDTO;
import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author caoyuan
 * @date 2018/6/28
 */
@FeignClient(contextId = "remoteLogService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteLogService {

	/**
	 * 保存日志
	 * @param sysLog 日志实体
	 * @return succes、false
	 */
	@NoToken
	@PostMapping("/log/save")
	R<Boolean> saveLog(@RequestBody SysLogDTO sysLog);

}
