package com.deepdimen.matrix.common.datasource.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * @author caoyuan
 * @date 2022/8/8
 *
 * 注入SQL 格式化的插件
 */
@ConditionalOnClass(name = "com.deepdimen.matrix.common.data.mybatis.DruidSqlLogFilter")
public class DynamicLogConfiguration {

}
