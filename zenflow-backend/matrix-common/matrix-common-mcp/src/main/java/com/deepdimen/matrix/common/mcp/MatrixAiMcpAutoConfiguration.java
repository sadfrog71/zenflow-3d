package com.deepdimen.matrix.common.mcp;

import com.deepdimen.matrix.common.core.factory.YamlPropertySourceFactory;
import com.deepdimen.matrix.common.mcp.config.MatrixSecurityToolAspect;
import com.deepdimen.matrix.common.mcp.core.ToolAutoRegister;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * Matrix AI MCP 自动配置类
 *
 * @author caoyuan
 * @date 2025/07/22
 */
@PropertySource(value = "classpath:mcp-config.yaml", factory = YamlPropertySourceFactory.class)
@Configuration(proxyBeanMethods = false)
@Import(ToolAutoRegister.class)
public class MatrixAiMcpAutoConfiguration {

	@Bean
	public MatrixSecurityToolAspect toolAspect() {
		return new MatrixSecurityToolAspect();
	}

}
