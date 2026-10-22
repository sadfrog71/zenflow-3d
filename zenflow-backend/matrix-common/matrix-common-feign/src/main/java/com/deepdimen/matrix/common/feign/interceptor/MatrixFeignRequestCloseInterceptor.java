package com.deepdimen.matrix.common.feign.interceptor;

import feign.RequestInterceptor;
import org.springframework.http.HttpHeaders;

/**
 * @author caoyuan
 * @date 2024/3/15
 * <p>
 * http connection close
 */
public class MatrixFeignRequestCloseInterceptor implements RequestInterceptor {

	/**
	 * set connection close
	 * @param template
	 */
	@Override
	public void apply(feign.RequestTemplate template) {
		template.header(HttpHeaders.CONNECTION, "close");
	}

}
