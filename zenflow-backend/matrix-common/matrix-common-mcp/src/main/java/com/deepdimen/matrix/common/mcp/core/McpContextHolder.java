package com.deepdimen.matrix.common.mcp.core;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import lombok.experimental.UtilityClass;

/**
 * MCP上下文持有类
 *
 * @author caoyuan
 * @date 2025/07/22
 */
@UtilityClass
public class McpContextHolder {

	private final ThreadLocal<HasPermission> THREAD_LOCAL = new TransmittableThreadLocal<>();

	public void setAnnotation(HasPermission annotation) {
		THREAD_LOCAL.set(annotation);
	}

	public HasPermission getAnnotation() {
		return THREAD_LOCAL.get();
	}

	public void clear() {
		THREAD_LOCAL.remove();
	}

}
