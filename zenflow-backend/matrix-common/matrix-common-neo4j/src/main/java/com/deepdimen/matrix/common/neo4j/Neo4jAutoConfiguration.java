package com.deepdimen.matrix.common.neo4j;

import com.deepdimen.matrix.common.core.factory.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * Neo4j 自动配置类
 * <p>
 * 为 Matrix 项目提供 Neo4j 图数据库的自动配置支持，主要用于集成 LangChain4j 框架。 该配置类会自动加载相关的配置属性，并禁用 Neo4j
 * 的健康检查功能。
 * </p>
 */
@AutoConfiguration
@PropertySource(value = "classpath:no-health-config.yml", factory = YamlPropertySourceFactory.class)
public class Neo4jAutoConfiguration {

}
