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

import com.deepdimen.matrix.admin.api.dto.MessageEmailDTO;
import com.deepdimen.matrix.admin.api.dto.MessageHookDTO;
import com.deepdimen.matrix.admin.api.dto.MessageSmsDTO;
import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 远程消息服务
 *
 * @author caoyuan
 * @date 2024/07/18
 */
@FeignClient(contextId = "remoteMessageService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteMessageService {

    /**
     * 发送短消息
     *
     * @param smsDTO 短信 DTO
     * @return {@link R }
     */
    @PostMapping("/sysMessage/send/sms")
    R sendSms(@RequestBody MessageSmsDTO smsDTO);

    /**
     * 发送邮件
     *
     * @param emailDTO 电子邮件 DTO
     * @return {@link R }
     */
    @PostMapping("/sysMessage/send/email")
    R sendEmail(@RequestBody MessageEmailDTO emailDTO);

    /**
     * 发送钩子
     *
     * @param hookDTO 钩子 dto
     * @return {@link R }
     */
    @PostMapping("/sysMessage/send/hook")
    R sendHook(@RequestBody MessageHookDTO hookDTO);

}
