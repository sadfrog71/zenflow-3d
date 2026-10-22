package com.deepdimen.matrix.common.api.encrypt.annotation;

import java.lang.annotation.*;

/**
 * 无加密配置注解
 *
 * @author caoyuan
 * @date 2025/01/08
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface NoEncrypt {
}
