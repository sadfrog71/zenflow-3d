package com.deepdimen.matrix.report;

import com.deepdimen.matrix.common.feign.annotation.EnableMatrixFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 业务模板
 *
 * @author lr
 * @since 2023-04-05
 */
@EnableMatrixFeignClients
@SpringBootApplication
public class MatrixReportPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrixReportPlatformApplication.class);
	}

}
