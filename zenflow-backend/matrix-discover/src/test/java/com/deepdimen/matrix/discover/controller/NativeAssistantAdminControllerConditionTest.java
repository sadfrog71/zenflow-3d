package com.deepdimen.matrix.discover.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NativeAssistantAdminControllerConditionTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(NativeAssistantAdminController.class);

	@Test
	void backsOffWhenNativeProvisioningIsUnavailable() {
		contextRunner.run(context -> {
			assertThat(context.getStartupFailure()).isNull();
			assertThat(context.containsBean("nativeAssistantAdminController")).isFalse();
		});
	}

}
