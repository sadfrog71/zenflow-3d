package com.deepdimen.matrix.discover;

import com.deepdimen.matrix.admin.service.impl.TenantUserDirectoryImpl;
import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Intelligent-agent and Skill microservice bootstrap.
 */
@EnableOpenApi("discover")
@EnableMatrixResourceServer
@EnableDiscoveryClient
@MapperScan({ "com.deepdimen.matrix.discover.mapper", "com.deepdimen.matrix.admin.mapper" })
@Import(TenantUserDirectoryImpl.class)
@SpringBootApplication(scanBasePackages = { "com.deepdimen.matrix.discover", "com.deepdimen.matrix.assistant" })
public class MatrixDiscoverApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrixDiscoverApplication.class, args);
	}

}
