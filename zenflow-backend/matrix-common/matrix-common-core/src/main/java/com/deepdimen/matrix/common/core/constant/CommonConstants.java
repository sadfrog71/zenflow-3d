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

package com.deepdimen.matrix.common.core.constant;

/**
 * @author caoyuan
 * @date 2017/10/29
 */
public interface CommonConstants {

    /**
     * header 中租户ID
     */
    String TENANT_ID = "TENANT-ID";

    /**
     * header 中版本信息
     */
    String VERSION = "VERSION";

    /**
     * 租户ID
     */
    Long TENANT_ID_1 = 1L;

    /**
     * 删除
     */
    String STATUS_DEL = "1";

    /**
     * 正常
     */
    String STATUS_NORMAL = "0";

    /**
     * 锁定
     */
    String STATUS_LOCK = "9";

    /**
     * 菜单树根节点
     */
    Long MENU_TREE_ROOT_ID = -1L;

    /**
     * 编码
     */
    String UTF8 = "UTF-8";

    /**
     * 前端工程名
     */
    String FRONT_END_PROJECT = "matrix-ui";

    /**
     * 移动端工程名
     */
    String UNI_END_PROJECT = "matrix-app";

    /**
     * 后端工程名
     */
    String BACK_END_PROJECT = "matrix";

    /**
     * 公共参数
     */
    String MATRIX_PUBLIC_PARAM_KEY = "MATRIX_PUBLIC_PARAM_KEY";

    /**
     * 成功标记
     */
    Integer SUCCESS = 0;

    /**
     * 失败标记
     */
    Integer FAIL = 1;

    /**
     * 默认存储bucket
     */
    String BUCKET_NAME = "matrix";

    /**
     * 滑块验证码
     */
    String IMAGE_CODE_BLOCK_PUZZLE = "blockPuzzle";

    /**
     * 点击文字验证码类型
     */
    String IMAGE_CODE_CLICK_WORD = "clickWord";

    /**
     * 验证码开关
     */
    String CAPTCHA_FLAG = "captcha_flag";

    /**
     * 密码传输是否加密
     */
    String ENC_FLAG = "enc_flag";

    /**
     * 客户端允许同时在线数量
     */
    String ONLINE_QUANTITY = "online_quantity";

    /**
     * 是否允许显式配置的 public client 获取 refresh token
     */
    String ALLOW_PUBLIC_REFRESH_TOKEN = "allow_public_refresh_token";

    /**
     * 请求开始时间
     */
    String REQUEST_START_TIME = "REQUEST-START-TIME";

    /**
     * 当前页
     */
    String CURRENT = "current";

    /**
     * size
     */
    String SIZE = "size";

}
