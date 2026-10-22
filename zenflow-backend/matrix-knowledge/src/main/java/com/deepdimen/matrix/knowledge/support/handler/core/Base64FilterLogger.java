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

package com.deepdimen.matrix.knowledge.support.handler.core;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.Arrays;
import java.util.Objects;

/**
 * 自定义 Logger 实现，用于过滤日志中的 base64 内容
 * <p>
 * 功能说明： - 自动识别并过滤 base64 图片数据（data:image/*） - 自动识别并过滤 base64 音频数据（data:audio/*） - 自动识别并过滤
 * base64 视频数据（data:video/*） - 将匹配的内容替换为 "[BASE64内容已省略]" - 支持单行和多行日志消息的过滤 - 与 Langchain4j
 * 的 logger() 方法兼容 - 支持配置化的过滤规则
 * <p>
 * 使用方法： 1. 在 OpenAiChatModel.builder() 中使用： .logger(new
 * Base64FilterLogger(YourClass.class)) 2. 或者使用默认的 logger 名称： .logger(new
 * Base64FilterLogger("OpenAI")) 3. 使用自定义配置： .logger(new Base64FilterLogger("OpenAI",
 * true, 100))
 *
 * @author deepdimen
 */
public class Base64FilterLogger implements Logger {

	/**
	 * 底层 Logger 实例
	 */
	private final Logger delegate;

	/**
	 * 替换 base64 内容的固定字符串
	 */
	private static final String BASE64_REPLACEMENT = "[BASE64内容已省略]";

	/**
	 * 是否启用 base64 过滤
	 */
	private final boolean filterEnabled = true;

	/**
	 * 最小 base64 长度阈值，避免误判短字符串
	 */
	private final int minBase64Length;

	/**
	 * 构造函数 - 使用默认配置
	 * @param clazz 类对象，用于创建底层 Logger
	 */
	public Base64FilterLogger(Class<?> clazz) {
		this(LoggerFactory.getLogger(clazz), true, 200);
	}

	/**
	 * 构造函数 - 使用默认配置
	 * @param name Logger 名称
	 */
	public Base64FilterLogger(String name) {
		this(LoggerFactory.getLogger(name), true, 200);
	}

	/**
	 * 构造函数 - 使用自定义配置
	 * @param clazz 类对象，用于创建底层 Logger
	 * @param filterEnabled 是否启用 base64 过滤
	 * @param minBase64Length 最小 base64 长度阈值
	 */
	public Base64FilterLogger(Class<?> clazz, boolean filterEnabled, int minBase64Length) {
		this(LoggerFactory.getLogger(clazz), filterEnabled, minBase64Length);
	}

	/**
	 * 构造函数 - 使用自定义配置
	 * @param name Logger 名称
	 * @param filterEnabled 是否启用 base64 过滤
	 * @param minBase64Length 最小 base64 长度阈值
	 */
	public Base64FilterLogger(String name, boolean filterEnabled, int minBase64Length) {
		this(LoggerFactory.getLogger(name), filterEnabled, minBase64Length);
	}

	/**
	 * 私有构造函数 - 实际实现
	 * @param delegate 底层 Logger 实例
	 * @param filterEnabled 是否启用 base64 过滤
	 * @param minBase64Length 最小 base64 长度阈值
	 */
	private Base64FilterLogger(Logger delegate, boolean filterEnabled, int minBase64Length) {
		this.delegate = delegate;
		this.minBase64Length = minBase64Length;
	}

	/**
	 * 过滤消息中的 base64 内容
	 * @param message 原始消息
	 * @return 过滤后的消息
	 */
	private String filterBase64Content(String message) {
		if (!filterEnabled || message == null || message.length() < minBase64Length) {
			return message;
		}

		if (!message.contains("data:image")) {
			return message;
		}

		// 使用预编译的正则表达式进行替换
		return StrUtil.replace(message, StrUtil.subBetween(message, "data:image", "\n"), BASE64_REPLACEMENT);
	}

	/**
	 * 获取 Logger 名称
	 * @return Logger 名称
	 */
	@Override
	public String getName() {
		return delegate.getName();
	}

	/**
	 * 检查是否启用 TRACE 级别日志
	 * @return 是否启用 TRACE 级别
	 */
	@Override
	public boolean isTraceEnabled() {
		return delegate.isTraceEnabled();
	}

	/**
	 * 记录 TRACE 级别日志
	 * @param msg 日志消息
	 */
	@Override
	public void trace(String msg) {
		if (isTraceEnabled()) {
			delegate.trace(msg);
		}
	}

	/**
	 * 记录 TRACE 级别日志
	 * @param format 日志格式
	 * @param arg 参数
	 */
	@Override
	public void trace(String format, Object arg) {
		if (isTraceEnabled()) {
			delegate.trace(format, arg);
		}
	}

	/**
	 * 记录 TRACE 级别日志
	 * @param format 日志格式
	 * @param arg1 参数1
	 * @param arg2 参数2
	 */
	@Override
	public void trace(String format, Object arg1, Object arg2) {
		if (isTraceEnabled()) {
			delegate.trace(format, arg1, arg2);
		}
	}

	/**
	 * 记录 TRACE 级别日志
	 * @param format 日志格式
	 * @param arguments 参数数组
	 */
	@Override
	public void trace(String format, Object... arguments) {
		if (isTraceEnabled()) {
			delegate.trace(format, arguments);
		}
	}

	/**
	 * 记录 TRACE 级别日志
	 * @param msg 日志消息
	 * @param t 异常信息
	 */
	@Override
	public void trace(String msg, Throwable t) {
		if (isTraceEnabled()) {
			delegate.trace(msg, t);
		}
	}

	/**
	 * 检查是否启用 TRACE 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @return 是否启用 TRACE 级别
	 */
	@Override
	public boolean isTraceEnabled(Marker marker) {
		return delegate.isTraceEnabled(marker);
	}

	/**
	 * 记录 TRACE 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 */
	@Override
	public void trace(Marker marker, String msg) {
		if (isTraceEnabled(marker)) {
			delegate.trace(marker, msg);
		}
	}

	/**
	 * 记录 TRACE 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg 参数
	 */
	@Override
	public void trace(Marker marker, String format, Object arg) {
		if (isTraceEnabled(marker)) {
			delegate.trace(marker, format, arg);
		}
	}

	/**
	 * 记录 TRACE 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg1 参数1
	 * @param arg2 参数2
	 */
	@Override
	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		if (isTraceEnabled(marker)) {
			delegate.trace(marker, format, arg1, arg2);
		}
	}

	/**
	 * 记录 TRACE 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arguments 参数数组
	 */
	@Override
	public void trace(Marker marker, String format, Object... arguments) {
		if (isTraceEnabled(marker)) {
			delegate.trace(marker, format, arguments);
		}
	}

	/**
	 * 记录 TRACE 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 * @param t 异常信息
	 */
	@Override
	public void trace(Marker marker, String msg, Throwable t) {
		if (isTraceEnabled(marker)) {
			delegate.trace(marker, msg, t);
		}
	}

	/**
	 * 检查是否启用 DEBUG 级别日志
	 * @return 是否启用 DEBUG 级别
	 */
	@Override
	public boolean isDebugEnabled() {
		return delegate.isDebugEnabled();
	}

	@Override
	public void debug(String msg) {
		if (isDebugEnabled()) {
			delegate.debug(msg);
		}
	}

	@Override
	public void debug(String format, Object arg) {
		if (isDebugEnabled()) {
			delegate.debug(format, arg);
		}
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		if (isDebugEnabled()) {
			delegate.debug(format, arg1, arg2);
		}
	}

	@Override
	public void debug(String format, Object... arguments) {
		if (isDebugEnabled()) {
			if (arguments == null) {
				delegate.debug(format, arguments);
				return;
			}

			delegate.debug(format,
					Arrays.stream(arguments)
						.map(argument -> filterBase64Content(Objects.nonNull(argument) ? argument.toString() : null))
						.toArray());
		}
	}

	@Override
	public void debug(String msg, Throwable t) {
		if (isDebugEnabled()) {
			delegate.debug(msg, t);
		}
	}

	/**
	 * 检查是否启用 DEBUG 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @return 是否启用 DEBUG 级别
	 */
	@Override
	public boolean isDebugEnabled(Marker marker) {
		return delegate.isDebugEnabled(marker);
	}

	/**
	 * 记录 DEBUG 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 */
	@Override
	public void debug(Marker marker, String msg) {
		if (isDebugEnabled(marker)) {
			delegate.debug(marker, msg);
		}
	}

	/**
	 * 记录 DEBUG 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg 参数
	 */
	@Override
	public void debug(Marker marker, String format, Object arg) {
		if (isDebugEnabled(marker)) {
			delegate.debug(marker, format, arg);
		}
	}

	/**
	 * 记录 DEBUG 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg1 参数1
	 * @param arg2 参数2
	 */
	@Override
	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		if (isDebugEnabled(marker)) {
			delegate.debug(marker, format, arg1, arg2);
		}
	}

	/**
	 * 记录 DEBUG 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arguments 参数数组
	 */
	@Override
	public void debug(Marker marker, String format, Object... arguments) {
		if (isDebugEnabled(marker)) {
			delegate.debug(marker, format, arguments);
		}
	}

	/**
	 * 记录 DEBUG 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 * @param t 异常信息
	 */
	@Override
	public void debug(Marker marker, String msg, Throwable t) {
		if (isDebugEnabled(marker)) {
			delegate.debug(marker, msg, t);
		}
	}

	@Override
	public boolean isInfoEnabled() {
		return delegate.isInfoEnabled();
	}

	@Override
	public void info(String msg) {
		if (isInfoEnabled()) {
			delegate.info(msg);
		}
	}

	@Override
	public void info(String format, Object arg) {
		if (isInfoEnabled()) {
			delegate.info(format, arg);
		}
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		if (isInfoEnabled()) {
			delegate.info(format, arg1, arg2);
		}
	}

	@Override
	public void info(String format, Object... arguments) {
		if (isInfoEnabled()) {
			if (arguments == null) {
				delegate.info(format, arguments);
				return;
			}

			delegate.info(format,
					Arrays.stream(arguments)
						.map(argument -> filterBase64Content(Objects.nonNull(argument) ? argument.toString() : null))
						.toArray());
		}
	}

	@Override
	public void info(String msg, Throwable t) {
		if (isInfoEnabled()) {
			delegate.info(msg, t);
		}
	}

	/**
	 * 检查是否启用 INFO 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @return 是否启用 INFO 级别
	 */
	@Override
	public boolean isInfoEnabled(Marker marker) {
		return delegate.isInfoEnabled(marker);
	}

	/**
	 * 记录 INFO 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 */
	@Override
	public void info(Marker marker, String msg) {
		if (isInfoEnabled(marker)) {
			delegate.info(marker, msg);
		}
	}

	/**
	 * 记录 INFO 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg 参数
	 */
	@Override
	public void info(Marker marker, String format, Object arg) {
		if (isInfoEnabled(marker)) {
			delegate.info(marker, format, arg);
		}
	}

	/**
	 * 记录 INFO 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg1 参数1
	 * @param arg2 参数2
	 */
	@Override
	public void info(Marker marker, String format, Object arg1, Object arg2) {
		if (isInfoEnabled(marker)) {
			delegate.info(marker, format, arg1, arg2);
		}
	}

	/**
	 * 记录 INFO 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arguments 参数数组
	 */
	@Override
	public void info(Marker marker, String format, Object... arguments) {
		if (isInfoEnabled(marker)) {
			delegate.info(marker, format, arguments);
		}
	}

	/**
	 * 记录 INFO 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 * @param t 异常信息
	 */
	@Override
	public void info(Marker marker, String msg, Throwable t) {
		if (isInfoEnabled(marker)) {
			delegate.info(marker, msg, t);
		}
	}

	@Override
	public boolean isWarnEnabled() {
		return delegate.isWarnEnabled();
	}

	@Override
	public void warn(String msg) {
		if (isWarnEnabled()) {
			delegate.warn(msg);
		}
	}

	@Override
	public void warn(String format, Object arg) {
		if (isWarnEnabled()) {
			delegate.warn(format, arg);
		}
	}

	@Override
	public void warn(String format, Object... arguments) {
		if (isWarnEnabled()) {
			delegate.warn(format, arguments);
		}
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		if (isWarnEnabled()) {
			delegate.warn(format, arg1, arg2);
		}
	}

	@Override
	public void warn(String msg, Throwable t) {
		if (isWarnEnabled()) {
			delegate.warn(msg, t);
		}
	}

	/**
	 * 检查是否启用 WARN 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @return 是否启用 WARN 级别
	 */
	@Override
	public boolean isWarnEnabled(Marker marker) {
		return delegate.isWarnEnabled(marker);
	}

	/**
	 * 记录 WARN 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 */
	@Override
	public void warn(Marker marker, String msg) {
		if (isWarnEnabled(marker)) {
			delegate.warn(marker, msg);
		}
	}

	/**
	 * 记录 WARN 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg 参数
	 */
	@Override
	public void warn(Marker marker, String format, Object arg) {
		if (isWarnEnabled(marker)) {
			delegate.warn(marker, format, arg);
		}
	}

	/**
	 * 记录 WARN 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg1 参数1
	 * @param arg2 参数2
	 */
	@Override
	public void warn(Marker marker, String format, Object arg1, Object arg2) {
		if (isWarnEnabled(marker)) {
			delegate.warn(marker, format, arg1, arg2);
		}
	}

	/**
	 * 记录 WARN 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arguments 参数数组
	 */
	@Override
	public void warn(Marker marker, String format, Object... arguments) {
		if (isWarnEnabled(marker)) {
			delegate.warn(marker, format, arguments);
		}
	}

	/**
	 * 记录 WARN 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 * @param t 异常信息
	 */
	@Override
	public void warn(Marker marker, String msg, Throwable t) {
		if (isWarnEnabled(marker)) {
			delegate.warn(marker, msg, t);
		}
	}

	@Override
	public boolean isErrorEnabled() {
		return delegate.isErrorEnabled();
	}

	@Override
	public void error(String msg) {
		if (isErrorEnabled()) {
			delegate.error(msg);
		}
	}

	@Override
	public void error(String format, Object arg) {
		if (isErrorEnabled()) {
			delegate.error(format, arg);
		}
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		if (isErrorEnabled()) {
			delegate.error(format, arg1, arg2);
		}
	}

	@Override
	public void error(String format, Object... arguments) {
		if (isErrorEnabled()) {
			delegate.error(format, arguments);
		}
	}

	@Override
	public void error(String msg, Throwable t) {
		if (isErrorEnabled()) {
			delegate.error(msg, t);
		}
	}

	/**
	 * 检查是否启用 ERROR 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @return 是否启用 ERROR 级别
	 */
	@Override
	public boolean isErrorEnabled(Marker marker) {
		return delegate.isErrorEnabled(marker);
	}

	/**
	 * 记录 ERROR 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 */
	@Override
	public void error(Marker marker, String msg) {
		if (isErrorEnabled(marker)) {
			delegate.error(marker, msg);
		}
	}

	/**
	 * 记录 ERROR 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg 参数
	 */
	@Override
	public void error(Marker marker, String format, Object arg) {
		if (isErrorEnabled(marker)) {
			delegate.error(marker, format, arg);
		}
	}

	/**
	 * 记录 ERROR 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arg1 参数1
	 * @param arg2 参数2
	 */
	@Override
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		if (isErrorEnabled(marker)) {
			delegate.error(marker, format, arg1, arg2);
		}
	}

	/**
	 * 记录 ERROR 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param format 日志格式
	 * @param arguments 参数数组
	 */
	@Override
	public void error(Marker marker, String format, Object... arguments) {
		if (isErrorEnabled(marker)) {
			delegate.error(marker, format, arguments);
		}
	}

	/**
	 * 记录 ERROR 级别日志（带 Marker）
	 * @param marker 日志标记
	 * @param msg 日志消息
	 * @param t 异常信息
	 */
	@Override
	public void error(Marker marker, String msg, Throwable t) {
		if (isErrorEnabled(marker)) {
			delegate.error(marker, msg, t);
		}
	}

}
