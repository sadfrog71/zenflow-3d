package com.deepdimen.matrix.discover.dify;

/**
 * Dify API 调用异常（请求失败、响应解析失败等）
 *
 * @author matrix
 * @date 2026/03/14
 */
public class DifyApiException extends RuntimeException {

	public DifyApiException(String message) {
		super(message);
	}

	public DifyApiException(String message, Throwable cause) {
		super(message, cause);
	}

}
