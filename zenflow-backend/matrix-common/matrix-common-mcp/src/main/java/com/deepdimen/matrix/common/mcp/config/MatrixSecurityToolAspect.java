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

package com.deepdimen.matrix.common.mcp.config;

import com.deepdimen.matrix.common.mcp.core.McpContextHolder;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.Ordered;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Matrix安全工具切面类：用于拦截MCP工具调用并检查注解
 *
 * @author caoyuan
 * @date 2025/07/22
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class MatrixSecurityToolAspect implements Ordered {

	@SneakyThrows
	@Around("@annotation(tool)")
	public Object around(ProceedingJoinPoint pjp, Tool tool) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();

		HasPermission annotation = method.getAnnotation(HasPermission.class);
		if (Objects.nonNull(annotation)) {
			McpContextHolder.setAnnotation(annotation);
		}

		try {
			return pjp.proceed(); // 继续执行方法
		}
		finally {
			McpContextHolder.clear();
		}
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}

}
