package com.deepdimen.matrix.work;

import com.deepdimen.matrix.admin.service.impl.SysBusinessMessageReader;
import com.deepdimen.matrix.admin.service.impl.TenantUserDirectoryImpl;
import com.deepdimen.matrix.assistant.generation.StubAssistantGenerationAdapter;
import com.deepdimen.matrix.assistant.generation.UnavailableAssistantGenerationAdapter;
import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@EnableOpenApi("work")
@EnableDiscoveryClient
@EnableMatrixResourceServer
@MapperScan({ "com.deepdimen.matrix.work.mapper", "com.deepdimen.matrix.capsule.mapper",
		"com.deepdimen.matrix.admin.mapper" })
@Import({ TenantUserDirectoryImpl.class, SysBusinessMessageReader.class, UnavailableAssistantGenerationAdapter.class,
		StubAssistantGenerationAdapter.class })
@SpringBootApplication(scanBasePackages = { "com.deepdimen.matrix.work", "com.deepdimen.matrix.capsule" })
public class MatrixWorkApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrixWorkApplication.class, args);
	}

}
