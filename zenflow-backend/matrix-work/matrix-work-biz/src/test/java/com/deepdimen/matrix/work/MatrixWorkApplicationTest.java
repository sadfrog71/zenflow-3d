package com.deepdimen.matrix.work;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import com.deepdimen.matrix.assistant.provider.autoconfigure.AssistantProviderAutoConfiguration;
import com.deepdimen.matrix.capsule.context.CapsuleContextProvider;

class MatrixWorkApplicationTest {

	@Test
	void cloudBootstrapHostsWorkInformationAndCapsuleUnderTheMatrixWorkAudience() throws Exception {
		SpringBootApplication application = MatrixWorkApplication.class.getAnnotation(SpringBootApplication.class);
		assertNotNull(application);
		assertTrue(List.of(application.scanBasePackages()).contains("com.deepdimen.matrix.work"));
		assertTrue(List.of(application.scanBasePackages()).contains("com.deepdimen.matrix.capsule"));
		assertNotNull(AssistantProviderAutoConfiguration.class);
		assertNotNull(CapsuleContextProvider.class);
		MapperScan mapperScan = MatrixWorkApplication.class.getAnnotation(MapperScan.class);
		assertNotNull(mapperScan);
		assertTrue(List.of(mapperScan.value()).contains("com.deepdimen.matrix.admin.mapper"));
		Import imported = MatrixWorkApplication.class.getAnnotation(Import.class);
		assertNotNull(imported);
		assertTrue(List.of(imported.value())
			.stream()
			.anyMatch(
					type -> type.getName().equals("com.deepdimen.matrix.admin.service.impl.TenantUserDirectoryImpl")));
		assertTrue(List.of(imported.value())
			.stream()
			.anyMatch(type -> type.getName()
				.equals("com.deepdimen.matrix.assistant.generation.UnavailableAssistantGenerationAdapter")));
		assertTrue(List.of(imported.value())
			.stream()
			.anyMatch(
					type -> type.getName().equals("com.deepdimen.matrix.admin.service.impl.SysBusinessMessageReader")));
		String applicationYaml = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);
		assertTrue(applicationYaml.contains("name: matrix-work"));
	}

}
