/*
 * Copyright (c) 2020 deepdimen Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.deepdimen.matrix.common.security.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * @author caoyuan
 * @date 2022-06-02
 */
@RequiredArgsConstructor
@EnableConfigurationProperties(PermitAllUrlProperties.class)
public class MatrixResourceServerAutoConfiguration {

	/**
	 * 鉴权具体的实现逻辑
	 * @return （#pms.xxx）
	 */
	@Bean("pms")
	public PermissionService permissionService() {
		return new PermissionService();
	}

	/**
	 * 请求令牌的抽取逻辑
	 * @param urlProperties 对外暴露的接口列表
	 * @return BearerTokenExtractor
	 */
	@Bean
	public MatrixBearerTokenExtractor pigBearerTokenExtractor(PermitAllUrlProperties urlProperties) {
		return new MatrixBearerTokenExtractor(urlProperties);
	}

	/**
	 * 资源服务器异常处理
	 * @param objectMapper jackson 输出对象
	 * @param securityMessageSource 自定义国际化处理器
	 * @return ResourceAuthExceptionEntryPoint
	 */
	@Bean
	public ResourceAuthExceptionEntryPoint resourceAuthExceptionEntryPoint(ObjectMapper objectMapper,
			MessageSource securityMessageSource) {
		return new ResourceAuthExceptionEntryPoint(objectMapper, securityMessageSource);
	}

	/**
	 * 资源服务器toke内省处理器
	 * @param authorizationService token 存储实现
	 * @return TokenIntrospector
	 */
	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector(OAuth2AuthorizationService authorizationService) {
		return new MatrixCustomOpaqueTokenIntrospector(authorizationService);
	}

	/**
	 * API Token 鉴权过滤器，通过 security.api-token.enabled=true 启用
	 * @param validatorProvider ApiTokenValidator 实现（由 matrix-upms 提供）
	 * @return AiApiTokenFilter
	 */
	@Bean
	@ConditionalOnProperty(value = "security.api-token.enabled", havingValue = "true")
	public AiApiTokenFilter aiApiTokenFilter(ObjectProvider<ApiTokenValidator> validatorProvider) {
		ApiTokenValidator validator = validatorProvider.getIfAvailable();
		if (validator == null) {
			throw new IllegalStateException("security.api-token.enabled=true 但未找到 ApiTokenValidator 实现，请确认 matrix-upms 已加载");
		}
		return new AiApiTokenFilter(validator);
	}

}
