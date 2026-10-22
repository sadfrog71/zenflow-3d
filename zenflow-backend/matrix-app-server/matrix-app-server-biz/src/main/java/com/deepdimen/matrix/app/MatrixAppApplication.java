package com.deepdimen.matrix.app;

import com.deepdimen.matrix.common.feign.annotation.EnableMatrixFeignClients;
import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author matrix archetype
 * <p>
 * 项目启动类
 */
@EnableOpenApi("app")
@EnableMatrixFeignClients
@EnableDiscoveryClient
@EnableMatrixResourceServer
@SpringBootApplication
public class MatrixAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrixAppApplication.class, args);
	}

}
