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
package com.deepdimen.matrix.pay.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.pay.entity.PayChannel;
import com.deepdimen.matrix.pay.entity.PayRefundOrder;
import com.deepdimen.matrix.pay.handler.PayOrderRefundHandler;
import com.deepdimen.matrix.pay.mapper.PayChannelMapper;
import com.deepdimen.matrix.pay.mapper.PayRefundOrderMapper;
import com.deepdimen.matrix.pay.service.PayRefundOrderService;
import com.deepdimen.matrix.pay.utils.PayChannelNameEnum;
import com.deepdimen.matrix.pay.utils.RefundNameEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 退款
 *
 * @author caoyuan
 * @date 2019-05-28 23:58:11
 */
@Service
@RequiredArgsConstructor
public class PayRefundOrderServiceImpl extends ServiceImpl<PayRefundOrderMapper, PayRefundOrder>
        implements PayRefundOrderService {

    private final Map<String, PayOrderRefundHandler> refundHandlerMap;

    private final PayChannelMapper channelMapper;

    /**
     * 退款操作
     *
     * @param refundOrder refundOrder
     * @return true/false
     */
    @Override
    public Boolean refund(PayRefundOrder refundOrder) {
        PayChannel payChannel = channelMapper.selectOne(Wrappers.<PayChannel>lambdaQuery().eq(PayChannel::getChannelMchId, refundOrder.getChannelMchId()));


        // 判断用哪个通道
        if (StrUtil.equals(payChannel.getChannelId(), PayChannelNameEnum.ALIPAY_WAP.getName())) {
            refundHandlerMap.get(RefundNameEnum.ALIPAY.getName()).handle(refundOrder);
        }

        throw new UnsupportedOperationException("暂不支持该渠道退款");
    }

}
