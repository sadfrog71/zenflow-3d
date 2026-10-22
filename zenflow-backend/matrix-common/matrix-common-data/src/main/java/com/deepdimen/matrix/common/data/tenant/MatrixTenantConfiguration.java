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

package com.deepdimen.matrix.common.data.tenant;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * 租户信息拦截配置类
 *
 * @author caoyuan
 * @date 2025/06/27
 */
@Configuration
public class MatrixTenantConfiguration {

	/**
	 * 创建并返回MatrixFeignTenantInterceptor实例
	 *
	 * @return RequestInterceptor实例
	 */
	@Bean
	public RequestInterceptor matrixFeignTenantInterceptor() {
		return new MatrixFeignTenantInterceptor();
	}

	/**
	 * 创建并返回一个租户请求拦截器实例
	 *
	 * @return 配置好的租户请求拦截器
	 */
	@Bean
	public ClientHttpRequestInterceptor matrixTenantRequestInterceptor() {
		return new TenantRequestInterceptor();
	}

}
