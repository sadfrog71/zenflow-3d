package com.deepdimen.matrix.common.core.util;

import java.lang.annotation.*;

/**
 * @author caoyuan
 * @since v5.3
 * <p>
 * table 实体增加此组件自动声明为租户表
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
public @interface TenantTable {

}
