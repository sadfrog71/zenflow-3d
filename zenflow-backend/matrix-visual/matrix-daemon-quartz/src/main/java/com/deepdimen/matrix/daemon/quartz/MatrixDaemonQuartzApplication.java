package com.deepdimen.matrix.daemon.quartz;

import com.deepdimen.matrix.common.feign.annotation.EnableMatrixFeignClients;
import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author frwcloud
 * @date 2019/01/23 定时任务模块
 */
@EnableOpenApi("job")
@EnableMatrixFeignClients
@EnableMatrixResourceServer
@EnableDiscoveryClient
@SpringBootApplication
public class MatrixDaemonQuartzApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrixDaemonQuartzApplication.class, args);
	}

}
