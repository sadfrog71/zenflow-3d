package com.deepdimen.matrix.discover.dify;

/**
 * Dify 认证异常（登录失败、token 刷新失败等）
 *
 * @author matrix
 * @date 2026/03/14
 */
public class DifyAuthException extends RuntimeException {

	public DifyAuthException(String message) {
		super(message);
	}

	public DifyAuthException(String message, Throwable cause) {
		super(message, cause);
	}

}
