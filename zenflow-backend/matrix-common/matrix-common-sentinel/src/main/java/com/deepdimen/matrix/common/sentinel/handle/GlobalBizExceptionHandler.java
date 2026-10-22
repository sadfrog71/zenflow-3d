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

package com.deepdimen.matrix.common.sentinel.handle;

import com.alibaba.csp.sentinel.Tracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepdimen.matrix.common.core.util.R;
import feign.FeignException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * @author caoyuan
 * @date 2020-06-29
 */
@Slf4j
@RestControllerAdvice
public class GlobalBizExceptionHandler {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<R> handleResponseStatusException(ResponseStatusException exception) {
		log.debug("请求被业务边界拒绝 status={} reason={}", exception.getStatusCode(), exception.getReason());
		String message = exception.getReason() == null ? exception.getMessage() : exception.getReason();
		return ResponseEntity.status(exception.getStatusCode()).body(R.failed(message));
	}

	/**
	 * 全局异常.
	 * @param e the e
	 * @return R
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public R handleGlobalException(Exception e) {

		log.error("全局异常信息 ex={}", e.getMessage(), e);

		// 业务异常交由 sentinel 记录
		Tracer.trace(e);
		return R.failed(e.getLocalizedMessage());
	}

	@ExceptionHandler(AsyncRequestNotUsableException.class)
	@ResponseStatus(HttpStatus.OK)
	public void handleGlobalException(IOException e) {
	}

	/**
	 * SQLException Exception
	 * @param exception 数据库调用异常
	 * @return R
	 */
	@ExceptionHandler({ SQLException.class })
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public R handleSQLException(SQLException exception) {
		log.error("数据库调用异常 ex={}", exception.getMessage(), exception);
		return R.failed("数据库调用异常，请联系管理员处理");
	}

	@SneakyThrows
	@ExceptionHandler(FeignException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public R handleGlobalException(FeignException e) {
		log.error("全局异常信息 ex={}", e.getMessage(), e);

		// 业务异常交由 sentinel 记录
		Tracer.trace(e);

		if (e.responseBody().isPresent()) {
			return objectMapper.readValue(e.responseBody().get().array(), R.class);
		}

		return R.failed(e.getLocalizedMessage());
	}

	/**
	 * AccessDeniedException
	 * @param e the e
	 * @return R
	 */
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public R handleAccessDeniedException(AccessDeniedException e) {
		log.error("拒绝授权异常信息 ex={}", e.getMessage());
		return R.failed("权限不足，不允许访问");
	}

	/**
	 * IllegalArgumentException
	 * @param e the e
	 * @return R
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public R handleIllegalArgumentException(IllegalArgumentException e) {
		log.warn("参数校验异常 ex={}", e.getMessage());
		return R.failed(e.getMessage());
	}

	/**
	 * validation Exception
	 * @param exception
	 * @return R
	 */
	@ExceptionHandler({ BindException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public R handleBodyValidException(BindException exception) {
		List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
		// 插入log 的逻辑
		return R.failed(String.format("%s %s", fieldErrors.get(0).getField(), fieldErrors.get(0).getDefaultMessage()));
	}

	/**
	 * 避免 404 重定向到 /error 导致NPE ,ignore-url 需要配置对应端点
	 * @return R
	 */
	@DeleteMapping("/error")
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public R noHandlerFoundException() {
		return R.failed(HttpStatus.NOT_FOUND.getReasonPhrase());
	}

	/**
	 * 保持和低版本请求路径不存在的行为一致
	 * <p>
	 * <a href="https://github.com/spring-projects/spring-boot/issues/38733">[Spring Boot
	 * 3.2.0] 404 Not Found behavior #38733</a>
	 * @param exception
	 * @return R
	 */
	@ExceptionHandler({ NoResourceFoundException.class })
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public R noResourceFoundException(NoResourceFoundException exception) {
		log.debug("请求路径 404 {}", exception.getMessage());
		return R.failed(exception.getMessage());
	}

}
