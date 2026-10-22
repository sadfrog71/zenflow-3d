package com.deepdimen.matrix.discover;

import com.deepdimen.matrix.common.security.annotation.EnableMatrixResourceServer;
import com.deepdimen.matrix.common.swagger.annotation.EnableOpenApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MatrixDiscoverApplicationTest {

	@Test
	void declaresStandaloneMicroserviceBootstrap() {
		Class<MatrixDiscoverApplication> applicationClass = MatrixDiscoverApplication.class;

		assertNotNull(applicationClass.getAnnotation(SpringBootApplication.class));
		assertNotNull(applicationClass.getAnnotation(EnableDiscoveryClient.class));
		assertNotNull(applicationClass.getAnnotation(EnableMatrixResourceServer.class));
		assertNotNull(applicationClass.getAnnotation(EnableOpenApi.class));
	}

}
