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

package com.deepdimen.matrix.pay.mapper;

import com.deepdimen.matrix.common.data.datascope.MatrixBaseMapper;
import com.deepdimen.matrix.pay.entity.PayNotifyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步通知记录
 *
 * @author caoyuan
 * @date 2019-05-28 23:57:23
 */
@Mapper
public interface PayNotifyRecordMapper extends MatrixBaseMapper<PayNotifyRecord> {

}
